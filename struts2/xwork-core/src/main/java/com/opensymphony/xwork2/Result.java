/*
 * Copyright 2002-2007,2009 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.opensymphony.xwork2;

import java.io.Serializable;


/**
 * Action的所有执行结果都被映射到一个View实现
 * 
 * View的例子有:
 * 
 * <ul>
 * <li>SwingPanelView - pops up a new Swing panel</li>
 * <li>ActionChainView - 执行另外一个Action</li>
 * <li>SerlvetRedirectView - 将http response redirect到另外一个url</li>
 * <li>ServletDispatcherView -  将http response dispatch到另外一个url</li>
 * </ul>
 *
 * @author plightbo
 */
// redirect: 通过在HTTP头把一个302的HTTP返回码和新的位置一并发送至浏览器，然后浏览器将自动发出一个指向这个新位置的HTTP请求
// dispatch: 它发出一个内部的对资源的请求，只通过一个请求为浏览器生成最终的视图。 
public interface Result extends Serializable {

    /**
     * 描述了一个action执行结果的通用接口
     * action的执行结果可能是  
     * 
     * 显示网页
     * 生成email
     * 发出JMS
     * 
     * @param invocation  the invocation context.
     * @throws Exception can be thrown.
     */
    public void execute(ActionInvocation invocation) throws Exception;

}
