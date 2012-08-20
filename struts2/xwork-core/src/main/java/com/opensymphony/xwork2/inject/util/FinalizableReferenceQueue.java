/**
 * Copyright (C) 2006 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.opensymphony.xwork2.inject.util;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 当引用对象被回收后，启动一个后台线程进行清理
 * 
 * 被引用的对象，被垃圾回收器按顺序注册到这个队列
 * 
 * @author Bob Lee (crazybob@google.com)
 */
// 在C++缓存管理过程中，程序员通常会自己新建一个队列，将被清除出内存的实例推入队列，等下次被调用时再取出。
// 好在java.lang.ref.ReferenceQueue帮我们实现了这个队列，我们只需将软引用实例在队列中注册即可。
// 因此，如果你需要对象中的一些操作，仅在它的实例被GC回收时才执行，便可以通过ReferenceQueue队列调用它们。
class FinalizableReferenceQueue extends ReferenceQueue<Object> {

  private static final Logger logger =
      Logger.getLogger(FinalizableReferenceQueue.class.getName());

  private FinalizableReferenceQueue() {}

  static ReferenceQueue<Object> instance = createAndStart();
  
  void cleanUp(Reference reference) {
    try {
      // 调用引用的FinalizableReference方法，这个方法在新的后台线程中调用
      // 由具体的子类来实现具体的逻辑
      ((FinalizableReference) reference).finalizeReferent();
    } catch (Throwable t) {
      deliverBadNews(t);
    }
  }

  void deliverBadNews(Throwable t) {
    logger.log(Level.SEVERE, "Error cleaning up after reference.", t);
  }

  void start() {
    Thread thread = new Thread("FinalizableReferenceQueue") {
      @Override
      public void run() {
        while (true) {
          try {
        	// ReferenceQueue中的remove方法将返回队列中下一个新生效的实例引用
        	// 所有SoftReference实例都已经在队列中注册，
        	// 当一个实例被GC清理出内存后，该实例在队列中的引用就会生效）。
        	// 线程随后被阻塞，直到队列中有新生效引用为止。
            cleanUp(remove());
          } catch (InterruptedException e) { /* ignore */ }
        }
      }
    };
    thread.setDaemon(true);
    thread.start();
  }

  static FinalizableReferenceQueue createAndStart() {
    FinalizableReferenceQueue queue = new FinalizableReferenceQueue();
    queue.start();
    return queue;
  }

  /**
   * Gets instance.
   */
  public static ReferenceQueue<Object> getInstance() {
    return instance;
  }
}
