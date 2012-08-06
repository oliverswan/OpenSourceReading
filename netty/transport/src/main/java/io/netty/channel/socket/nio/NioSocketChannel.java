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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ChannelBufType;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.socket.DefaultSocketChannelConfig;
import io.netty.channel.socket.SocketChannelConfig;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class NioSocketChannel extends AbstractNioByteChannel implements io.netty.channel.socket.SocketChannel {

    private static final ChannelMetadata METADATA = new ChannelMetadata(ChannelBufType.BYTE, false);

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(NioSocketChannel.class);

    private final SocketChannelConfig config;
    // 建立一个SocketChannel此时还未连接
    private static SocketChannel newSocket() {
        try {
            return SocketChannel.open();
        } catch (IOException e) {
            throw new ChannelException("Failed to open a socket.", e);
        }
    }

    public NioSocketChannel() {
        this(newSocket());
    }

    public NioSocketChannel(SocketChannel socket) {
        this(null, null, socket);
    }

    public NioSocketChannel(Channel parent, Integer id, SocketChannel socket) {
    	// socket就是ch
        super(parent, id, socket);
        try {
        	// 设置socketchannel非阻塞
            socket.configureBlocking(false);
        } catch (IOException e) {
            try {
                socket.close();
            } catch (IOException e2) {
                if (logger.isWarnEnabled()) {
                    logger.warn(
                            "Failed to close a partially initialized socket.", e2);
                }

            }

            throw new ChannelException("Failed to enter non-blocking mode.", e);
        }

        config = new DefaultSocketChannelConfig(socket.socket());
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    @Override
    public SocketChannelConfig config() {
        return config;
    }

    @Override
    protected SocketChannel javaChannel() {
        return (SocketChannel) super.javaChannel();
    }

    @Override
    public boolean isActive() {
        SocketChannel ch = javaChannel();
        return ch.isOpen() && ch.isConnected();
    }

    @Override
    protected SocketAddress localAddress0() {
        return javaChannel().socket().getLocalSocketAddress();
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return javaChannel().socket().getRemoteSocketAddress();
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        javaChannel().socket().bind(localAddress);
    }

    @Override
    protected boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
        // 如果本地地址不为空，则
    	if (localAddress != null) {
    		// 客户端也是需要端口的，所以如果指定了本地地址，就bind到这个本地地址
            javaChannel().socket().bind(localAddress);
        }

        boolean success = false;
        try {
        	// 连接到远程地址
            boolean connected = javaChannel().connect(remoteAddress);
            if (connected) {
            	// 如果已经连上就监听可读事件
                selectionKey().interestOps(SelectionKey.OP_READ);
            } else {
                selectionKey().interestOps(SelectionKey.OP_CONNECT);
            }
            success = true;
            return connected;
        } finally {
            if (!success) {
                doClose();
            }
        }
    }

    @Override
    protected void doFinishConnect() throws Exception {
        if (!javaChannel().finishConnect()) {
            throw new Error();
        }
        selectionKey().interestOps(SelectionKey.OP_READ);
    }

    @Override
    protected void doDisconnect() throws Exception {
        doClose();
    }

    @Override
    protected void doClose() throws Exception {
        javaChannel().close();
    }

    @Override
    protected int doReadBytes(ByteBuf byteBuf) throws Exception {
    	// 从channel中读出数据到buffer
        return byteBuf.writeBytes(javaChannel(), byteBuf.writableBytes());
    }

    @Override
    protected int doWriteBytes(ByteBuf buf, boolean lastSpin) throws Exception {
    	// 可读字节长度
        final int expectedWrittenBytes = buf.readableBytes();

        // FIXME: This is not as efficient as Netty 3's SendBufferPool if heap buffer is used
        //        because of potentially unwanted repetitive memory copy in case of
        //        a slow connection or a large output buffer that triggers OP_WRITE.
        
        // 慢的连接或者大的输出buffer，触发OP_WRITE可能导致不需要的重复的内存复制
        // 就buffer里可读到的数据，写入到buffer
        final int writtenBytes = buf.readBytes(javaChannel(), expectedWrittenBytes);

        final SelectionKey key = selectionKey();
        final int interestOps = key.interestOps();
        
        // 如果写入的数据大于期望的写入长度
        if (writtenBytes >= expectedWrittenBytes) {
            // Wrote the outbound buffer completely - clear OP_WRITE.
            if ((interestOps & SelectionKey.OP_WRITE) != 0) {
            	// 设置key的interestOp，等于去除OP_WRITE
                key.interestOps(interestOps & ~SelectionKey.OP_WRITE);
            }
        } else {
            // Wrote something or nothing.
            // a) If wrote something, the caller will not retry.如果写了一些内容，调用者不会重试
            //    - 设置 OP_WRITE 让event loop后面调用flushForcibly()
            // b) 如果什么都没写入:
            //    1) If 'lastSpin' 是 false, 调用者会再次调用本方法.
            //       - 不更新 OP_WRITE.
            //    2) If 'lastSpin' is true, 调用者不会再调用本方法
            //       - 设置 OP_WRITE 让event loop后面调用flushForcibly()
            if (writtenBytes > 0 || lastSpin) {
                if ((interestOps & SelectionKey.OP_WRITE) == 0) {
                	// 给key加入OP_WRITE
                    key.interestOps(interestOps | SelectionKey.OP_WRITE);
                }
            }
        }
        // 返回已写入的字节
        return writtenBytes;
    }
}
