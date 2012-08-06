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

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.group.ChannelGroup;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.channels.Channels;

/**
 * 处理或将ChannelEvent发送给ChannelPipeline中另外一个ChannelHandler
 * 
 * <h3>Sub-types</h3>
 * <p>
 * 自己本身不提供任何方法，需要实现它的子接口。
 * 
 * 有两个子接口处理接收到的事件，
 * ChannelUpstreamHandler用来处理upstream事件
 * ChannelDownstreamHandler处理downstream事件
 * 
 * <h3>The context object</h3>
 * <p>
 * 
 * ChannelHandler由ChannelHandlerContext来提供
 * ChannelHandler应该与它所在的ChannelPipeline通过context 对象交互。
 * 
 * ChannelHandler使用context object可以将事件传递给 upstream or
 * downstream, 或者动态修改pipeline, 或者将和特定handler的数据保存到context对象上
 *
 * <h3>状态管理</h3>
 *
 * A {@link ChannelHandler} 通常需要存储一些状态信息，推荐的方法是使用成员变量:
 * <pre>
 * public class DataServerHandler extends {@link SimpleChannelHandler} {
 *
 *     <b>private boolean loggedIn;</b>
 *
 *     {@code @Override}
 *     public void messageReceived({@link ChannelHandlerContext} ctx, {@link MessageEvent} e) {
 *         {@link Channel} ch = e.getChannel();
 *         Object o = e.getMessage();
 *         if (o instanceof LoginMessage) {
 *             authenticate((LoginMessage) o);
 *             <b>loggedIn = true;</b>
 *         } else (o instanceof GetDataMessage) {
 *             if (<b>loggedIn</b>) {
 *                 ch.write(fetchSecret((GetDataMessage) o));
 *             } else {
 *                 fail();
 *             }
 *         }
 *     }
 *     ...
 * }
 * </pre>
 * 因为 the handler 实例有一个状态变量，是和一个特定连接绑定的，
 * 你必须为每一个新的channel创建一个新的handler实例，来避免竞争race condition，
 * 这样非认证的客户端可以获取机密信息。
 * <pre>
 * // 为每一个channel创建一个channelhandler
 * // See {@link Bootstrap#setPipelineFactory(ChannelPipelineFactory)}.
 * public class DataServerPipelineFactory implements {@link ChannelPipelineFactory} {
 *     public {@link ChannelPipeline} getPipeline() {
 *         return {@link Channels}.pipeline(<b>new DataServerHandler()</b>);
 *     }
 * }
 * </pre>
 *
 * <h4>使用附件</h4>
 *
 * Although it's recommended to use member variables to store the state of a
 * handler, for some reason you might not want to create many handler instances.
 * In such a case, you can use an <em>attachment</em> which is provided by
 * {@link ChannelHandlerContext}:
 * <pre>
 * {@code @Sharable}
 * public class DataServerHandler extends {@link SimpleChannelHandler} {
 *
 *     {@code @Override}
 *     public void messageReceived({@link ChannelHandlerContext} ctx, {@link MessageEvent} e) {
 *         {@link Channel} ch = e.getChannel();
 *         Object o = e.getMessage();
 *         if (o instanceof LoginMessage) {
 *             authenticate((LoginMessage) o);
 *             <b>ctx.setAttachment(true)</b>;
 *         } else (o instanceof GetDataMessage) {
 *             if (<b>Boolean.TRUE.equals(ctx.getAttachment())</b>) {
 *                 ch.write(fetchSecret((GetDataMessage) o));
 *             } else {
 *                 fail();
 *             }
 *         }
 *     }
 *     ...
 * }
 * </pre>
 * Now that the state of the handler is stored as an attachment, 
 * 你可以将同一个handler实例，添加给不同的pipelines
 * 
 * <pre>
 * public class DataServerPipelineFactory implements {@link ChannelPipelineFactory} {
 *
 *     private static final DataServerHandler <b>SHARED</b> = new DataServerHandler();
 *
 *     public {@link ChannelPipeline} getPipeline() {
 *         return {@link Channels}.pipeline(<b>SHARED</b>);
 *     }
 * }
 * </pre>
 *
 * <h4>Using a {@link ChannelLocal}</h4>
 *
 * 如果有一个状态变量，需要从channel外部读取，你可以使用ChannelLocal。
 * 
 * If you have a state variable which needs to be accessed either from other
 * handlers or outside handlers, you can use {@link ChannelLocal}:
 * <pre>
 * public final class DataServerState {
 *
 *     <b>public static final ChannelLocal<Boolean> loggedIn = new {@link ChannelLocal}&lt;&gt;() {
 *         protected Boolean initialValue(Channel channel) {
 *             return false;
 *         }
 *     }</b>
 *     ...
 * }
 *
 * {@code @Sharable}
 * public class DataServerHandler extends {@link SimpleChannelHandler} {
 *
 *     {@code @Override}
 *     public void messageReceived({@link ChannelHandlerContext} ctx, {@link MessageEvent} e) {
 *         Channel ch = e.getChannel();
 *         Object o = e.getMessage();
 *         if (o instanceof LoginMessage) {
 *             authenticate((LoginMessage) o);
 *             <b>DataServerState.loggedIn.set(ch, true);</b>
 *         } else (o instanceof GetDataMessage) {
 *             if (<b>DataServerState.loggedIn.get(ch)</b>) {
 *                 ctx.getChannel().write(fetchSecret((GetDataMessage) o));
 *             } else {
 *                 fail();
 *             }
 *         }
 *     }
 *     ...
 * }
 *
 * // Print the remote addresses of the authenticated clients:
 * {@link ChannelGroup} allClientChannels = ...;
 * for ({@link Channel} ch: allClientChannels) {
 *     if (<b>DataServerState.loggedIn.get(ch)</b>) {
 *         System.out.println(ch.getRemoteAddress());
 *     }
 * }
 * </pre>
 *
 * <h4>The {@code @Sharable} annotation</h4>
 * <p>
 * In the examples above which used an attachment or a {@link ChannelLocal},
 * you might have noticed the {@code @Sharable} annotation.
 * <p>
 * If a {@link ChannelHandler} is annotated with the {@code @Sharable}
 * annotation, it means you can create an instance of the handler just once and
 * add it to one or more {@link ChannelPipeline}s multiple times without
 * a race condition.
 * <p>
 * If this annotation is not specified, you have to create a new handler
 * instance every time you add it to a pipeline because it has unshared state
 * such as member variables.
 * <p>
 * This annotation is provided for documentation purpose, just like
 * <a href="http://www.javaconcurrencyinpractice.com/annotations/doc/">the JCIP annotations</a>.
 *
 * <h3>Additional resources worth reading</h3>
 * <p>
 * Please refer to the {@link ChannelEvent} and {@link ChannelPipeline} to find
 * out what a upstream event and a downstream event are, what fundamental
 * differences they have, and how they flow in a pipeline.
 * @apiviz.landmark
 * @apiviz.exclude ^io\.netty\.handler\..*$
 */
public interface ChannelHandler {

    void beforeAdd(ChannelHandlerContext ctx) throws Exception;
    void afterAdd(ChannelHandlerContext ctx) throws Exception;
    void beforeRemove(ChannelHandlerContext ctx) throws Exception;
    void afterRemove(ChannelHandlerContext ctx) throws Exception;

    void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception;
    void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception;

    /**
     * 表明相同的ChannelHandler实例可以被多次添加到一个或多个ChannelPipeline中，并不会有race condition
     * <p>
     * 如果这个标志没有指定, 那么每次添加到pipeline的时候，都需要新建一个实例。
     * 因为他们有不能共享的成员变量
     * <p>
     * This annotation is provided for documentation purpose, just like
     * <a href="http://www.javaconcurrencyinpractice.com/annotations/doc/">the JCIP annotations</a>.
     */
    @Inherited
    @Documented
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Sharable {
        // no value
    }
}
