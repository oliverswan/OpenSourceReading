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

package com.opensymphony.xwork2.inject;

import java.io.Serializable;
import java.util.Set;

/**
 * 将有@Inject标注的地方，注入进去依赖，包括构造函数, 方法和字段。
 * 
 * <p>When injecting a method or constructor, you can additionally annotate
 * its parameters with {@link Inject} and specify a dependency name. 
 * 
 * 当一个参数没有annotation的话，容器使用方法或者构造函数的Inject标注
 * 
 * <p>For example:
 *
 * <pre>
 *  class Foo {
 *
 *    // 注入叫i的常量
 *    @Inject("i") int i;
 *
 *    // 注入Bar的默认实现，和叫s的字符串常量
 *    @Inject Foo(Bar bar, @Inject("s") String s) {
 *      ...
 *    }
 *
 *    // 注入Baz的默认实现，和叫foo的Bob实现
 *    @Inject void initialize(Baz baz, @Inject("foo") Bob bob) {
 *      ...
 *    }
 *
 *    // 注入Tee的默认实现
 *   @Inject void setTee(Tee tee) {
 *      ...
 *    }
 *  }
 * </pre>
 *
 * <p>创建并注入一个Foo的实例:
 *
 * <pre>
 *  Container c = ...;
 *  // 使用Container c将上面Foo的有@Inject标注的成员，注入值
 *  Foo foo = c.inject(Foo.class);
 * </pre>
 *
 * @see ContainerBuilder
 * @author crazybob@google.com (Bob Lee)
 */
public interface Container extends Serializable {

  /**
   * Default dependency name.
   */
  String DEFAULT_NAME = "default";

  /**
   * 将依赖注入到现有对象的属性或方法
   */
  void inject(Object o);

  /**
   * 创建并注入一个类型的新实例
   * Creates and injects a new instance of type {@code implementation}.
   */
  <T> T inject(Class<T> implementation);

  /**
   * 获取一个在ContainerBuilder中声明的，指定依赖的实例
   */
  <T> T getInstance(Class<T> type, String name);

  /**
   * Convenience method.&nbsp;Equivalent to {@code getInstance(type,
   * DEFAULT_NAME)}.
   */
  <T> T getInstance(Class<T> type);
  
  /**
   * Gets a set of all registered names for the given type
   * @param type The instance type
   * @return A set of registered names or empty set if no instances are registered for that type
   */
  Set<String> getInstanceNames(Class<?> type);

  /**
   * Sets the scope strategy for the current thread.
   */
  void setScopeStrategy(Scope.Strategy scopeStrategy);

  /**
   * Removes the scope strategy for the current thread.
   */
  void removeScopeStrategy();
}
