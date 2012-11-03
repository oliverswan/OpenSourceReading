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

import com.opensymphony.xwork2.inject.util.ReferenceCache;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import java.util.Map.Entry;
import java.security.AccessControlException;

/**
 * Default {@link Container} implementation.
 *
 * @author crazybob@google.com (Bob Lee)
 * @see ContainerBuilder
 */
// 为不同类持有Injectors
class ContainerImpl implements Container {

	// 用来为不同类型的成员创建实例的工厂，最后注入的就是这个工厂创建的实例
	final Map<Key<?>, InternalFactory<?>> factories;
	
	// 类型--->names
	final Map<Class<?>, Set<String>> factoryNamesByType;

	ContainerImpl( Map<Key<?>, InternalFactory<?>> factories ) {
		this.factories = factories;
		Map<Class<?>, Set<String>> map = new HashMap<Class<?>, Set<String>>();
		
		// 为factory里的每一个key，创建一个Hashset
		for ( Key<?> key : factories.keySet() ) {
			Set<String> names = map.get(key.getType());
			if (names == null) {
				names = new HashSet<String>();
				map.put(key.getType(), names);
			}
			names.add(key.getName());
		}

		for ( Entry<Class<?>, Set<String>> entry : map.entrySet() ) {
			entry.setValue(Collections.unmodifiableSet(entry.getValue()));
		}

		this.factoryNamesByType = Collections.unmodifiableMap(map);
	}

	@SuppressWarnings("unchecked")
	<T> InternalFactory<? extends T> getFactory( Key<T> key ) {
		return (InternalFactory<T>) factories.get(key);
	}

	/**
	 * 对元素进行reference封装的缓存Map
	 * 一个class类型映射一个Injector列表
	 */
	final Map<Class<?>, List<Injector>> injectors =
			// 一个Map，如果key或者value的引用没了，则删除Entry
			new ReferenceCache<Class<?>, List<Injector>>() {
		
				// 如果值还没有被缓存，则会创建
				@Override
				protected List<Injector> create( Class<?> key ) {
					List<Injector> injectors = new ArrayList<Injector>();
					addInjectors(key, injectors);
					return injectors;
				}
			};

	/**
	 * 递归地为clazz的成员属性和方法添加injector到传入的List<Injector>
	 * 首先注入父类然后再注入子类
	 */
	void addInjectors( Class clazz, List<Injector> injectors ) {
		if (clazz == Object.class) {
			return;
		}

		// 递归调用，先为clazz父类添加
		addInjectors(clazz.getSuperclass(), injectors);

		// TODO (crazybob): Filter out overridden members.
		
		// 将带@Inject的成员变量，创建FieldInjector，加入到injectors
		addInjectorsForFields(clazz.getDeclaredFields(), false, injectors);
		// 将带@Inject的方法，创建MethodInjector，加入到injectors
		addInjectorsForMethods(clazz.getDeclaredMethods(), false, injectors);
	}

	void injectStatics( List<Class<?>> staticInjections ) {
		final List<Injector> injectors = new ArrayList<Injector>();

		for ( Class<?> clazz : staticInjections ) {
			addInjectorsForFields(clazz.getDeclaredFields(), true, injectors);
			addInjectorsForMethods(clazz.getDeclaredMethods(), true, injectors);
		}

		callInContext(new ContextualCallable<Void>() {
			public Void call( InternalContext context ) {
				for ( Injector injector : injectors ) {
					injector.inject(context, null);
				}
				return null;
			}
		});
	}

	void addInjectorsForMethods( Method[] methods, boolean statics,
								 List<Injector> injectors ) {
		addInjectorsForMembers(Arrays.asList(methods), statics, injectors,
				new InjectorFactory<Method>() {
					public Injector create( ContainerImpl container, Method method,
											String name ) throws MissingDependencyException {
						return new MethodInjector(container, method, name);
					}
				});
	}

