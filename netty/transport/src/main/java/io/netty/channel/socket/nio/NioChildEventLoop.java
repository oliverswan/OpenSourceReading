/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.socket.nio;

import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.SingleThreadEventLoop;
import io.netty.channel.socket.nio.AbstractNioChannel.NioUnsafe;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;

import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

final class NioChildEventLoop extends SingleThreadEventLoop {

    /**
     * Internal Netty logger.
     */
    protected static final InternalLogger logger = InternalLoggerFactory
            .getInstance(NioChildEventLoop.class);

    static final int CLEANUP_INTERVAL = 256; // XXX Hard-coded value, but won't need customization.

    /**
     * The NIO {@link Selector}.
     */
    protected final Selector selector;// 选择器，把它和事件注册到channel上

    /**
     * 是否需要打断Selector.select方法的阻塞
     * 我们对select方法使用timeone，select阻塞timeone时间，除非select方法醒来
     */
    protected final AtomicBoolean wakenUp = new AtomicBoolean();

    private int cancelledKeys;
    private boolean cleanedCancelledKeys;

    NioChildEventLoop(ThreadFactory threadFactory, SelectorProvider selectorProvider) {
        super(threadFactory);// 父类是个SingleThreadEventLoop
        if (selectorProvider == null) {
            throw new NullPointerException("selectorProvider");
        }
        // 打开selector
        selector = openSelector(selectorProvider);
    }

    private static Selector openSelector(SelectorProvider provider) {
        try {
            return provider.openSelector();
        } catch (IOException e) {
            throw new ChannelException("failed to open a new selector", e);
        }
    }

    @Override
    protected void run() {
        Selector selector = this.selector;
        for (;;) {

            wakenUp.set(false);

            try {
            	// 在这里如果被 waken up 了，就一种被waken up太早的情况，是不好的
            	
                SelectorUtil.select(selector);//  开始select，时间是10，监听任何IO事件？

                // 'wakenUp.compareAndSet(false, true)' is always evaluated
                // before calling 'selector.wakeup()' to 降低wake-up消耗。
                // overhead. (Selector.wakeup()是非常耗资源的动作)
                //
                // 然而, 这个方法里有一个race condition
                // 如果wakenUp被太早设置为true，race condition就会被触发
                //
                // 'wakenUp' is set to true too early if:
                
                // 1) Selector is waken up between 'wakenUp.set(false)' and
                //    'selector.select(...)'. (BAD)
                // 2) Selector is waken up between 'selector.select(...)' and
                //    'if (wakenUp.get()) { ... }'. (OK)
                //
                // In the first case, 'wakenUp' is set to true and the
                // following 'selector.select(...)' will wake up immediately.
                
                // Until 'wakenUp' is set to false again in the next round,
                // 'wakenUp.compareAndSet(false, true)' will fail, and therefore
                // any attempt to wake up the Selector will fail, too, causing
                // the following 'selector.select(...)' call to block
                // unnecessarily.导致不需要的阻塞
                //
                // 为了解决这个问题,我们加了下面的代码
//                if (wakenUp.get()) {
//                    selector.wakeup();// we wake up the selector again if wakenUp
//                }
                // 
                
                // 这样做效率不高 in that it wakes up the selector for both
                // the first case (BAD - wake-up required) and the second case
                // (OK - no wake-up required).

                // 在下面的代码还没执行到的时候，就被waken up了是第二种waken up过早的情况
                
                if (wakenUp.get()) {
                    selector.wakeup();
                }

                cancelledKeys = 0;
                
                // 执行taskqueue里所有任务
                runAllTasks();
                
                // 处理IO事件
                processSelectedKeys();

                if (isShutdown()) {
                    closeAll();
                    if (peekTask() == null) {
                        break;
                    }
                }
            } catch (Throwable t) {
                logger.warn(
                        "Unexpected exception in the selector loop.", t);

                // 阻止可能的连贯性的失败，会导致cpu消耗
                // Prevent possible consecutive immediate failures that lead to
                // excessive CPU consumption.
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // Ignore.
                }
            }
        }
    }

    @Override
    protected void cleanup() {
        try {
            selector.close();
        } catch (IOException e) {
            logger.warn(
                    "Failed to close a selector.", e);
        }
    }

    void cancel(SelectionKey key) {
        key.cancel();
        cancelledKeys ++;
        if (cancelledKeys >= CLEANUP_INTERVAL) {
            cancelledKeys = 0;
            cleanedCancelledKeys = true;
            SelectorUtil.cleanupKeys(selector);
        }
    }

    private void processSelectedKeys() {
    	// 任何channel的事件都可能在这里收到
        Set<SelectionKey> selectedKeys = selector.selectedKeys();
        if (selectedKeys.isEmpty()) {
            return;
        }

        Iterator<SelectionKey> i;
        cleanedCancelledKeys = false;
        boolean clearSelectedKeys = true;
        try {
            for (i = selectedKeys.iterator(); i.hasNext();) {
                final SelectionKey k = i.next();
                // 获取Channels
                final AbstractNioChannel ch = (AbstractNioChannel) k.attachment();
                // 获取Unsafe
                final NioUnsafe unsafe = ch.unsafe();
                try {
                	// 获取该channel可以进行的操作位
                    int readyOps = k.readyOps();
                    // channel可读或者链接建立
                    if ((readyOps & (SelectionKey.OP_READ | SelectionKey.OP_ACCEPT)) != 0 || readyOps == 0) {
                        // 实际地读
                    	unsafe.read();
                        if (!ch.isOpen()) {
                            // Connection already closed - no need to handle write.
                            continue;
                        }
                    }
                    if ((readyOps & SelectionKey.OP_WRITE) != 0) {
                    	// 实际地写
                        unsafe.flushNow();
                    }
                    if ((readyOps & SelectionKey.OP_CONNECT) != 0) {
                    	// 结束连接
                        unsafe.finishConnect();
                    }
                } catch (CancelledKeyException ignored) {
                    unsafe.close(unsafe.voidFuture());
                }

                if (cleanedCancelledKeys) {
                    // Create the iterator again to avoid ConcurrentModificationException
                    if (selectedKeys.isEmpty()) {
                        clearSelectedKeys = false;
                        break;
                    } else {
                        i = selectedKeys.iterator();
                    }
                }
            }
        } finally {
            if (clearSelectedKeys) {
                selectedKeys.clear();
            }
        }
    }

    private void closeAll() {
        SelectorUtil.cleanupKeys(selector);
        Set<SelectionKey> keys = selector.keys();
        Collection<Channel> channels = new ArrayList<Channel>(keys.size());
        for (SelectionKey k: keys) {
            channels.add((Channel) k.attachment());
        }

        for (Channel ch: channels) {
            ch.unsafe().close(ch.unsafe().voidFuture());
        }
    }

    @Override
    protected void wakeup(boolean inEventLoop) {
        if (wakenUp.compareAndSet(false, true)) {
            selector.wakeup();// 让select方法立即返回，如果没有被调用select，则下次调用也立即返回
        }
    }
}
