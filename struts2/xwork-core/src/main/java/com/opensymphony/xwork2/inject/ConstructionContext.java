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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * Context of a dependency construction. 
 * Used to manage circular references.
 * 
 * 用来管理循环依赖
 *
 * @author crazybob@google.com (Bob Lee)
 */
class ConstructionContext<T> {

  T currentReference;
  boolean constructing;

  List<DelegatingInvocationHandler<T>> invocationHandlers;

  T getCurrentReference() {
    return currentReference;
  }

  void removeCurrentReference() {
    this.currentReference = null;
  }

  void setCurrentReference(T currentReference) {
    this.currentReference = currentReference;
  }

  boolean isConstructing() {
    return constructing;
  }

  void startConstruction() {
    this.constructing = true;
  }

  void finishConstruction() {
    this.constructing = false;
    invocationHandlers = null;
  }

  Object createProxy(Class<? super T> expectedType) {
    // TODO: if I create a proxy which implements all the interfaces of
    // the implementation type, I'll be able to get away with one proxy
    // instance (as opposed to one per caller).

    // 如果期待的类型不是接口
    if (!expectedType.isInterface()) {
      throw new DependencyException(
          expectedType.getName() + " is not an interface.");
    }
    
    // 准备调用代理集合
    if (invocationHandlers == null) {
      invocationHandlers = new ArrayList<DelegatingInvocationHandler<T>>();
    }

    // 调用代理处理器
    DelegatingInvocationHandler<T> invocationHandler = new DelegatingInvocationHandler<T>();
    invocationHandlers.add(invocationHandler);
    // 方法调用AOP拦截
    return Proxy.newProxyInstance(
      expectedType.getClassLoader(),
      new Class[] { expectedType },
      invocationHandler
    );
  }

  void setProxyDelegates(T delegate) {
    if (invocationHandlers != null) {
      for (DelegatingInvocationHandler<T> invocationHandler
          : invocationHandlers) {
        invocationHandler.setDelegate(delegate);
      }
    }
  }

  static class DelegatingInvocationHandler<T> implements InvocationHandler {

    T delegate;

    public Object invoke(Object proxy, Method method, Object[] args)
        throws Throwable {
      if (delegate == null) {
        throw new IllegalStateException(
            "Not finished constructing. Please don't call methods on this"
                + " object until the caller's construction is complete.");
      }

      try {
        return method.invoke(delegate, args);
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      } catch (IllegalArgumentException e) {
        throw new RuntimeException(e);
      } catch (InvocationTargetException e) {
        throw e.getTargetException();
      }
    }

    void setDelegate(T delegate) {
      this.delegate = delegate;
    }
  }
}