	// 为成员变量添加Injectors
	void addInjectorsForFields( Field[] fields, boolean statics,
								List<Injector> injectors ) {
		addInjectorsForMembers(Arrays.asList(fields), statics, injectors,
				// 最后一个参数是InjectorFactory
				new InjectorFactory<Field>() {
					public Injector create( ContainerImpl container, Field field,
											String name ) throws MissingDependencyException {
						// 新建一个FieldInjector
						return new FieldInjector(container, field, name);
					}
				});
	}

	<M extends Member & AnnotatedElement> void addInjectorsForMembers(
			List<M> members, boolean statics, List<Injector> injectors,
			InjectorFactory<M> injectorFactory ) {
		// 遍历所有的成员
		for ( M member : members ) {
			// 如果支持静态
			if (isStatic(member) == statics) {
				// 获取该成员的Inject
				Inject inject = member.getAnnotation(Inject.class);
				if (inject != null) {
					try {
						// 使用injectorFactory创建FieldInjector
						injectors.add(injectorFactory.create(this, member, inject.value()));
					} catch ( MissingDependencyException e ) {
						if (inject.required()) {
							throw new DependencyException(e);
						}
					}
				}
			}
		}
	}

	interface InjectorFactory<M extends Member & AnnotatedElement> {

		Injector create( ContainerImpl container, M member, String name )
				throws MissingDependencyException;
	}

	private boolean isStatic( Member member ) {
		return Modifier.isStatic(member.getModifiers());
	}

	// 静态类，与Container关系绑得很紧
	static class FieldInjector implements Injector {

		final Field field;// 对应的field
		final InternalFactory<?> factory;// 内部工厂
		final ExternalContext<?> externalContext;// 外部环境

		public FieldInjector( ContainerImpl container, Field field, String name )
				throws MissingDependencyException {
			this.field = field;
			if (!field.isAccessible()) {
				SecurityManager sm = System.getSecurityManager();
				try {
					if (sm != null) {
						sm.checkPermission(new ReflectPermission("suppressAccessChecks"));
					}
					field.setAccessible(true);
				} catch ( AccessControlException e ) {
					throw new DependencyException("Security manager in use, could not access field: "
							+ field.getDeclaringClass().getName() + "(" + field.getName() + ")", e);
				}
			}

			Key<?> key = Key.newInstance(field.getType(), name);
			// 获取该成员的类型对应的Factory
			factory = container.getFactory(key);
			if (factory == null) {
				throw new MissingDependencyException(
						"No mapping found for dependency " + key + " in " + field + ".");
			}
			// 获取该成员的类型对应的ExternalContext
			this.externalContext = ExternalContext.newInstance(field, key, container);
		}

		public void inject( InternalContext context, Object o ) {
			ExternalContext<?> previous = context.getExternalContext();
			// 设置使用当前的externalContext
			context.setExternalContext(externalContext);
			try {
				// 使用InternalFactory为o对象的field成员设值
				field.set(o, factory.create(context));
			} catch ( IllegalAccessException e ) {
				throw new AssertionError(e);
			} finally {
				context.setExternalContext(previous);
			}
		}
	}

	/**
	 * Gets parameter injectors.
	 *
	 * @param member		 to which the parameters belong
	 * @param annotations	on the parameters
	 * @param parameterTypes parameter types
	 *
	 * @return injections
	 */
	<M extends AccessibleObject & Member> ParameterInjector<?>[]
	getParametersInjectors( M member,
							Annotation[][] annotations, Class[] parameterTypes, String defaultName )
			throws MissingDependencyException {
		List<ParameterInjector<?>> parameterInjectors =
				new ArrayList<ParameterInjector<?>>();

		Iterator<Annotation[]> annotationsIterator =
				Arrays.asList(annotations).iterator();
		for ( Class<?> parameterType : parameterTypes ) {
			Inject annotation = findInject(annotationsIterator.next());
			String name = annotation == null ? defaultName : annotation.value();
			Key<?> key = Key.newInstance(parameterType, name);
			parameterInjectors.add(createParameterInjector(key, member));
		}

		return toArray(parameterInjectors);
	}

