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
package com.opensymphony.xwork2.interceptor;

import com.opensymphony.xwork2.ActionInvocation;
import com.opensymphony.xwork2.util.TextParseUtil;
import com.opensymphony.xwork2.util.logging.Logger;
import com.opensymphony.xwork2.util.logging.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeMap;

/**
 * <!-- START SNIPPET: description -->
 *
 * 拦截参数到达栈的下面或者Action.
 * 
 * 你可以为一个Action使用多个参数过滤拦截器
 * 
 * 比如,
 * 你可以使用一个参数拦截器，为所有action过滤所有需要block的参数
 * 
 * and those you wanted blocked 
 * from an individual action you could add an additional interceptor
 * for each action.
 * 
 * <!-- END SNIPPET: description -->
 * 
 * <!-- START SNIPPET: parameters -->
 *
 * <ul>
 * <li>allowed - a comma delimited list of parameter prefixes
 *  that are allowed to pass to the action</li>
 * <li>blocked - a comma delimited list of parameter prefixes 
 * that are not allowed to pass to the action</li>
 * <li>defaultBlock - boolean (default to false) whether by
 * default a given parameter is blocked. If true, then a parameter
 * must have a prefix in the allowed list in order to be able 
 * to pass to the action
 * </ul>
 * 
 * <p>The way parameters are filtered for the least configuration is that
 * if a string is in the allowed or blocked lists, then any parameter
 * that is a member of the object represented by the parameter is allowed
 * or blocked respectively.</p>
 * 
 * <p>For example, if the parameters are:
 * <ul>
 * <li>blocked: person,person.address.createDate,personDao</li>
 * <li>allowed: person.address</li>
 * <li>defaultBlock: false</li>
 * </ul>
 * <br>
 * The parameters person.name, person.phoneNum etc would be blocked 
 * because 'person' is in the blocked list. However, person.address.street
 * and person.address.city would be allowed because person.address is
 * in the allowed list (the longer string determines permissions).</p> 
 * <!-- END SNIPPET: parameters -->
 *
 * <!-- START SNIPPET: extending -->
 * There are no known extension points to this interceptor.
 * <!-- END SNIPPET: extending -->
 * 
 * <pre>
 * <!-- START SNIPPET: example -->
 * <interceptors>
 *   ...
 *   <interceptor name="parameterFilter" class="com.opensymphony.xwork2.interceptor.ParameterFilterInterceptor"/>
 *   ...
 * </interceptors>
 * 
 * <action ....>
 *   ...
 *   <interceptor-ref name="parameterFilter">
 *     <param name="blocked">person,person.address.createDate,personDao</param>
 *   </interceptor-ref>
 *   ...
 * </action>
 * <!-- END SNIPPET: example -->
 * </pre>
 * 
 * @author Gabe
 */
public class ParameterFilterInterceptor extends AbstractInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(ParameterFilterInterceptor.class);

    private Collection<String> allowed;
    private Collection<String> blocked;
    private Map<String, Boolean> includesExcludesMap;
    private boolean defaultBlock = false;

    @Override
    public String intercept(ActionInvocation invocation) throws Exception {

        Map<String, Object> parameters = invocation.getInvocationContext().getParameters();
        HashSet<String> paramsToRemove = new HashSet<String>();

        Map<String, Boolean> includesExcludesMap = getIncludesExcludesMap();

        for (String param : parameters.keySet()) {
            boolean currentAllowed = !isDefaultBlock();

            for (String currRule : includesExcludesMap.keySet()) {
                if (param.startsWith(currRule)
                        && (param.length() == currRule.length()
                        || isPropSeperator(param.charAt(currRule.length())))) {
                    currentAllowed = includesExcludesMap.get(currRule).booleanValue();
                }
            }
            if (!currentAllowed) {
                paramsToRemove.add(param);
            }
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Params to remove: " + paramsToRemove);
        }

        for (Object aParamsToRemove : paramsToRemove) {
            parameters.remove(aParamsToRemove);
        }

        return invocation.invoke();
    }

    /**
     * Tests if the given char is a property seperator char <code>.([</code>.
     *
     * @param c the char
     * @return <tt>true</tt>, if char is property separator, <tt>false</tt> otherwise.
     */
    private static boolean isPropSeperator(char c) {
        return c == '.' || c == '(' || c == '[';
    }

    private Map<String, Boolean> getIncludesExcludesMap() {
        if (this.includesExcludesMap == null) {
            this.includesExcludesMap = new TreeMap<String, Boolean>();

            if (getAllowedCollection() != null) {
                for (String e : getAllowedCollection()) {
                    this.includesExcludesMap.put(e, Boolean.TRUE);
                }
            }
            if (getBlockedCollection() != null) {
                for (String b : getBlockedCollection()) {
                    this.includesExcludesMap.put(b, Boolean.FALSE);
                }
            }
        }

        return this.includesExcludesMap;
    }

    /**
     * @return Returns the defaultBlock.
     */
    public boolean isDefaultBlock() {
        return defaultBlock;
    }

    /**
     * @param defaultExclude The defaultExclude to set.
     */
    public void setDefaultBlock(boolean defaultExclude) {
        this.defaultBlock = defaultExclude;
    }

    /**
     * @return Returns the blocked.
     */
    public Collection<String> getBlockedCollection() {
        return blocked;
    }

    /**
     * @param blocked The blocked to set.
     */
    public void setBlockedCollection(Collection<String> blocked) {
        this.blocked = blocked;
    }

    /**
     * @param blocked The blocked paramters as comma separated String.
     */
    public void setBlocked(String blocked) {
        setBlockedCollection(asCollection(blocked));
    }

    /**
     * @return Returns the allowed.
     */
    public Collection<String> getAllowedCollection() {
        return allowed;
    }

    /**
     * @param allowed The allowed to set.
     */
    public void setAllowedCollection(Collection<String> allowed) {
        this.allowed = allowed;
    }

    /**
     * @param allowed The allowed paramters as comma separated String.
     */
    public void setAllowed(String allowed) {
        setAllowedCollection(asCollection(allowed));
    }

    /**
     * Return a collection from the comma delimited String.
     *
     * @param commaDelim the comma delimited String.
     * @return A collection from the comma delimited String. Returns <tt>null</tt> if the string is empty.
     */
    private Collection<String> asCollection(String commaDelim) {
        if (commaDelim == null || commaDelim.trim().length() == 0) {
            return null;
        }
        return TextParseUtil.commaDelimitedStringToSet(commaDelim);
    }

}
