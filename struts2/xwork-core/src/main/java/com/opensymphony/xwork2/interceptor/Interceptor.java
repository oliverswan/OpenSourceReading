/*
 * Copyright 2002-2006,2009 The Apache Software Foundation.
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
package com.opensymphony.xwork2.interceptor;

import com.opensymphony.xwork2.ActionInvocation;

import java.io.Serializable;


/**
 * <!-- START SNIPPET: introduction -->
 * <p/>
 * 
 * 是一个无状态的类，依据拦截器模式，比如Filter,AOP
 * 
 * <p/>
 * Interceptors 是动态拦截Action调用的对象
 * 
 * 可以让开发者定义在execute方法前后执行的代码。
 * 也可以阻止execute方法的执行。
 * 
 * 用来封装一些应用到多个action的代码
 * 
 * <p/>
 * <p/>
 * <p/>
 * Interceptors 必须是无状态的
 * 不能为每次请求和action创建新的实例
 * 
 * Interceptors 可以选择
 * short-circuit ActionInvocation的执行
 * 或者返回一个代码，比如SUCCESS
 * 或者它可以在委托执行invoke的前后执行一些代码
 * 
 * <p/>
 * <!-- END SNIPPET: introduction -->
 * <p/>
 * <p/>
 * <p/>
 * <!-- START SNIPPET: parameterOverriding -->
 * <p/>
 * 拦截器的参数可以通过下面的方式定义:-
 * <p/>
 * <p/>
 * <p/>
 * <b>Method 1:</b>
 * <pre>
 * <action name="myAction" class="myActionClass">
 * 	   <!-- 这里复制了整个default stack -->
 *     <interceptor-ref name="exception"/>
 *     <interceptor-ref name="alias"/>
 *     <interceptor-ref name="params"/>
 *     <interceptor-ref name="servletConfig"/>
 *     <interceptor-ref name="prepare"/>
 *     <interceptor-ref name="i18n"/>
 *     <interceptor-ref name="chain"/>
 *     <interceptor-ref name="modelDriven"/>
 *     <interceptor-ref name="fileUpload"/>
 *     <interceptor-ref name="staticParams"/>
 *     <interceptor-ref name="params"/>
 *     <interceptor-ref name="conversionError"/>
 *     <interceptor-ref name="validation">
 *     <param name="excludeMethods">myValidationExcudeMethod</param>
 *     </interceptor-ref>
 *     <interceptor-ref name="workflow">
 *     <param name="excludeMethods">myWorkflowExcludeMethod</param>
 *     </interceptor-ref>
 * </action>
 * </pre>
 * <p/>
 * <b>Method 2:</b>
 * <pre>
 * <action name="myAction" class="myActionClass">
 * 	<!-- 这里引用了现有的defaultstack -->
 *   <interceptor-ref name="defaultStack">
 *     <param name="validation.excludeMethods">myValidationExcludeMethod</param>
 *     <param name="workflow.excludeMethods">myWorkflowExcludeMethod</param>
 *   </interceptor-ref>
 * </action>
 * </pre>
 * <p/>
 * <p/>
 * In the second method, the 'interceptor-ref' refer to an existing
 * interceptor-stack, namely defaultStack in this example, 
 * and override the validator
 * and workflow interceptor excludeMethods typically in this case. Note that in the
 * 'param' tag, the name attribute contains a dot (.) the word before the dot(.)
 * specifies the interceptor name whose parameter is to be overridden and the word after
 * the dot (.) specifies the parameter itself. Essetially it is as follows :-
 * <p/>
 * <pre>
 *    <interceptor-name>.<parameter-name>
 * </pre>
 * <p/>
 * <b>Note</b> also that in this case the 'interceptor-ref' name attribute
 * is used to indicate an interceptor stack which makes sense as if it is referring
 * to the interceptor itself it would be just using Method 1 describe above.
 * <p/>
 * <!-- END SNIPPET: parameterOverriding -->
 * <p/>
 * <p/>
 * <b>Nested Interceptor param overriding</b>
 * <p/>
 * <!-- START SNIPPET: nestedParameterOverriding -->
 * <p/>
 * Interceptor stack parameter overriding could be nested into as many level as possible, though it would
 * be advisable not to nest it too deep as to avoid confusion, For example,
 * <pre>
 * <interceptor name="interceptor1" class="foo.bar.Interceptor1" />
 * <interceptor name="interceptor2" class="foo.bar.Interceptor2" />
 * <interceptor name="interceptor3" class="foo.bar.Interceptor3" />
 * <interceptor name="interceptor4" class="foo.bar.Interceptor4" />
 * 
 * <interceptor-stack name="stack1">
 *     <interceptor-ref name="interceptor1" />
 * </interceptor-stack>
 * 
 * <interceptor-stack name="stack2">
 *     <interceptor-ref name="intercetor2" />
 *     <interceptor-ref name="stack1" />
 * </interceptor-stack>
 * <interceptor-stack name="stack3">
 *     <interceptor-ref name="interceptor3" />
 *     <interceptor-ref name="stack2" />
 * </interceptor-stack>
 * <interceptor-stack name="stack4">
 *     <interceptor-ref name="interceptor4" />
 *     <interceptor-ref name="stack3" />
 *  </interceptor-stack>
 * </pre>
 * Assuming the interceptor has the following properties
 * <table border="1" width="100%">
 * <tr>
 * <td>Interceptor</td>
 * <td>property</td>
 * </tr>
 * <tr>
 * <td>Interceptor1</td>
 * <td>param1</td>
 * </tr>
 * <tr>
 * <td>Interceptor2</td>
 * <td>param2</td>
 * </tr>
 * <tr>
 * <td>Interceptor3</td>
 * <td>param3</td>
 * </tr>
 * <tr>
 * <td>Interceptor4</td>
 * <td>param4</td>
 * </tr>
 * </table>
 * We could override them as follows :-
 * <pre>
 *    <action ... >
 *        <!-- to override parameters of interceptor located directly in the stack  -->
 *        <interceptor-ref name="stack4">
 *           <param name="interceptor4.param4"> ... </param>
 *        </interceptor-ref>
 *    </action>
 * <p/>
 *    <action ... >
 *        <!-- to override parameters of interceptor located under nested stack -->
 *        <interceptor-ref name="stack4">
 *            <param name="stack3.interceptor3.param3"> ... </param>
 *            <param name="stack3.stack2.interceptor2.param2"> ... </param>
 *            <param name="stack3.stack2.stack1.interceptor1.param1"> ... </param>
 *        </interceptor-ref>
 *    </action>
 *  </pre>
 * <p/>
 * <!-- END SNIPPET: nestedParameterOverriding -->
 *
 * @author Jason Carreira
 * @author tmjee
 * @version $Date: 2009-12-28 02:18:29 +0800 (Mon, 28 Dec 2009) $ $Id: Interceptor.java 894090 2009-12-27 18:18:29Z martinc $
 */
public interface Interceptor extends Serializable {

    /**
     * Called to let an interceptor clean up any resources it has allocated.
     */
    void destroy();

    /**
     * Called after an interceptor is created, but before any requests are processed using
     * {@link #intercept(com.opensymphony.xwork2.ActionInvocation) intercept} , giving
     * the Interceptor a chance to initialize any needed resources.
     */
    void init();

    /**
     * 在这个方法里，对传入的ActionInvocation进行拦截。
     * 
     * Allows the Interceptor to do some processing on 
     * the request before and/or after the rest of 
     * the processing of the
     * request by the {@link ActionInvocation} or 
     * to short-circuit the processing and just 
     * return a String return code.
     *
     * @param invocation the action invocation
     * @return the return code, either returned from {@link ActionInvocation#invoke()}, or from the interceptor itself.
     * @throws Exception any system-level error, as defined in {@link com.opensymphony.xwork2.Action#execute()}.
     */
    String intercept(ActionInvocation invocation) throws Exception;

}
