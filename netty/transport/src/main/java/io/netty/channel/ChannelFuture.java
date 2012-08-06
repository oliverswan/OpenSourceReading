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

import java.util.concurrent.TimeUnit;

/**
 * 异步的I/O操作的结果。
 * 
 * 任何IO操作都是异步的，不保证方法返回后什么时候执行。
 * 而是，会被返回一个ChannelFuture实例，会告诉你IO操作的状态或者结果。
 * 
 * IO操作开始的时候，会创建一个新的future实例.  
 * 
 * 新的future实例没有完全初始化- 它不成功，失败，也不是取消。因为IO操作还没有结束.  
 * 
 * 如果IO操作成功，失败或以取消结束。future会标记为completed，并附带很多特定的消息。比如导致失败的信息。
 * 
 * 即使失败或者取消，也是属于completed状态。
 * 
 * <pre>
 *                                      +---------------------------+
 *                                      | Completed successfully    |
 *                                      +---------------------------+
 *                                 +---->      isDone() = <b>true</b>      |
 * +--------------------------+    |    |   isSuccess() = <b>true</b>      |
 * |        Uncompleted       |    |    +===========================+
 * +--------------------------+    |    | Completed with failure    |
 * |      isDone() = <b>false</b>    |    |    +---------------------------+
 * |   isSuccess() = false    |----+---->   isDone() = <b>true</b>         |
 * | isCancelled() = false    |    |    | getCause() = <b>non-null</b>     |
 * |    getCause() = null     |    |    +===========================+
 * +--------------------------+    |    | Completed by cancellation |
 *                                 |    +---------------------------+
 *                                 +---->      isDone() = <b>true</b>      |
 *                                      | isCancelled() = <b>true</b>      |
 *                                      +---------------------------+
 * </pre>
 *
 * 有很多方法可以用来检查IO方法是否已经completed，等待completed，获取IO操作的结果。
 * 可以添加ChannelFutureListener，来当IO操作completed的时候被通知。
 * 
 * 推荐#addListener(ChannelFutureListener)，相对于await()
 *
 * <p>
 * addListener(ChannelFutureListener)是非阻塞的  
 * ChannelFutureListener能更好地利用资源，因为它根本就不阻塞。
 * 
 * it could be tricky to implement a sequential logic if you are not used to
 * event-driven programming.
 * 
 * 如果你不是使用事件驱动编程，可以实现一个连续的逻辑。
 * 
 * <p>
 * 相比之下await()就是一个阻塞的方法.  消耗更大，并有可能导致死锁。
 *
 * 不要在ChannelHandler里调用await方法。
 * 
 * <p>
 * ChannelHandler的事件处理方法，会被IO线程调用。
 * 如果在事件处理方法里调用await，这个事件处理方法又是被IO线程调用，IO操作会一直等待下去，因为await可能阻塞IO
 * 操作，这样形成dead lock
 * 
 * <pre>
 * // 永远不要这样做
 * {@code @Override}
 * public void messageReceived({@link ChannelHandlerContext} ctx, {@link MessageEvent} e) {
 *     if (e.getMessage() instanceof GoodByeMessage) {
 *         {@link ChannelFuture} future = e.getChannel().close();
 *         future.awaitUninterruptibly();
 *         // Perform post-closure operation
 *         // ...
 *     }
 * }
 *
 * // 推荐是这样的
 * {@code @Override}
 * public void messageReceived({@link ChannelHandlerContext} ctx, {@link MessageEvent} e) {
 *     if (e.getMessage() instanceof GoodByeMessage) {
 *         {@link ChannelFuture} future = e.getChannel().close();
 *         future.addListener(new {@link ChannelFutureListener}() {
 *             public void operationComplete({@link ChannelFuture} future) {
 *                 // Perform post-closure operation
 *                 // ...
 *             }
 *         });
 *     }
 * }
 * </pre>
 * <p>
 * 
 * 不要在IO线程里调用await方法，否则会抛出IllegalStateException异常来阻止dead lock。
 * 
 *
 * <h3>不要混淆IO超时和await超时</h3>
 *
 * await的参数指定的超时时间，和IO的超时时间没有任何关系。
 * 
 * If an I/O operation times out, the future will be marked as
 * 'completed with failure,' as depicted in the diagram above.  For example,
 * connect timeout should be configured via a transport-specific option:
 * <pre>
 * // 不要这样做，不要用await来等待超时
 * {@link ClientBootstrap} b = ...;
 * {@link ChannelFuture} f = b.connect(...);
 * f.awaitUninterruptibly(10, TimeUnit.SECONDS);
 * if (f.isCancelled()) {
 *     // Connection attempt cancelled by user
 * } else if (!f.isSuccess()) {
 *     // You might get a NullPointerException here because the future
 *     // might not be completed yet.
 *     f.getCause().printStackTrace();
 * } else {
 *     // Connection established successfully
 * }
 *
 * // 推荐通过设置Option来设置超时时间
 * {@link ClientBootstrap} b = ...;
 * // Configure the connect timeout option.
 * <b>b.setOption("connectTimeoutMillis", 10000);</b>
 * {@link ChannelFuture} f = b.connect(...);
 * f.awaitUninterruptibly();
 *
 * // Now we are sure the future is completed.
 * assert f.isDone();
 *
 * if (f.isCancelled()) {
 *     // Connection attempt cancelled by user
 * } else if (!f.isSuccess()) {
 *     f.getCause().printStackTrace();
 * } else {
 *     // Connection established successfully
 * }
 * </pre>
 * @apiviz.landmark
 * @apiviz.owns io.netty.channel.ChannelFutureListener - - notifies
 */
