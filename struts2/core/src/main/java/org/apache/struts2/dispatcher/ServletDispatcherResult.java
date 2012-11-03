/*
 * $Id: ServletDispatcherResult.java 1324674 2012-04-11 09:42:13Z mcucchiara $
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

import com.opensymphony.xwork2.ActionInvocation;
import com.opensymphony.xwork2.inject.Inject;
import com.opensymphony.xwork2.util.logging.Logger;
import com.opensymphony.xwork2.util.logging.LoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.StrutsStatics;
import org.apache.struts2.views.util.UrlHelper;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.PageContext;
import java.util.Map;


/**
 * <!-- START SNIPPET: description -->
 *
 * Includes 或 forwards 到view，通常是一个jsp. 
 * 
 * 底层会使用RequestDispatcher(jsp本质上也是servlet), 目标servlet
 * 会收到与当前servlet相同的request,response对象
 * 
 * 因此可以使用request.setAttribute() - the Struts action is
 * available.来传递参数
 * 
 * <p/>
 * 
 * 本result转向目标的方式有3种:
 *
 * <ul>
 *
 * <li>如果在jsp的scope中(a PageContext is available), 
 * PageContext.include方法会被调用
 *
 * <li>如果没有 PageContext并且我们不是在任何类型的include 中(there is no
 * "javax.servlet.include.servlet_path" in the request attributes), 
 * 那么会调用RequestDispatcher.forward</li>
 *
 * <li>RequestDispatcher#include.</li>
 *
 * </ul>
 * <!-- END SNIPPET: description -->
 *
 * <b>This result type takes the following parameters:</b>
 *
 * <!-- START SNIPPET: params -->
 *
 * <ul>
 *
 * <li><b>location (default)</b> - the location to go to after execution (ex. jsp).</li>
 *
 * <li><b>parse</b> - true by default. If set to false, the location param will not be parsed for Ognl expressions.</li>
 *
 * </ul>
 *
 * <!-- END SNIPPET: params -->
 *
 * <b>Example:</b>
 *
 * <pre><!-- START SNIPPET: example -->
 * <result name="success" type="dispatcher">
 *   <param name="location">foo.jsp</param>
 * </result>
 * <!-- END SNIPPET: example --></pre>
 *
 * This result follows the same rules from {@link StrutsResultSupport}.
 *
 * @see javax.servlet.RequestDispatcher
 */
public class ServletDispatcherResult extends StrutsResultSupport {

    private static final long serialVersionUID = -1970659272360685627L;

    private static final Logger LOG = LoggerFactory.getLogger(ServletDispatcherResult.class);

    private UrlHelper urlHelper;

    public ServletDispatcherResult() {
        super();
    }

    public ServletDispatcherResult(String location) {
        super(location);
    }

    @Inject
    public void setUrlHelper(UrlHelper urlHelper) {
        this.urlHelper = urlHelper;
    }

    /**
     * Dispatches to the given location. Does its forward via a RequestDispatcher. If the
     * dispatch fails a 404 error will be sent back in the http response.
     *
     * @param finalLocation the location to dispatch to.
     * @param invocation    the execution state of the action
     * @throws Exception if an error occurs. If the dispatch fails the error will go back via the
     *                   HTTP request.
     */
    public void doExecute(String finalLocation, ActionInvocation invocation) throws Exception {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Forwarding to location " + finalLocation);
        }

        PageContext pageContext = ServletActionContext.getPageContext();

        if (pageContext != null) {
            pageContext.include(finalLocation);
        } else {
            HttpServletRequest request = ServletActionContext.getRequest();
            HttpServletResponse response = ServletActionContext.getResponse();
            RequestDispatcher dispatcher = request.getRequestDispatcher(finalLocation);

            //add parameters passed on the location to #parameters
            // see WW-2120
            if (StringUtils.isNotEmpty(finalLocation) && finalLocation.indexOf("?") > 0) {
                String queryString = finalLocation.substring(finalLocation.indexOf("?") + 1);
                Map<String, Object> parameters = getParameters(invocation);
                Map<String, Object> queryParams = urlHelper.parseQueryString(queryString, true);
                if (queryParams != null && !queryParams.isEmpty())
                    parameters.putAll(queryParams);
            }

            // if the view doesn't exist, let's do a 404
            if (dispatcher == null) {
                response.sendError(404, "result '" + finalLocation + "' not found");
                return;
            }

            //if we are inside an action tag, we always need to do an include
            Boolean insideActionTag = (Boolean) ObjectUtils.defaultIfNull(request.getAttribute(StrutsStatics.STRUTS_ACTION_TAG_INVOCATION), Boolean.FALSE);

            // If we're included, then include the view
            // Otherwise do forward
            // This allow the page to, for example, set content type
            if (!insideActionTag && !response.isCommitted() && (request.getAttribute("javax.servlet.include.servlet_path") == null)) {
                request.setAttribute("struts.view_uri", finalLocation);
                request.setAttribute("struts.request_uri", request.getRequestURI());

                dispatcher.forward(request, response);
            } else {
                dispatcher.include(request, response);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getParameters(ActionInvocation invocation) {
        return (Map<String, Object>) invocation.getInvocationContext().getContextMap().get("parameters");
    }

}