	<T> ParameterInjector<T> createParameterInjector(
			Key<T> key, Member member ) throws MissingDependencyException {
		InternalFactory<? extends T> factory = getFactory(key);
		if (factory == null) {
			throw new MissingDependencyException(
					"No mapping found for dependency " + key + " in " + member + ".");
		}

		ExternalContext<T> externalContext =
				ExternalContext.newInstance(member, key, this);
		return new ParameterInjector<T>(externalContext, factory);
	}

	@SuppressWarnings("unchecked")
	private ParameterInjector<?>[] toArray(
			List<ParameterInjector<?>> parameterInjections ) {
		return parameterInjections.toArray(
				new ParameterInjector[parameterInjections.size()]);
	}

	/**
	 * Finds the {@link Inject} annotation in an array of annotations.
	 */
	Inject findInject( Annotation[] annotations ) {
		for ( Annotation annotation : annotations ) {
			if (annotation.annotationType() == Inject.class) {
				return Inject.class.cast(annotation);
			}
		}
		return null;
	}

	static class MethodInjector implements Injector {

		final Method method;
		final ParameterInjector<?>[] parameterInjectors;

		public MethodInjector( ContainerImpl container, Method method, String name )
				throws MissingDependencyException {
			this.method = method;
			if (!method.isAccessible()) {
				SecurityManager sm = System.getSecurityManager();
				try {
					if (sm != null) {
						sm.checkPermission(new ReflectPermission("suppressAccessChecks"));
					}
					method.setAccessible(true);
				} catch ( AccessControlException e ) {
					throw new DependencyException("Security manager in use, could not access method: "
							+ name + "(" + method.getName() + ")", e);
				}
			}

			Class<?>[] parameterTypes = method.getParameterTypes();
			if (parameterTypes.length == 0) {
				throw new DependencyException(
						method + " has no parameters to inject.");
			}
			parameterInjectors = container.getParametersInjectors(
					method, method.getParameterAnnotations(), parameterTypes, name);
		}

		public void inject( InternalContext context, Object o ) {
			try {
				method.invoke(o, getParameters(method, context, parameterInjectors));
			} catch ( Exception e ) {
				throw new RuntimeException(e);
			}
		}
	}

	Map<Class<?>, ConstructorInjector> constructors =
			new ReferenceCache<Class<?>, ConstructorInjector>() {
				@Override
				@SuppressWarnings("unchecked")
				protected ConstructorInjector<?> create( Class<?> implementation ) {
					return new ConstructorInjector(ContainerImpl.this, implementation);
				}
			};

	static class ConstructorInjector<T> {

		final Class<T> implementation;
		final List<Injector> injectors;
		final Constructor<T> constructor;
		final ParameterInjector<?>[] parameterInjectors;

		ConstructorInjector( ContainerImpl container, Class<T> implementation ) {
			this.implementation = implementation;

			constructor = findConstructorIn(implementation);
			if (!constructor.isAccessible()) {
				SecurityManager sm = System.getSecurityManager();
				try {
					if (sm != null) {
						sm.checkPermission(new ReflectPermission("suppressAccessChecks"));
					}
					constructor.setAccessible(true);
				} catch ( AccessControlException e ) {
					throw new DependencyException("Security manager in use, could not access constructor: "
							+ implementation.getName() + "(" + constructor.getName() + ")", e);
				}
			}

			MissingDependencyException exception = null;
			Inject inject = null;
			ParameterInjector<?>[] parameters = null;

			try {
				inject = constructor.getAnnotation(Inject.class);
				parameters = constructParameterInjector(inject, container, constructor);
			} catch ( MissingDependencyException e ) {
				exception = e;
			}
			parameterInjectors = parameters;

			if (exception != null) {
				if (inject != null && inject.required()) {
					throw new DependencyException(exception);
				}
			}
			injectors = container.injectors.get(implementation);
		}

