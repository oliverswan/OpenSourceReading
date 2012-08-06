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

import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoop;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public abstract class AbstractNioChannel extends AbstractChannel {

    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(AbstractNioChannel.class);

    private final SelectableChannel ch;
    private final int defaultInterestOps;
    private volatile SelectionKey selectionKey;

    /**
     * 当前尝试连接的future，如果已经有实例的，再次尝试连接会失败
     * The future of the current connection attempt.  If not null, subsequent
     * connection attempts will fail.
     */
    private ChannelFuture connectFuture;
    private ScheduledFuture<?> connectTimeoutFuture;
    private ConnectException connectTimeoutException;

    protected AbstractNioChannel(
            Channel parent, Integer id, SelectableChannel ch, int defaultInterestOps) {
        super(parent, id);
        this.ch = ch;
        this.defaultInterestOps = defaultInterestOps;
        try {
            ch.configureBlocking(false);
        } catch (IOException e) {
            try {
                ch.close();
            } catch (IOException e2) {
                if (logger.isWarnEnabled()) {
                    logger.warn(
                            "Failed to close a partially initialized socket.", e2);
                }

            }

            throw new ChannelException("Failed to enter non-blocking mode.", e);
        }
    }

    @Override
    public boolean isOpen() {
        return ch.isOpen();
    }

    @Override
    public InetSocketAddress localAddress() {
        return (InetSocketAddress) super.localAddress();
    }

    @Override
    public InetSocketAddress remoteAddress() {
        return (InetSocketAddress) super.remoteAddress();
    }

    @Override
    public NioUnsafe unsafe() {
        return (NioUnsafe) super.unsafe();
    }

    protected SelectableChannel javaChannel() {
        return ch;
    }

    protected SelectionKey selectionKey() {
        assert selectionKey != null;
        return selectionKey;
    }

    public interface NioUnsafe extends Unsafe {
        java.nio.channels.Channel ch();
        void finishConnect();
        void read();
    }

    protected abstract class AbstractNioUnsafe extends AbstractUnsafe implements NioUnsafe {
        @Override
        public java.nio.channels.Channel ch() {
            return javaChannel();
        }

        @Override
        // channel持有的unsafe的connect方法
        public void connect(
                final SocketAddress remoteAddress, final SocketAddress localAddress, final ChannelFuture future) {
            // 如果被转向到thread线程中来了
        	if (eventLoop().inEventLoop()) {
            	// 保证是打开的
                if (!ensureOpen(future)) {
                    return;
                }

                try {
                    if (connectFuture != null) {
                        throw new IllegalStateException("connection attempt already made");
                    }
                    // 子类实现
                    boolean wasActive = isActive();
                    // 本地地址，远程地址连接
                    // 子类实现
                    if (doConnect(remoteAddress, localAddress)) {
                        future.setSuccess();
                        if (!wasActive && isActive()) {
                            pipeline().fireChannelActive();
                        }
                    } else {
                    	// 连接失败
                        connectFuture = future;

                        // Schedule connect timeout.
                        int connectTimeoutMillis = config().getConnectTimeoutMillis();
                        if (connectTimeoutMillis > 0) {
                        	// 等待这么长时间，执行抛出异常的动作
                            connectTimeoutFuture = eventLoop().schedule(new Runnable() {
                                @Override
                                public void run() {
                                    if (connectTimeoutException == null) {
                                        connectTimeoutException = new ConnectException("connection timed out");
                                    }
                                    ChannelFuture connectFuture = AbstractNioChannel.this.connectFuture;
                                    // 如果连接成功了就会 将AbstractNioChannel.this.connectFuture置为null,所以就不会抛出异常了
                                    if (connectFuture != null && connectFuture.setFailure(connectTimeoutException)) {
                                        pipeline().fireExceptionCaught(connectTimeoutException);
                                        close(voidFuture());
                                    }
                                }
                            }, connectTimeoutMillis, TimeUnit.MILLISECONDS);
                        }
                    }
                } catch (Throwable t) {
                    future.setFailure(t);
                    pipeline().fireExceptionCaught(t);
                    closeIfClosed();
                }
            } 
            // 不在eventLoop中
            else {
            	// 将要执行的代码转向到thread变量中的线程，当再次执行的时候，就是在eventloop中执行了
                eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                    	// 在eventloop中，重新调用本方法
                        connect(remoteAddress, localAddress, future);
                    }
                });
            }
        }

        @Override
        public void finishConnect() {
            assert eventLoop().inEventLoop();
            assert connectFuture != null;
            try {
                boolean wasActive = isActive();
                doFinishConnect();
                connectFuture.setSuccess();
                if (!wasActive && isActive()) {
                    pipeline().fireChannelActive();
                }
            } catch (Throwable t) {
                connectFuture.setFailure(t);
                pipeline().fireExceptionCaught(t);
                closeIfClosed();
            } finally {
                connectTimeoutFuture.cancel(false);
                connectFuture = null;
            }
        }
    }

    @Override
    protected boolean isCompatible(EventLoop loop) {
        return loop instanceof NioChildEventLoop;
    }

    @Override
    protected boolean isFlushPending() {
        SelectionKey selectionKey = this.selectionKey;
        return selectionKey.isValid() && (selectionKey.interestOps() & SelectionKey.OP_WRITE) != 0;
    }

    @Override
    protected Runnable doRegister() throws Exception {
        NioChildEventLoop loop = (NioChildEventLoop) eventLoop();
        selectionKey = javaChannel().register(
                loop.selector, isActive()? defaultInterestOps : 0, this);
        return null;
    }

    @Override
    protected void doDeregister() throws Exception {
        ((NioChildEventLoop) eventLoop()).cancel(selectionKey());
    }

    protected abstract boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception;
    protected abstract void doFinishConnect() throws Exception;
}