public interface ChannelFuture {

    /**
     * Returns a channel where the I/O operation associated with this
     * future takes place.
     */
    Channel channel();

    /**
     * Returns {@code true} if and only if this future is
     * complete, regardless of whether the operation was successful, failed,
     * or cancelled.
     */
    boolean isDone();

    /**
     * Returns {@code true} if and only if this future was
     * cancelled by a {@link #cancel()} method.
     */
    boolean isCancelled();

    /**
     * Returns {@code true} if and only if the I/O operation was completed
     * successfully.
     */
    boolean isSuccess();

    /**
     * Returns the cause of the failed I/O operation if the I/O operation has
     * failed.
     *
     * @return the cause of the failure.
     *         {@code null} if succeeded or this future is not
     *         completed yet.
     */
    Throwable cause();

    /**
     * Cancels the I/O operation associated with this future
     * and notifies all listeners if canceled successfully.
     *
     * @return {@code true} if and only if the operation has been canceled.
     *         {@code false} if the operation can't be canceled or is already
     *         completed.
     */
    boolean cancel();

    /**
     * Marks this future as a success and notifies all
     * listeners.
     *
     * @return {@code true} if and only if successfully marked this future as
     *         a success. Otherwise {@code false} because this future is
     *         already marked as either a success or a failure.
     */
    boolean setSuccess();

    /**
     * Marks this future as a failure and notifies all
     * listeners.
     *
     * @return {@code true} if and only if successfully marked this future as
     *         a failure. Otherwise {@code false} because this future is
     *         already marked as either a success or a failure.
     */
    boolean setFailure(Throwable cause);

    /**
     * Notifies the progress of the operation to the listeners that implements
     * {@link ChannelFutureProgressListener}. Please note that this method will
     * not do anything and return {@code false} if this future is complete
     * already.
     *
     * @return {@code true} if and only if notification was made.
     */
    boolean setProgress(long amount, long current, long total);

    /**
     * Adds the specified listener to this future.  The
     * specified listener is notified when this future is
     * {@linkplain #isDone() done}.  If this future is already
     * completed, the specified listener is notified immediately.
     */
    ChannelFuture addListener(ChannelFutureListener listener);

    /**
     * Removes the specified listener from this future.
     * The specified listener is no longer notified when this
     * future is {@linkplain #isDone() done}.  If the specified
     * listener is not associated with this future, this method
     * does nothing and returns silently.
     */
    ChannelFuture removeListener(ChannelFutureListener listener);

    /**
     * Waits for this future until it is done, and rethrows the cause of the failure if this future
     * failed.  If the cause of the failure is a checked exception, it is wrapped with a new
     * {@link ChannelException} before being thrown.
     */
    ChannelFuture sync() throws InterruptedException;

    /**
     * Waits for this future until it is done, and rethrows the cause of the failure if this future
     * failed.  If the cause of the failure is a checked exception, it is wrapped with a new
     * {@link ChannelException} before being thrown.
     */
    ChannelFuture syncUninterruptibly();

    /**
     * Waits for this future to be completed.
     *
     * @throws InterruptedException
     *         if the current thread was interrupted
     */
    ChannelFuture await() throws InterruptedException;

    /**
     * Waits for this future to be completed without
     * interruption.  This method catches an {@link InterruptedException} and
     * discards it silently.
     */
    ChannelFuture awaitUninterruptibly();

    /**
     * Waits for this future to be completed within the
     * specified time limit.
     *
     * @return {@code true} if and only if the future was completed within
     *         the specified time limit
     *
     * @throws InterruptedException
     *         if the current thread was interrupted
     */
    boolean await(long timeout, TimeUnit unit) throws InterruptedException;

    /**
     * Waits for this future to be completed within the
     * specified time limit.
     *
     * @return {@code true} if and only if the future was completed within
     *         the specified time limit
     *
     * @throws InterruptedException
     *         if the current thread was interrupted
     */
    boolean await(long timeoutMillis) throws InterruptedException;

    /**
     * Waits for this future to be completed within the
     * specified time limit without interruption.  This method catches an
     * {@link InterruptedException} and discards it silently.
     *
     * @return {@code true} if and only if the future was completed within
     *         the specified time limit
     */
    boolean awaitUninterruptibly(long timeout, TimeUnit unit);

    /**
     * Waits for this future to be completed within the
     * specified time limit without interruption.  This method catches an
     * {@link InterruptedException} and discards it silently.
     *
     * @return {@code true} if and only if the future was completed within
     *         the specified time limit
     */
    boolean awaitUninterruptibly(long timeoutMillis);

    /**
     * A {@link ChannelFuture} which is not allowed to be sent to {@link ChannelPipeline} due to
     * implementation details.
     */
    interface Unsafe extends ChannelFuture {
        // Tag interface
    }
}