		ParameterInjector<?>[] constructParameterInjector(
				Inject inject, ContainerImpl container, Constructor<T> constructor ) throws MissingDependencyException {
			return constructor.getParameterTypes().length == 0
					? null // default constructor.
					: container.getParametersInjectors(
					constructor,
					constructor.getParameterAnnotations(),
					constructor.getParameterTypes(),
					inject.value()
			);
		}

		@SuppressWarnings("unchecked")
		private Constructor<T> findConstructorIn( Class<T> implementation ) {
			Constructor<T> found = null;
			Constructor<T>[] declaredConstructors = (Constructor<T>[]) implementation
					.getDeclaredConstructors();
			for ( Constructor<T> constructor : declaredConstructors ) {
				if (constructor.getAnnotation(Inject.class) != null) {
					if (found != null) {
						throw new DependencyException("More than one constructor annotated"
								+ " with @Inject found in " + implementation + ".");
					}
					found = constructor;
				}
			}
			if (found != null) {
				return found;
			}

			// If no annotated constructor is found, look for a no-arg constructor
			// instead.
			try {
				return implementation.getDeclaredConstructor();
			} catch ( NoSuchMethodException e ) {
				throw new DependencyException("Could not find a suitable constructor"
						+ " in " + implementation.getName() + ".");
			}
		}

		/**
		 * 创建一个实例，返回Object,而不是期望的T，因为有可能返回一个代理
		 */
		Object construct( InternalContext context, Class<? super T> expectedType ) {
			// 获取ConstructionContext
			ConstructionContext<T> constructionContext = context.getConstructionContext(this);

			// 在构造函数中有一个循环依赖，所以创建一个代理
			// 比如A的构造函数中@Inject B，而B的构造函数中@Inject A
			if (constructionContext.isConstructing()) {
				// TODO (crazybob): if we can't proxy this object, can we proxy the
				// other object?
				return constructionContext.createProxy(expectedType);
			}

			// If we're re-entering this factory while injecting fields or methods,
			// return the same instance. This prevents infinite loops.
			// 避免死循环
			T t = constructionContext.getCurrentReference();
			if (t != null) {
				return t;
			}

			try {
				// First time through...
				constructionContext.startConstruction();
				try {
					Object[] parameters =
							getParameters(constructor, context, parameterInjectors);
					t = constructor.newInstance(parameters);
					// 设置新创建的实例为delegate,后面再创建循环依赖的时候，方法调用都会代理给本实例
					constructionContext.setProxyDelegates(t);
				} finally {
					constructionContext.finishConstruction();
				}

				// Store reference. If an injector re-enters this factory, they'll
				// get the same reference.
				constructionContext.setCurrentReference(t);

				// Inject fields and methods.
				for ( Injector injector : injectors ) {
					injector.inject(context, t);
				}

				return t;
			} catch ( InstantiationException e ) {
				throw new RuntimeException(e);
			} catch ( IllegalAccessException e ) {
				throw new RuntimeException(e);
			} catch ( InvocationTargetException e ) {
				throw new RuntimeException(e);
			} finally {
				constructionContext.removeCurrentReference();
			}
		}
	}

	static class ParameterInjector<T> {

		final ExternalContext<T> externalContext;
		final InternalFactory<? extends T> factory;

		public ParameterInjector( ExternalContext<T> externalContext,
								  InternalFactory<? extends T> factory ) {
			this.externalContext = externalContext;
			this.factory = factory;
		}

		T inject( Member member, InternalContext context ) {
			ExternalContext<?> previous = context.getExternalContext();
			context.setExternalContext(externalContext);
			try {
				return factory.create(context);
			} finally {
				context.setExternalContext(previous);
			}
		}
	}

	private static Object[] getParameters( Member member, InternalContext context,
										   ParameterInjector[] parameterInjectors ) {
		if (parameterInjectors == null) {
			return null;
		}

		Object[] parameters = new Object[parameterInjectors.length];
		for ( int i = 0; i < parameters.length; i++ ) {
			parameters[i] = parameterInjectors[i].inject(member, context);
		}
		return parameters;
	}

