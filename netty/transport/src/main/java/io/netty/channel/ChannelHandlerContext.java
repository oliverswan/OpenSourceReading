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
import io.netty.util.AttributeMap;

import java.nio.channels.Channels;
import java.util.Set;

/**
 * 允许ChannelHandler和它的ChannelPipeline或其他handler进行交互。
 * 一个handler可以发送一个ChannelEvent到upstream或者downstream，还可以动态修改它所在的ChannelPipeline
 * 
 * <h3>发送事件</h3>
 *
 * 通过调用sendUpstream，sendDownstream可以发送ChannelEvent到同一个ChannelPipeline中的最近的handler 
 * 参考ChannelPipeline来了解事件的流程。
 *
 * <h3>Modifying a pipeline</h3>
 *
 * 调用getPipeline()可以获取所属的ChannelPipeline
 * 
 * 可以在运行时，对handler进行增，删，替换。
 *
 * <h3>Retrieving for later use</h3>
 *
 * 可以保持对ChannelHandlerContext的引用，来让后面使用, 比如在handler方法之外触发事件，甚至从另外要给线程。
 * 
 * <pre>
 * public class MyHandler extends {@link SimpleChannelHandler}
 *                        implements {@link LifeCycleAwareChannelHandler} {
 *
 *		// 保存context
 *     <b>private {@link ChannelHandlerContext} ctx;</b>
 *
 *     public void beforeAdd({@link ChannelHandlerContext} ctx) {
 *         <b>this.ctx = ctx;</b>
 *     }
 *
 *     public void login(String username, password) {
 *         {@link Channels}.write(
 *                 <b>this.ctx</b>,
 *                 {@link Channels}.succeededFuture(<b>this.ctx.getChannel()</b>),
 *                 new LoginMessage(username, password));
 *     }
 *     ...
 * }
 * </pre>
 *
 * <h3>Storing stateful information</h3>
 *
 * {@link #setAttachment(Object)} and {@link #getAttachment()} allow you to
 * store and access stateful information that is related with a handler and its
 * context.  Please refer to {@link ChannelHandler} to learn various recommended
 * ways to manage stateful information.
 *
 * <h3>A handler can have more than one context</h3>
 *
 * Please note that a {@link ChannelHandler} instance can be added to more than
 * one {@link ChannelPipeline}.  It means a single {@link ChannelHandler}
 * instance can have more than one {@link ChannelHandlerContext} and therefore
 * the single instance can be invoked with different
 * {@link ChannelHandlerContext}s if it is added to one or more
 * {@link ChannelPipeline}s more than once.
 * <p>
 * For example, the following handler will have as many independent attachments
 * as how many times it is added to pipelines, regardless if it is added to the
 * same pipeline multiple times or added to different pipelines multiple times:
 * <pre>
 * public class FactorialHandler extends {@link SimpleChannelHandler} {
 *
 *   // This handler will receive a sequence of increasing integers starting
 *   // from 1.
 *   {@code @Override}
 *   public void messageReceived({@link ChannelHandlerContext} ctx, {@link MessageEvent} evt) {
 *     Integer a = (Integer) ctx.getAttachment();
 *     Integer b = (Integer) evt.getMessage();
 *
 *     if (a == null) {
 *       a = 1;
 *     }
 *
 *     ctx.setAttachment(Integer.valueOf(a * b));
 *   }
 * }
 *
 * // Different context objects are given to "f1", "f2", "f3", and "f4" even if
 * // they refer to the same handler instance.  Because the FactorialHandler
 * // stores its state in a context object (as an attachment), the factorial is
 * // calculated correctly 4 times once the two pipelines (p1 and p2) are active.
 * FactorialHandler fh = new FactorialHandler();
 *
 * {@link ChannelPipeline} p1 = {@link Channels}.pipeline();
 * p1.addLast("f1", fh);
 * p1.addLast("f2", fh);
 *
 * {@link ChannelPipeline} p2 = {@link Channels}.pipeline();
 * p2.addLast("f3", fh);
 * p2.addLast("f4", fh);
 * </pre>
 *
 * <h3>Additional resources worth reading</h3>
 * <p>
 * Please refer to the {@link ChannelHandler}, {@link ChannelEvent}, and
 * {@link ChannelPipeline} to find out what a upstream event and a downstream
 * event are, what fundamental differences they have, how they flow in a
 * pipeline,  and how to handle the event in your application.
 * @apiviz.owns io.netty.channel.ChannelHandler
 */
public interface ChannelHandlerContext
         extends AttributeMap, ChannelFutureFactory,
                 ChannelInboundInvoker, ChannelOutboundInvoker {
    Channel channel();
    ChannelPipeline pipeline();
    EventExecutor executor();

    String name();
    ChannelHandler handler();
    Set<ChannelHandlerType> type();

    boolean hasInboundByteBuffer();
    boolean hasInboundMessageBuffer();
    ByteBuf inboundByteBuffer();
    <T> MessageBuf<T> inboundMessageBuffer();

    boolean hasOutboundByteBuffer();
    boolean hasOutboundMessageBuffer();
    ByteBuf outboundByteBuffer();
    <T> MessageBuf<T> outboundMessageBuffer();

    boolean hasNextInboundByteBuffer();
    boolean hasNextInboundMessageBuffer();
    ByteBuf nextInboundByteBuffer();
    MessageBuf<Object> nextInboundMessageBuffer();

    boolean hasNextOutboundByteBuffer();
    boolean hasNextOutboundMessageBuffer();
    ByteBuf nextOutboundByteBuffer();
    MessageBuf<Object> nextOutboundMessageBuffer();
}
