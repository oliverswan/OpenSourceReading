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

import java.lang.reflect.Member;
import java.util.*;
import java.util.logging.Logger;

/**
 * 用来创建依赖注入的Container
 * 
 * 依赖type和name唯一地确定一个依赖映射，可以为不同的类型使用相同的名字。
 * 
 * 线程不安全
 *
 * <p>默认添加下面的工厂:
 *
 * <ul>
 *   <li>注入当前Container
 *   <li>将Logger注入到声明类的被注入成员
 * </ul>
 *
 * @author crazybob@google.com (Bob Lee)
 */
public final class ContainerBuilder {

  // 保存所有创建被注入实例的工厂，每一种被注入的类型有一个InternalFactory
  final Map<Key<?>, InternalFactory<?>> factories =
      new HashMap<Key<?>, InternalFactory<?>>();
  
  // 创建单例的工厂
  final List<InternalFactory<?>> singletonFactories =
      new ArrayList<InternalFactory<?>>();
  
  final List<Class<?>> staticInjections = new ArrayList<Class<?>>();
  
  boolean created;
  boolean allowDuplicates = false;
  
  // Container类型的工厂
  private static final InternalFactory<Container> CONTAINER_FACTORY =
      new InternalFactory<Container>() {
        public Container create(InternalContext context) {
          return context.getContainer();
        }
      };

  private static final InternalFactory<Logger> LOGGER_FACTORY =
      new InternalFactory<Logger>() {
        public Logger create(InternalContext context) {
          Member member = context.getExternalContext().getMember();
          return member == null ? Logger.getAnonymousLogger()
              : Logger.getLogger(member.getDeclaringClass().getName());
        }
      };

  /**
   * 构建新的ContainerBuilder，默认添加Container,Log类型对应的InternalFactory
   */
  public ContainerBuilder() {
    // In the current container as the default Container implementation.
    factories.put(Key.newInstance(Container.class, Container.DEFAULT_NAME),
        CONTAINER_FACTORY);

    // Inject the logger for the injected member's declaring class.
    factories.put(Key.newInstance(Logger.class, Container.DEFAULT_NAME),
        LOGGER_FACTORY);
  }

  /**
   * 映射一个依赖，就是将一种Class的类型，和对应的InternalFactory保存到缓存中
   * 
   * 本类所有的方法都漏斗式地通过这里，意思就是最终通过调用这个方法来结束调用
   */
  private <T> ContainerBuilder factory(final Key<T> key,
      InternalFactory<? extends T> factory, Scope scope) {
	  
    ensureNotCreated();
    checkKey(key);
    
    // 创建一个带范围的工厂
    final InternalFactory<? extends T> scopedFactory =
        scope.scopeFactory(key.getType(), key.getName(), factory);
    
    // 保存这个工程
    factories.put(key, scopedFactory);
    if (scope == Scope.SINGLETON) {
    	// 如果是单例的话就保存到单例工厂缓存
      singletonFactories.add(new InternalFactory<T>() {
        public T create(InternalContext context) {
          try {
            context.setExternalContext(ExternalContext.newInstance(
                null, key, context.getContainerImpl()));
            return scopedFactory.create(context);
          } finally {
            context.setExternalContext(null);
          }
        }
      });
    }
    return this;
  }
  
  /**
   * Ensures a key isn't already mapped.
   */
  private void checkKey(Key<?> key) {
    if (factories.containsKey(key) && !allowDuplicates) {
      throw new DependencyException(
          "Dependency mapping for " + key + " already exists.");
    }
  }