	// 会被CallInContext
	void inject( Object o, InternalContext context ) {
		// 获取o对应的所有injector，如果没有就会创建injectors并添加对应类型所有的injector
		List<Injector> injectors = this.injectors.get(o.getClass());
		for ( Injector injector : injectors ) {
			// 依次调用inject
			injector.inject(context, o);
		}
	}

	<T> T inject( Class<T> implementation, InternalContext context ) {
		try {
			ConstructorInjector<T> constructor = getConstructor(implementation);
			return implementation.cast(
					constructor.construct(context, implementation));
		} catch ( Exception e ) {
			throw new RuntimeException(e);
		}
	}

	@SuppressWarnings("unchecked")
	<T> T getInstance( Class<T> type, String name, InternalContext context ) {
		ExternalContext<?> previous = context.getExternalContext();
		// 使用当前的ExternalContext
		Key<T> key = Key.newInstance(type, name);
		context.setExternalContext(ExternalContext.newInstance(null, key, this));
		try {
			InternalFactory o = getFactory(key);
			if (o != null) {
				return getFactory(key).create(context);
			} else {
				return null;
			}
		} finally {
			context.setExternalContext(previous);
		}
	}

	<T> T getInstance( Class<T> type, InternalContext context ) {
		return getInstance(type, DEFAULT_NAME, context);
	}

	public void inject( final Object o ) {
		callInContext(new ContextualCallable<Void>() {
			public Void call( InternalContext context ) {
				inject(o, context);
				return null;
			}
		});
	}

	public <T> T inject( final Class<T> implementation ) {
		return callInContext(new ContextualCallable<T>() {
			public T call( InternalContext context ) {
				return inject(implementation, context);
			}
		});
	}

	public <T> T getInstance( final Class<T> type, final String name ) {
		return callInContext(new ContextualCallable<T>() {
			public T call( InternalContext context ) {
				return getInstance(type, name, context);
			}
		});
	}

	public <T> T getInstance( final Class<T> type ) {
		return callInContext(new ContextualCallable<T>() {
			public T call( InternalContext context ) {
				return getInstance(type, context);
			}
		});
	}

	public Set<String> getInstanceNames( final Class<?> type ) {
        Set<String> names = factoryNamesByType.get(type);
        if (names == null) {
            names = Collections.emptySet();
        }
        return names;
	}

	// 线程本地的context，Object数组
	ThreadLocal<Object[]> localContext =
			new ThreadLocal<Object[]>() {
				@Override
				protected Object[] initialValue() {
					return new Object[1];
				}
			};

	/**
	 * 在当前线程的Context中调用
	 */
	<T> T callInContext( ContextualCallable<T> callable ) {
		Object[] reference = localContext.get();
		
		// 如果线程本地的contexts是空的
		if (reference[0] == null) {
			// new一个
			reference[0] = new InternalContext(this);
			try {
				// 调用
				return callable.call((InternalContext) reference[0]);
			} finally {
				// 如果本次调用的话，则用完删除
				reference[0] = null;
				// WW-3768: ThreadLocal was not removed
				localContext.remove();
			}
		} else {
			// Someone else will clean up this context.
			return callable.call((InternalContext) reference[0]);
		}
	}

	interface ContextualCallable<T> {

		T call( InternalContext context );
	}

	/**
	 * Gets a constructor function for a given implementation class.
	 */
	@SuppressWarnings("unchecked")
	<T> ConstructorInjector<T> getConstructor( Class<T> implementation ) {
		return constructors.get(implementation);
	}

	final ThreadLocal<Object> localScopeStrategy =
			new ThreadLocal<Object>();

	public void setScopeStrategy( Scope.Strategy scopeStrategy ) {
		this.localScopeStrategy.set(scopeStrategy);
	}

	public void removeScopeStrategy() {
		this.localScopeStrategy.remove();
	}

	/**
	 * 在给定的o对象中，注入一个field或method
	 */
	interface Injector extends Serializable {

		void inject( InternalContext context, Object o );
	}

	static class MissingDependencyException extends Exception {

		MissingDependencyException( String message ) {
			super(message);
		}
	}
}
