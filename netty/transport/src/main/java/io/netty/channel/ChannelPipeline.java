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

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;


/**
 * A list of {@link ChannelHandler}s which handles or intercepts
 * {@link ChannelEvent}s of a {@link Channel}.  {@link ChannelPipeline}
 * implements an advanced form of the
 * <a href="http://java.sun.com/blueprints/corej2eepatterns/Patterns/InterceptingFilter.html">Intercepting
 * Filter</a> pattern to give a user full control over how an event is handled
 * and how the {@link ChannelHandler}s in the pipeline interact with each other.
 *
 * <h3>Creation of a pipeline</h3>
 *
 * 必须为每一个新创建的channel，创建一个新的pipeline。
 * 
 * 一旦绑定，channel和pipeline的绑定是永久的。channel不能再附着到其他pipeline上，也不能从当前pipeline上卸除。
 * 
 * 使用Channels工具类来创建pipeline。
 * 
 * <pre>
 * import static io.netty.channel.{@link Channels}.*;
 * {@link ChannelPipeline} pipeline = pipeline(); // same with Channels.pipeline()
 * </pre>
 *
 * <h3>How an event flows in a pipeline</h3>
 *
 * A {@link ChannelEvent可以被ChannelUpstreamHandler或者ChannelDownstreamHandler处理，
 * 并可以调用sendUpstream，sendDownstream将其发送到最接近的handler
 * 
 * upstream和downstream处理event的拦截器可能不同。
 * 
 * 参考channelEvent
 * 
 * <pre>
 *                                       I/O Request
 *                                     via Channel or ChannelHandlerContext
 *                                           |
 *  +----------------------------------------+---------------+
 *  |                  ChannelPipeline       |               |
 *  |                                       \|/              |
 *  |  +----------------------+  +-----------+------------+  |
 *  |  | Upstream Handler  N  |  | Downstream Handler  1  |  |
 *  |  +----------+-----------+  +-----------+------------+  |
 *  |            /|\                         |               |
 *  |             |                         \|/              |
 *  |  +----------+-----------+  +-----------+------------+  |
 *  |  | Upstream Handler N-1 |  | Downstream Handler  2  |  |
 *  |  +----------+-----------+  +-----------+------------+  |
 *  |            /|\                         .               |
 *  |             .                          .               |
 *  |     [ sendUpstream() ]        [ sendDownstream() ]     |
 *  |     [ + VAL_INBOUND data ]        [ + VAL_OUTBOUND data  ]     |
 *  |             .                          .               |
 *  |             .                         \|/              |
 *  |  +----------+-----------+  +-----------+------------+  |
 *  |  | Upstream Handler  2  |  | Downstream Handler M-1 |  |
 *  |  +----------+-----------+  +-----------+------------+  |
 *  |            /|\                         |               |
 *  |             |                         \|/              |
 *  |  +----------+-----------+  +-----------+------------+  |
 *  |  | Upstream Handler  1  |  | Downstream Handler  M  |  |
 *  |  +----------+-----------+  +-----------+------------+  |
 *  |            /|\                         |               |
 *  +-------------+--------------------------+---------------+
 *                |                         \|/
 *  +-------------+--------------------------+---------------+
 *  |             |                          |               |
 *  |     [ Socket.read() ]          [ Socket.write() ]      |
 *  |                                                        |
 *  |  Netty Internal I/O Threads (Transport Implementation) |
 *  +--------------------------------------------------------+
 * </pre>
 * 
 * An upstream 是从下到上的方式拦截过滤。 An upstream handler通常处理IO线程尝试的inbound数据。
 *  The inbound 数据通常通过InputStream#read(byte[])从一个remote peer中读取。
 *  
 * If an upstream event goes beyond the top upstream handler, it is discarded
 * silently.
 * <p>
 * A downstream event 被从上到下处理.  A downstream handler通常生成或者传递outbound数据，
 * 比如写请求。
 * usually generates or transforms the outbound traffic such as write requests.
 * If a downstream event goes beyond the bottom downstream handler, it is
 * handled by an I/O thread associated with the {@link Channel}. 
 * 
 * The I/O thread
 * often performs the actual output operation such as {@link OutputStream#write(byte[])}.
 * <p>
 * For example, let us assume that we created the following pipeline:
 * <pre>
 * {@link ChannelPipeline} p = {@link Channels}.pipeline();
 * p.addLast("1", new UpstreamHandlerA());
 * p.addLast("2", new UpstreamHandlerB());
 * p.addLast("3", new DownstreamHandlerA());
 * p.addLast("4", new DownstreamHandlerB());
 * p.addLast("5", new UpstreamHandlerX());
 * </pre>
 * In the example above, the class whose name starts with {@code Upstream} means
 * it is an upstream handler.  The class whose name starts with
 * {@code Downstream} means it is a downstream handler.
 * <p>
 * 
 * up 是 12345 down 是 54321
 * 
 * In the given example configuration, the handler evaluation order is 1, 2, 3,
 * 4, 5 when an event goes upstream.  When an event goes downstream, the order
 * is 5, 4, 3, 2, 1.  On top of this principle, {@link ChannelPipeline} skips
 * the evaluation of certain handlers to shorten the stack depth:
 * <ul>
 * 不实现接口就不处理
 * <li>3 and 4 don't implement {@link ChannelUpstreamHandler}, and therefore the
 *     actual evaluation order of an upstream event will be: 1, 2, and 5.</li>
 * <li>1, 2, and 5 don't implement {@link ChannelDownstreamHandler}, and
 *     therefore the actual evaluation order of a downstream event will be:
 *     4 and 3.</li>
 * <li>If 5 extended {@link SimpleChannelHandler} which implements both
 *     {@link ChannelUpstreamHandler} and {@link ChannelDownstreamHandler}, the
 *     evaluation order of an upstream and a downstream event could be 125 and
 *     543 respectively.</li>
 * </ul>
 *
 * <h3>Building a pipeline</h3>
 * <p>
 * A user is supposed to have one or more {@link ChannelHandler}s in a
 * pipeline to receive I/O events (e.g. read) and to request I/O operations
 * (e.g. write and close).  For example, a typical server will have the following
 * handlers in each channel's pipeline, but your mileage may vary depending on
 * the complexity and characteristics of the protocol and business logic:
 *
 * <ol>
 * <li>Protocol Decoder - 将二进制数据ByteBuf翻译为java对象
 * <li>Protocol Encoder - 将java对象翻译为ByteBuf
 * <li><tt>ExecutionHandler  - 应用一个线程模型
 * <li>Business Logic Handler - 业务逻辑处理，比如数据库存取
 * </ol>
 *
 * and it could be represented as shown in the following example:
 *
 * <pre>
 * {@link ChannelPipeline} pipeline = {@link Channels#pipeline() Channels.pipeline()};
 * pipeline.addLast("decoder", new MyProtocolDecoder());
 * pipeline.addLast("encoder", new MyProtocolEncoder());
 * pipeline.addLast("executor", new ExecutionHandler(...));
 * pipeline.addLast("handler", new MyBusinessLogicHandler());
 * </pre>
 *
 * <h3>线程安全</h3>
 * <p>
 * ChannelPipeline是线程安全的，所以ChannelHandler可以在任何时候被添加。
 *
 * <h3>陷阱</h3>
 * <p>
 * Due to the internal implementation detail of the current default
 * {@link ChannelPipeline}, the following code does not work as expected if
 * <tt>FirstHandler</tt> is the last handler in the pipeline:
 * 
 * 如果第一个handler是最后一个handler
 * <pre>
 * public class FirstHandler extends {@link SimpleChannelUpstreamHandler} {
 *
 *     {@code @Override}
 *     public void messageReceived({@link ChannelHandlerContext} ctx, {@link MessageEvent} e) {
 *         // 删除自身
 *         ctx.getPipeline().remove(this);
 *         // 添加第二个
 *         ctx.getPipeline().addLast("2nd", new SecondHandler());
 *         // 转发到下一个handler
 *         ctx.sendUpstream(e);
 *     }
 * }
 * </pre>
 * 
 * 以上会有问题，要实现上面的行为，你需要在删除自身前首先添加第二个handler。
 * 
 * 要保证最少有一个handler
 * 
 * @apiviz.landmark
 * @apiviz.composedOf io.netty.channel.ChannelHandlerContext
 * @apiviz.owns       io.netty.channel.ChannelHandler
 */