  /**
   * Maps a factory to a given dependency type and name.
   *
   * @param type of dependency
   * @param name of dependency
   * @param factory creates objects to inject
   * @param scope scope of injected instances
   * @return this builder
   */
  public <T> ContainerBuilder factory(final Class<T> type, final String name,
      final Factory<? extends T> factory, Scope scope) {
	
	// 新建一个InternalFactory
    InternalFactory<T> internalFactory =  new InternalFactory<T>() {

      public T create(InternalContext context) {
        try {
          
          Context externalContext = context.getExternalContext();
          // 传入externalContext
          return factory.create(externalContext);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }

      @Override
      public String toString() {
        return new LinkedHashMap<String, Object>() {{
          put("type", type);
          put("name", name);
          put("factory", factory);
        }}.toString();
      }
    };

    // 调用内部方法
    return factory(Key.newInstance(type, name), internalFactory, scope);
  }

  /**
   * Convenience method.&nbsp;Equivalent to {@code factory(type,
   * Container.DEFAULT_NAME, factory, scope)}.
   *
   * @see #factory(Class, String, Factory, Scope)
   */
  public <T> ContainerBuilder factory(Class<T> type,
      Factory<? extends T> factory, Scope scope) {
    return factory(type, Container.DEFAULT_NAME, factory, scope);
  }

  /**
   * Convenience method.&nbsp;Equivalent to {@code factory(type, name, factory,
   * Scope.DEFAULT)}.
   *
   * @see #factory(Class, String, Factory, Scope)
   */
  public <T> ContainerBuilder factory(Class<T> type, String name,
      Factory<? extends T> factory) {
    return factory(type, name, factory, Scope.DEFAULT);
  }

  /**
   * Convenience method.&nbsp;Equivalent to {@code factory(type,
   * Container.DEFAULT_NAME, factory, Scope.DEFAULT)}.
   *
   * @see #factory(Class, String, Factory, Scope)
   */
  public <T> ContainerBuilder factory(Class<T> type,
      Factory<? extends T> factory) {
    return factory(type, Container.DEFAULT_NAME, factory, Scope.DEFAULT);
  }

  /**
   * 将一个实现类映射到一个给定的依赖type and name. 
   * 使用container创建实例，递归注入依赖
   *
   * @param type 依赖的类型
   * @param name 依赖的名称
   * @param implementation 依赖的实现类
   * @param scope 被注入的实例的scope
   * @return this 返回本builder
   */
  public <T> ContainerBuilder factory(final Class<T> type, final String name,
      final Class<? extends T> implementation, final Scope scope) {
    // This factory 创建给定实现类的新实例
    // 我们必须懒加载构造函数因为Container还没有被创建
	// 构建一个factory
    InternalFactory<? extends T> factory = new InternalFactory<T>() {
      // InternalFactory的成员变量
      volatile ContainerImpl.ConstructorInjector<? extends T> constructor;

      @SuppressWarnings("unchecked")
      public T create(InternalContext context) {
        if (constructor == null) {
          this.constructor =
        	  // 得到被注入的类的实现类的构造函数
              context.getContainerImpl().getConstructor(implementation);
        }
        // 构造被注入类的实例
        return (T) constructor.construct(context, type);
      }

      @Override
      public String toString() {
        return new LinkedHashMap<String, Object>() {{
          put("type", type);
          put("name", name);
          put("implementation", implementation);
          put("scope", scope);
        }}.toString();
      }
    };
    // 
    return factory(Key.newInstance(type, name), factory, scope);
  }

  /**
   * Maps an implementation class to a given dependency type and name. Creates
   * instances using the container, recursively injecting dependencies.
   *
   * <p>Sets scope to value from {@link Scoped} annotation on the
   * implementation class. Defaults to {@link Scope#DEFAULT} if no annotation
   * is found.
   *
   * @param type of dependency
   * @param name of dependency
   * @param implementation class
   * @return this builder
   */
  public <T> ContainerBuilder factory(final Class<T> type, String name,
      final Class<? extends T> implementation) {
    Scoped scoped = implementation.getAnnotation(Scoped.class);
    Scope scope = scoped == null ? Scope.DEFAULT : scoped.value();
    return factory(type, name, implementation, scope);
  }

  /**
   * Convenience method.&nbsp;Equivalent to {@code factory(type,
   * Container.DEFAULT_NAME, implementation)}.
   *
   * @see #factory(Class, String, Class)
   */
  public <T> ContainerBuilder factory(Class<T> type,
      Class<? extends T> implementation) {
    return factory(type, Container.DEFAULT_NAME, implementation);
  }

  /**
   * Convenience method.&nbsp;Equivalent to {@code factory(type,
   * Container.DEFAULT_NAME, type)}.
   *
   * @see #factory(Class, String, Class)
   */
  public <T> ContainerBuilder factory(Class<T> type) {
    return factory(type, Container.DEFAULT_NAME, type);
  }

  /**
   * Convenience method.&nbsp;Equivalent to {@code factory(type, name, type)}.
   *
   * @see #factory(Class, String, Class)
   */
  public <T> ContainerBuilder factory(Class<T> type, String name) {
    return factory(type, name, type);
  }

  /**
   * Convenience method.&nbsp;Equivalent to {@code factory(type,
   * Container.DEFAULT_NAME, implementation, scope)}.
   *
   * @see #factory(Class, String, Class, Scope)
   */
  public <T> ContainerBuilder factory(Class<T> type,
      Class<? extends T> implementation, Scope scope) {
    return factory(type, Container.DEFAULT_NAME, implementation, scope);
  }

  /**
   * Convenience method.&nbsp;Equivalent to {@code factory(type,
   * Container.DEFAULT_NAME, type, scope)}.
   *
   * @see #factory(Class, String, Class, Scope)
   */
  public <T> ContainerBuilder factory(Class<T> type, Scope scope) {
    return factory(type, Container.DEFAULT_NAME, type, scope);
  }

  /**
   * Convenience method.&nbsp;Equivalent to {@code factory(type, name, type,
   * scope)}.
   *
   * @see #factory(Class, String, Class, Scope)
   */
  public <T> ContainerBuilder factory(Class<T> type, String name, Scope scope) {
    return factory(type, name, type, scope);
  }
  
  /**
   * Convenience method.&nbsp;Equivalent to {@code alias(type, Container.DEFAULT_NAME,
   * type)}.
   *
   * @see #alias(Class, String, String)
   */
  public <T> ContainerBuilder alias(Class<T> type, String alias) {
    return alias(type, Container.DEFAULT_NAME, alias);
  }
  
  /**
   * Maps an existing factory to a new name. 
   * 
   * @param type of dependency
   * @param name of dependency
   * @param alias of to the dependency
   * @return this builder
   */
  public <T> ContainerBuilder alias(Class<T> type, String name, String alias) {
    return alias(Key.newInstance(type, name), Key.newInstance(type, alias));
  }
  
  /**
   * Maps an existing dependency. All methods in this class ultimately funnel through
   * here.
   */
  private <T> ContainerBuilder alias(final Key<T> key,
      final Key<T> aliasKey) {
    ensureNotCreated();
    checkKey(aliasKey);
    
    final InternalFactory<? extends T> scopedFactory = 
      (InternalFactory<? extends T>)factories.get(key);
    if (scopedFactory == null) {
        throw new DependencyException(
                "Dependency mapping for " + key + " doesn't exists.");
    }
    factories.put(aliasKey, scopedFactory);
    return this;
  }

  /**
   * Maps a constant value to the given name.
   */
  public ContainerBuilder constant(String name, String value) {
    return constant(String.class, name, value);
  }

  /**
   * Maps a constant value to the given name.
   */
  public ContainerBuilder constant(String name, int value) {
    return constant(int.class, name, value);
  }

  /**
   * Maps a constant value to the given name.
   */
  public ContainerBuilder constant(String name, long value) {
    return constant(long.class, name, value);
  }

  /**
   * Maps a constant value to the given name.
   */
  public ContainerBuilder constant(String name, boolean value) {
    return constant(boolean.class, name, value);
  }

  /**
   * Maps a constant value to the given name.
   */
  public ContainerBuilder constant(String name, double value) {
    return constant(double.class, name, value);
  }

  /**
   * Maps a constant value to the given name.
   */
  public ContainerBuilder constant(String name, float value) {
    return constant(float.class, name, value);
  }

  /**
   * Maps a constant value to the given name.
   */
  public ContainerBuilder constant(String name, short value) {
    return constant(short.class, name, value);
  }

  /**
   * Maps a constant value to the given name.
   */
  public ContainerBuilder constant(String name, char value) {
    return constant(char.class, name, value);
  }

  /**
   * Maps a class to the given name.
   */
  public ContainerBuilder constant(String name, Class value) {
    return constant(Class.class, name, value);
  }

  /**
   * Maps an enum to the given name.
   */
  public <E extends Enum<E>> ContainerBuilder constant(String name, E value) {
    return constant(value.getDeclaringClass(), name, value);
  }

  /**
   * Maps a constant value to the given type and name.
   */
  private <T> ContainerBuilder constant(final Class<T> type, final String name,
      final T value) {
    InternalFactory<T> factory = new InternalFactory<T>() {
      public T create(InternalContext ignored) {
        return value;
      }

      @Override
      public String toString() {
        return new LinkedHashMap<String, Object>() {
          {
            put("type", type);
            put("name", name);
            put("value", value);
          }
        }.toString();
      }
    };

    return factory(Key.newInstance(type, name), factory, Scope.DEFAULT);
  }

  /**
   * Upon creation, the {@link Container} will inject static fields and methods
   * into the given classes.
   *
   * @param types for which static members will be injected
   */
  public ContainerBuilder injectStatics(Class<?>... types) {
    staticInjections.addAll(Arrays.asList(types));
    return this;
  }

  /**
   * Returns true if this builder contains a mapping for the given type and
   * name.
   */
  public boolean contains(Class<?> type, String name) {
    return factories.containsKey(Key.newInstance(type, name));
  }

  /**
   * Convenience method.&nbsp;Equivalent to {@code contains(type,
   * Container.DEFAULT_NAME)}.
   */
  public boolean contains(Class<?> type) {
    return contains(type, Container.DEFAULT_NAME);
  }

  /**
   * 创建一个Container实例
   * 
   * 为使用injectStatics注册的classes注册静态成员
   *
   * @param loadSingletons If true, the container will load all singletons
   *  now. If false, the container will lazily load singletons. Eager loading
   *  is appropriate for production use while lazy loading can speed
   *  development.
   * @throws IllegalStateException if called more than once
   */
  public Container create(boolean loadSingletons) {
    // 一个builder实例只能创建一个Container实例
	ensureNotCreated();
    created = true;
    
    final ContainerImpl container = new ContainerImpl(
        new HashMap<Key<?>, InternalFactory<?>>(factories));
    if (loadSingletons) {
      // 在Container自己的线程本地Context中调用
      container.callInContext(new ContainerImpl.ContextualCallable<Void>() {
        public Void call(InternalContext context) {
          for (InternalFactory<?> factory : singletonFactories) {
            factory.create(context);
          }
          return null;
        }
      });
    }
    container.injectStatics(staticInjections);
    return container;
  }

  /**
   * Currently we only support creating one Container instance per builder.
   * If we want to support creating more than one container per builder,
   * we should move to a "factory factory" model where we create a factory
   * instance per Container. Right now, one factory instance would be
   * shared across all the containers, singletons synchronize on the
   * container when lazy loading, etc.
   */
  private void ensureNotCreated() {
    if (created) {
      throw new IllegalStateException("Container already created.");
    }
  }
  
  public void setAllowDuplicates(boolean val) {
      allowDuplicates = val;
  }

  /**
   * Implemented by classes which participate in building a container.
   */
  public interface Command {

    /**
     * Contributes factories to the given builder.
     *
     * @param builder
     */
    void build(ContainerBuilder builder);
  }
}
