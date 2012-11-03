/*
 * $Id: StrutsResultSupport.java 1099157 2011-05-03 17:53:55Z jogep $
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.struts2.dispatcher;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import org.apache.struts2.StrutsStatics;

import com.opensymphony.xwork2.ActionInvocation;
import com.opensymphony.xwork2.Result;
import com.opensymphony.xwork2.util.TextParseUtil;
import com.opensymphony.xwork2.util.logging.Logger;
import com.opensymphony.xwork2.util.logging.LoggerFactory;


/**
 * <!-- START SNIPPET: javadoc -->
 *
 * 所有Struts Action执行结果的基类.
 * 
 * The "location" param is the default parameter, meaning the 
 * most common usage of this result would be:
 * <p/>
 * 为任何子类提供两个通用参数:
 * <ul>
 * <li>location - 执行完要去的地方一般是jsp或者action (could be a jsp page or another action).
 * It can be parsed as per the rules definied in the
 * {@link TextParseUtil#translateVariables(java.lang.String, com.opensymphony.xwork2.util.ValueStack) translateVariables}
 * method</li>
 * <li>parse - 默认为true，如果是false那么location参数的值，不会被作为表达式，进行解析</li>
 * <li>encode - 默认为flase，location参数的值不会被url encoded. 
 * This only have effect when parse is true</li>
 * </ul>
 *
 * <b>NOTE:</b>
 * The encode 参数必须是当parse为true的时候，才有效
 *
 * <!-- END SNIPPET: javadoc -->
 *
 * <p/>
 *
 * <!-- START SNIPPET: example -->
 *
 * <p/>
 * In the struts.xml configuration file, these would be included as:
 * <p/>
 * <pre>
 *  <result name="success" type="redirect">
 *      <param name="location">foo.jsp</param>
 *  </result></pre>
 * <p/>
 * or
 * <p/>
 * <pre>
 *  <result name="success" type="redirect" >
 *      <param name="<b>location</b>">foo.jsp?url=${myUrl}</param>
 *      <param name="<b>parse</b>">true</param>
 *      <param name="<b>encode</b>">true</param>
 *  </result></pre>
 * <p/>
 * In the above case, myUrl will be parsed against Ognl Value Stack and then
 * URL encoded.
 * <p/>
 * or when using the default parameter feature
 * <p/>
 * <pre>
 *  <result name="success" type="redirect"><b>foo.jsp</b></result></pre>
 * <p/>
 * You should subclass this class if you're interested in adding more parameters or functionality
 * to your Result. If you do subclass this class you will need to
 * override {@link #doExecute(String, ActionInvocation)}.<p>
 * <p/>
 * Any custom result can be defined in struts.xml as:
 * <p/>
 * <pre>
 *  <result-types>
 *      ...
 *      <result-type name="myresult" class="com.foo.MyResult" />
 *  </result-types></pre>
 * <p/>
 * Please see the {@link com.opensymphony.xwork2.Result} class for more info on Results in general.
 *
 * <!-- END SNIPPET: example -->
 *
 * @see com.opensymphony.xwork2.Result
 */
public abstract class StrutsResultSupport implements Result, StrutsStatics {

    private static final Logger LOG = LoggerFactory.getLogger(StrutsResultSupport.class);

    /** The default parameter */
    public static final String DEFAULT_PARAM = "location";

    private boolean parse;
    private boolean encode;
    private String location;
    private String lastFinalLocation;

    public StrutsResultSupport() {
        this(null, true, false);
    }

    public StrutsResultSupport(String location) {
        this(location, true, false);
    }

    public StrutsResultSupport(String location, boolean parse, boolean encode) {
        this.location = location;
        this.parse = parse;
        this.encode = encode;
    }

    /**
     * The location to go to after action execution. This could be a JSP page or another action.
     * The location can contain OGNL expressions which will be evaulated if the <tt>parse</tt>
     * parameter is set to <tt>true</tt>.
     *
     * @param location the location to go to after action execution.
     * @see #setParse(boolean)
     */
    public void setLocation(String location) {
        this.location = location;
    }
    
    /**
     * Gets the location it was created with, mainly for testing
     */
    public String getLocation() {
        return location;
    }

    /**
     * Returns the last parsed and encoded location value
     */
    public String getLastFinalLocation() {
        return lastFinalLocation;
    }

    /**
     * Set parse to <tt>true</tt> to indicate that the location should be parsed as an OGNL expression. This
     * is set to <tt>true</tt> by default.
     *
     * @param parse <tt>true</tt> if the location parameter is an OGNL expression, <tt>false</tt> otherwise.
     */
    public void setParse(boolean parse) {
        this.parse = parse;
    }

    /**
     * Set encode to <tt>true</tt> to indicate that the location should be url encoded. This is set to
     * <tt>true</tt> by default
     *
     * @param encode <tt>true</tt> if the location parameter should be url encode, <tt>false</tt> otherwise.
     */
    public void setEncode(boolean encode) {
        this.encode = encode;
    }

    /**
     * Implementation of the <tt>execute</tt> method from the <tt>Result</tt> interface. This will call
     * the abstract method {@link #doExecute(String, ActionInvocation)} after optionally evaluating the
     * location as an OGNL evaluation.
     *
     * @param invocation the execution state of the action.
     * @throws Exception if an error occurs while executing the result.
     */
    public void execute(ActionInvocation invocation) throws Exception {
        lastFinalLocation = conditionalParse(location, invocation);
        doExecute(lastFinalLocation, invocation);
    }

    /**
     * Parses the parameter for OGNL expressions against the valuestack
     *
     * @param param The parameter value
     * @param invocation The action invocation instance
     * @return The resulting string
     */
    protected String conditionalParse(String param, ActionInvocation invocation) {
        if (parse && param != null && invocation != null) {
            return TextParseUtil.translateVariables(param, invocation.getStack(),
                    new TextParseUtil.ParsedValueEvaluator() {
                        public Object evaluate(Object parsedValue) {
                            if (encode) {
                                if (parsedValue != null) {
                                    try {
                                        // use UTF-8 as this is the recommended encoding by W3C to
                                        // avoid incompatibilities.
                                        return URLEncoder.encode(parsedValue.toString(), "UTF-8");
                                    }
                                    catch(UnsupportedEncodingException e) {
                                        if (LOG.isWarnEnabled()) {
                                            LOG.warn("error while trying to encode ["+parsedValue+"]", e);
                                        }
                                    }
                                }
                            }
                            return parsedValue;
                        }
            });
        } else {
            return param;
        }
    }

    /**
     * Executes the result given a final location (jsp page, action, etc) and the action invocation
     * (the state in which the action was executed). Subclasses must implement this class to handle
     * custom logic for result handling.
     *
     * @param finalLocation the location (jsp page, action, etc) to go to.
     * @param invocation    the execution state of the action.
     * @throws Exception if an error occurs while executing the result.
     */
    protected abstract void doExecute(String finalLocation, ActionInvocation invocation) throws Exception;
}