public interface ChannelPipeline extends ChannelInboundInvoker, ChannelOutboundInvoker {

    MessageBuf<Object> inboundMessageBuffer();
    ByteBuf inboundByteBuffer();
    MessageBuf<Object> outboundMessageBuffer();
    ByteBuf outboundByteBuffer();

    /**
     * Inserts a {@link ChannelHandler} at the first position of this pipeline.
     *
     * @param name     the name of the handler to insert first
     * @param handler  the handler to insert first
     *
     * @throws IllegalArgumentException
     *         if there's an entry with the same name already in the pipeline
     * @throws NullPointerException
     *         if the specified name or handler is {@code null}
     */
    ChannelPipeline addFirst(String name, ChannelHandler handler);

    /**
     * Inserts a {@link ChannelHandler} at the first position of this pipeline.
     *
     * @param name     the name of the handler to insert first
     * @param handler  the handler to insert first
     *
     * @throws IllegalArgumentException
     *         if there's an entry with the same name already in the pipeline
     * @throws NullPointerException
     *         if the specified name or handler is {@code null}
     */
    ChannelPipeline addFirst(EventExecutor executor, String name, ChannelHandler handler);

    /**
     * Appends a {@link ChannelHandler} at the last position of this pipeline.
     *
     * @param name     the name of the handler to append
     * @param handler  the handler to append
     *
     * @throws IllegalArgumentException
     *         if there's an entry with the same name already in the pipeline
     * @throws NullPointerException
     *         if the specified name or handler is {@code null}
     */
    ChannelPipeline addLast(String name, ChannelHandler handler);

