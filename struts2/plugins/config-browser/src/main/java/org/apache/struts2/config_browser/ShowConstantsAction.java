/*
 * $Id: ShowConstantsAction.java 1302806 2012-03-20 09:17:02Z lukaszlenart $
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

package org.apache.struts2.config_browser;

import com.opensymphony.xwork2.inject.Container;
import com.opensymphony.xwork2.inject.Inject;

import java.util.HashMap;
import java.util.Map;

/**
 * Shows all constants as loaded by Struts
 */
public class ShowConstantsAction extends ActionNamesAction {

    private Map<String, String> constants;

    @Inject
    public void setContainer(Container container) {
        constants = new HashMap<String, String>();
        for (String key : container.getInstanceNames(String.class)) {
            constants.put(key, container.getInstance(String.class, key));
        }
    }

    public Map<String, String> getConstants() {
        return constants;
    }
}
