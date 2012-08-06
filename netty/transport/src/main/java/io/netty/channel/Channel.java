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
package io.netty.channel;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.MessageBuf;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.AttributeMap;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;


/**
 * socket的代理，可以进行io操作
 * 
 * <p>
 * A channel provides a user:
 * <ul>
 * <li>当前状态(e.g. is it open? is it connected?),</li>
 * <li>配置参数 ChannelConfig (e.g. receive buffer size),</li>
 * <li>IO操作 (e.g. read, write, connect, and bind), and</li>
 * <li>ChannelPipeline用来处理该channel的事件</li>
 * </ul>
 *
 * <h3>所有的IO操作都是异步的.</h3>
 * <p>
 * All I/O operations in Netty are asynchronous.  It means any I/O calls will
 * return immediately with no guarantee that the requested I/O operation has
 * been completed at the end of the call.  Instead, you will be returned with
 * a {@link ChannelFuture} instance which will notify you when the requested I/O
 * operation has succeeded, failed, or canceled.
 *
 * <h3>Channels是层级结构的</h3>
 * 
 * serversocket会作为接受的socket的parent
 * 
 * <p>
 * The semantics of the hierarchical structure depends on the transport
 * implementation where the {@link Channel} belongs to.  For example, you could
 * write a new {@link Channel} implementation that creates the sub-channels that
 * share one socket connection, as <a href="http://beepcore.org/">BEEP</a> and
 * <a href="http://en.wikipedia.org/wiki/Secure_Shell">SSH</a> do.
 * 
 * <h3>Downcast to access transport-specific operations</h3>
 * <p>
 * Some transports exposes additional operations that is specific to the
 * transport.  Down-cast the {@link Channel} to sub-type to invoke such
 * operations.  For example, with the old I/O datagram transport, multicast
 * join / leave operations are provided by {@link DatagramChannel}.
 *
 * <h3>InterestOps</h3>
 * <p>
 * Channel有一个叫做interestOps的属性。很像SelectionKey#interestOps()的SelectionKey
 * 
 * 表现为一个位成员属性<a href="http://en.wikipedia.org/wiki/Bit_field">bit
 * field</a> which is composed of the two flags.
 * <ul>
 * <li>{@link #OP_READ} - 如果设置了的话，远程peer发送的数据，会立即读取。  如果没设置的话，数据不会读取直到OP_READ被重新设置
 * <li>{@link #OP_WRITE} - 如果没有被设置，写请求会被flush。如果设置了写请求会被放入到队列而不发送。
 * <li>{@link #OP_READ_WRITE} - 表示只停止写
 * <li>{@link #OP_NONE} - 表示只停止读
 * </ul>
 * </p><p>
 * setReadable方法来暂停恢复读操作
 * </p><p>
 * 
 * 你不能像读那样，设置写标志，来控制写操作的暂停和恢复。
 * 
 * OP_WRITE标志是只读的，只是用来告诉你还没有处理的写请求是否超过了限制，这样比就不要继续写操作。
 * Please note that you cannot suspend or resume write operation just like you
 * can set or clear {@link #OP_READ}. The {@link #OP_WRITE} flag is read only
 * and provided simply as a mean to tell you if the size of pending write
 * requests exceeded a certain threshold or not so that you don't issue too many
 * pending writes that lead to an {@link OutOfMemoryError}.  For example, the
 * NIO socket transport uses the {@code writeBufferLowWaterMark} and
 * {@code writeBufferHighWaterMark} properties in {@link NioSocketChannelConfig}
 * to determine when to set or clear the {@link #OP_WRITE} flag.
 * </p>
 * @apiviz.landmark
 * @apiviz.composedOf io.netty.channel.ChannelConfig
 * @apiviz.composedOf io.netty.channel.ChannelPipeline
 *
 * @apiviz.exclude ^io\.netty\.channel\.([a-z]+\.)+[^\.]+Channel$
 */
public interface Channel extends AttributeMap, ChannelOutboundInvoker, ChannelFutureFactory, Comparable<Channel> {

    /**
     * Returns the unique integer ID of this channel.
     */
    Integer id();

    EventLoop eventLoop();

    /**
     * Returns the parent of this channel.
     *
     * @return the parent channel.
     *         {@code null} if this channel does not have a parent channel.
     */
    Channel parent();

    /**
     * Returns the configuration of this channel.
     */
    ChannelConfig config();

    /**
     * 返回ChannelPipeline，用来处理本Channel的ChannelEvent
     */
    ChannelPipeline pipeline();

    boolean isOpen();
    boolean isRegistered();
    boolean isActive();

    ChannelMetadata metadata();

    ByteBuf outboundByteBuffer();
    <T> MessageBuf<T> outboundMessageBuffer();

    /**
     * Returns the local address where this channel is bound to.  The returned
     * {@link SocketAddress} is supposed to be down-cast into more concrete
     * type such as {@link InetSocketAddress} to retrieve the detailed
     * information.
     *
     * @return the local address of this channel.
     *         {@code null} if this channel is not bound.
     */
    SocketAddress localAddress();

    /**
     * 返回本Channel连上的远端地址.  The
     * returned {@link SocketAddress} is supposed to be down-cast into more
     * concrete type such as {@link InetSocketAddress} to retrieve the detailed
     * information.
     *
     * @return the remote address of this channel.
     *         {@code null} if this channel is not connected.
     *         If this channel is not connected but it can receive messages
     *         from arbitrary remote addresses (e.g. {@link DatagramChannel},
     *         use {@link MessageEvent#getRemoteAddress()} to determine
     *         the origination of the received message as this method will
     *         return {@code null}.
     */
    SocketAddress remoteAddress();

    /**
     * Returns the {@link ChannelFuture} which will be notified when this
     * channel is closed.  This method always returns the same future instance.
     */
    ChannelFuture closeFuture();

    Unsafe unsafe();
    // 主要的连接逻辑
    interface Unsafe {
        ChannelHandlerContext directOutboundContext();
        ChannelFuture voidFuture();

        SocketAddress localAddress();
        SocketAddress remoteAddress();

        void register(EventLoop eventLoop, ChannelFuture future);
        void bind(SocketAddress localAddress, ChannelFuture future);
        void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelFuture future);
        void disconnect(ChannelFuture future);
        void close(ChannelFuture future);
        void deregister(ChannelFuture future);

        void flush(ChannelFuture future);
        void flushNow();
    }
}