    /**
     * Appends a {@link ChannelHandler} at the last position of this pipeline.
     *
     * @param name     the name of the handler to append
     * @param handler  the handler to append
     *
     * @throws IllegalArgumentException
     *         if there's an entry with the same name already in the pipeline
     * @throws NullPointerException
     *         if the specified name or handler is {@code null}
     */
    ChannelPipeline addLast(EventExecutor executor, String name, ChannelHandler handler);

    /**
     * Inserts a {@link ChannelHandler} before an existing handler of this
     * pipeline.
     *
     * @param baseName  the name of the existing handler
     * @param name      the name of the handler to insert before
     * @param handler   the handler to insert before
     *
     * @throws NoSuchElementException
     *         if there's no such entry with the specified {@code baseName}
     * @throws IllegalArgumentException
     *         if there's an entry with the same name already in the pipeline
     * @throws NullPointerException
     *         if the specified baseName, name, or handler is {@code null}
     */
    ChannelPipeline addBefore(String baseName, String name, ChannelHandler handler);

    /**
     * Inserts a {@link ChannelHandler} before an existing handler of this
     * pipeline.
     *
     * @param baseName  the name of the existing handler
     * @param name      the name of the handler to insert before
     * @param handler   the handler to insert before
     *
     * @throws NoSuchElementException
     *         if there's no such entry with the specified {@code baseName}
     * @throws IllegalArgumentException
     *         if there's an entry with the same name already in the pipeline
     * @throws NullPointerException
     *         if the specified baseName, name, or handler is {@code null}
     */
    ChannelPipeline addBefore(EventExecutor executor, String baseName, String name, ChannelHandler handler);

    /**
     * Inserts a {@link ChannelHandler} after an existing handler of this
     * pipeline.
     *
     * @param baseName  the name of the existing handler
     * @param name      the name of the handler to insert after
     * @param handler   the handler to insert after
     *
     * @throws NoSuchElementException
     *         if there's no such entry with the specified {@code baseName}
     * @throws IllegalArgumentException
     *         if there's an entry with the same name already in the pipeline
     * @throws NullPointerException
     *         if the specified baseName, name, or handler is {@code null}
     */
    ChannelPipeline addAfter(String baseName, String name, ChannelHandler handler);

    /**
     * Inserts a {@link ChannelHandler} after an existing handler of this
     * pipeline.
     *
     * @param baseName  the name of the existing handler
     * @param name      the name of the handler to insert after
     * @param handler   the handler to insert after
     *
     * @throws NoSuchElementException
     *         if there's no such entry with the specified {@code baseName}
     * @throws IllegalArgumentException
     *         if there's an entry with the same name already in the pipeline
     * @throws NullPointerException
     *         if the specified baseName, name, or handler is {@code null}
     */
    ChannelPipeline addAfter(EventExecutor executor, String baseName, String name, ChannelHandler handler);

    ChannelPipeline addFirst(ChannelHandler... handlers);

    ChannelPipeline addFirst(EventExecutor executor, ChannelHandler... handlers);

    ChannelPipeline addLast(ChannelHandler... handlers);

    ChannelPipeline addLast(EventExecutor executor, ChannelHandler... handlers);

    /**
     * Removes the specified {@link ChannelHandler} from this pipeline.
     *
     * @throws NoSuchElementException
     *         if there's no such handler in this pipeline
     * @throws NullPointerException
     *         if the specified handler is {@code null}
     */
    void remove(ChannelHandler handler);

    /**
     * Removes the {@link ChannelHandler} with the specified name from this
     * pipeline.
     *
     * @return the removed handler
     *
     * @throws NoSuchElementException
     *         if there's no such handler with the specified name in this pipeline
     * @throws NullPointerException
     *         if the specified name is {@code null}
     */
    ChannelHandler remove(String name);

