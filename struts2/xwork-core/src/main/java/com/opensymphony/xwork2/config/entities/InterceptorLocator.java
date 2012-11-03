package com.opensymphony.xwork2.config.entities;

/**
 * 用来获取拦截器配置
 */
public interface InterceptorLocator {

    /**
     * Gets an interceptor configuration object.
     * @param name The interceptor or interceptor stack name
     * @return Either an {@link InterceptorConfig} or {@link InterceptorStackConfig} object
     */
    Object getInterceptorConfig(String name);
}