    /**
     * Removes the {@link ChannelHandler} of the specified type from this
     * pipeline
     *
     * @param <T>          the type of the handler
     * @param handlerType  the type of the handler
     *
     * @return the removed handler
     *
     * @throws NoSuchElementException
     *         if there's no such handler of the specified type in this pipeline
     * @throws NullPointerException
     *         if the specified handler type is {@code null}
     */
    <T extends ChannelHandler> T remove(Class<T> handlerType);

    /**
     * Removes the first {@link ChannelHandler} in this pipeline.
     *
     * @return the removed handler
     *
     * @throws NoSuchElementException
     *         if this pipeline is empty
     */
    ChannelHandler removeFirst();

    /**
     * Removes the last {@link ChannelHandler} in this pipeline.
     *
     * @return the removed handler
     *
     * @throws NoSuchElementException
     *         if this pipeline is empty
     */
    ChannelHandler removeLast();

    /**
     * Replaces the specified {@link ChannelHandler} with a new handler in
     * this pipeline.
     *
     * @throws NoSuchElementException
     *         if the specified old handler does not exist in this pipeline
     * @throws IllegalArgumentException
     *         if a handler with the specified new name already exists in this
     *         pipeline, except for the handler to be replaced
     * @throws NullPointerException
     *         if the specified old handler, new name, or new handler is
     *         {@code null}
     */
    void replace(ChannelHandler oldHandler, String newName, ChannelHandler newHandler);

    /**
     * Replaces the {@link ChannelHandler} of the specified name with a new
     * handler in this pipeline.
     *
     * @return the removed handler
     *
     * @throws NoSuchElementException
     *         if the handler with the specified old name does not exist in this pipeline
     * @throws IllegalArgumentException
     *         if a handler with the specified new name already exists in this
     *         pipeline, except for the handler to be replaced
     * @throws NullPointerException
     *         if the specified old handler, new name, or new handler is
     *         {@code null}
     */
    ChannelHandler replace(String oldName, String newName, ChannelHandler newHandler);

    /**
     * Replaces the {@link ChannelHandler} of the specified type with a new
     * handler in this pipeline.
     *
     * @return the removed handler
     *
     * @throws NoSuchElementException
     *         if the handler of the specified old handler type does not exist
     *         in this pipeline
     * @throws IllegalArgumentException
     *         if a handler with the specified new name already exists in this
     *         pipeline, except for the handler to be replaced
     * @throws NullPointerException
     *         if the specified old handler, new name, or new handler is
     *         {@code null}
     */
    <T extends ChannelHandler> T replace(Class<T> oldHandlerType, String newName, ChannelHandler newHandler);

    /**
     * Returns the first {@link ChannelHandler} in this pipeline.
     *
     * @return the first handler.  {@code null} if this pipeline is empty.
     */
    ChannelHandler first();

    /**
     * Returns the last {@link ChannelHandler} in this pipeline.
     *
     * @return the last handler.  {@code null} if this pipeline is empty.
     */
    ChannelHandler last();

    /**
     * Returns the {@link ChannelHandler} with the specified name in this
     * pipeline.
     *
     * @return the handler with the specified name.
     *         {@code null} if there's no such handler in this pipeline.
     */
    ChannelHandler get(String name);

    /**
     * Returns the {@link ChannelHandler} of the specified type in this
     * pipeline.
     *
     * @return the handler of the specified handler type.
     *         {@code null} if there's no such handler in this pipeline.
     */
    <T extends ChannelHandler> T get(Class<T> handlerType);

    /**
     * Returns the context object of the specified {@link ChannelHandler} in
     * this pipeline.
     *
     * @return the context object of the specified handler.
     *         {@code null} if there's no such handler in this pipeline.
     */
    ChannelHandlerContext context(ChannelHandler handler);

    /**
     * Returns the context object of the {@link ChannelHandler} with the
     * specified name in this pipeline.
     *
     * @return the context object of the handler with the specified name.
     *         {@code null} if there's no such handler in this pipeline.
     */
    ChannelHandlerContext context(String name);

    /**
     * Returns the context object of the {@link ChannelHandler} of the
     * specified type in this pipeline.
     *
     * @return the context object of the handler of the specified type.
     *         {@code null} if there's no such handler in this pipeline.
     */
    ChannelHandlerContext context(Class<? extends ChannelHandler> handlerType);

    /**
     * Returns the {@link Channel} that this pipeline is attached to.
     *
     * @return the channel. {@code null} if this pipeline is not attached yet.
     */
    Channel channel();

    /**
     * Returns the {@link List} of the handler names.
     */
    List<String> names();

    /**
     * Converts this pipeline into an ordered {@link Map} whose keys are
     * handler names and whose values are handlers.
     */
    Map<String, ChannelHandler> toMap();
}
