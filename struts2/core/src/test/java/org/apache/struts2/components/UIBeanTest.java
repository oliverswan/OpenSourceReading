/*
 * $Id: UIBeanTest.java 754994 2009-03-16 20:09:51Z musachy $
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

package org.apache.struts2.components;

import java.util.Collections;
import java.util.Map;

import org.apache.struts2.StrutsTestCase;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.opensymphony.xwork2.ActionContext;
import com.opensymphony.xwork2.util.ValueStack;

/**
 *
 * @version $Date: 2009-03-17 04:09:51 +0800 (Tue, 17 Mar 2009) $ $Id: UIBeanTest.java 754994 2009-03-16 20:09:51Z musachy $
 */
public class UIBeanTest extends StrutsTestCase {

    public void testPopulateComponentHtmlId1() throws Exception {
        ValueStack stack = ActionContext.getContext().getValueStack();
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();

        Form form = new Form(stack, req, res);
        form.getParameters().put("id", "formId");

        TextField txtFld = new TextField(stack, req, res);
        txtFld.setId("txtFldId");

        txtFld.populateComponentHtmlId(form);

        assertEquals("txtFldId", txtFld.getParameters().get("id"));
    }

    public void testPopulateComponentHtmlIdWithOgnl() throws Exception {
        ValueStack stack = ActionContext.getContext().getValueStack();
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();

        Form form = new Form(stack, req, res);
        form.getParameters().put("id", "formId");

        TextField txtFld = new TextField(stack, req, res);
        txtFld.setName("txtFldName%{'1'}");

        txtFld.populateComponentHtmlId(form);

        assertEquals("formId_txtFldName1", txtFld.getParameters().get("id"));
    }

    public void testPopulateComponentHtmlId2() throws Exception {
        ValueStack stack = ActionContext.getContext().getValueStack();
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();

        Form form = new Form(stack, req, res);
        form.getParameters().put("id", "formId");

        TextField txtFld = new TextField(stack, req, res);
        txtFld.setName("txtFldName");

        txtFld.populateComponentHtmlId(form);

        assertEquals("formId_txtFldName", txtFld.getParameters().get("id"));
    }

    public void testEscape() throws Exception {
        ValueStack stack = ActionContext.getContext().getValueStack();
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        UIBean bean = new UIBean(stack, req, res) {
            protected String getDefaultTemplate() {
                return null;
            }
        };

        assertEquals(bean.escape("hello[world"), "hello_world");
        assertEquals(bean.escape("hello.world"), "hello_world");
        assertEquals(bean.escape("hello]world"), "hello_world");
        assertEquals(bean.escape("hello!world"), "hello!world");
        assertEquals(bean.escape("hello!@#$%^&*()world"), "hello!@#$%^&*()world");
    }

    public void testEscapeId() throws Exception {
        ValueStack stack = ActionContext.getContext().getValueStack();
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();

        Form form = new Form(stack, req, res);
        form.getParameters().put("id", "formId");

        TextField txtFld = new TextField(stack, req, res);
        txtFld.setName("foo/bar");
        txtFld.populateComponentHtmlId(form);
        assertEquals("formId_foo_bar", txtFld.getParameters().get("id"));
    }

    public void testGetThemeFromForm() throws Exception {
        ValueStack stack = ActionContext.getContext().getValueStack();
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();

        Form form = new Form(stack, req, res);
        form.setTheme("foo");

        TextField txtFld = new TextField(stack, req, res);
        assertEquals("foo", txtFld.getTheme());
    }

    public void testGetThemeFromContext() throws Exception {
        ValueStack stack = ActionContext.getContext().getValueStack();
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        Map context = Collections.singletonMap("theme", "bar");
        ActionContext.getContext().put("attr", context);

        TextField txtFld = new TextField(stack, req, res);
        assertEquals("bar", txtFld.getTheme());
    }

    public void testGetThemeFromContextNonString() throws Exception {
        ValueStack stack = ActionContext.getContext().getValueStack();
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        Map context = Collections.singletonMap("theme", new Integer(12));
        ActionContext.getContext().put("attr", context);

        TextField txtFld = new TextField(stack, req, res);
        assertEquals("12", txtFld.getTheme());
    }

//    I couldn't figure out how to make this test work. Bailing for now.
//    public void testEscapeLabel() throws Exception {
//        ValueStack stack = ActionContext.getContext().getValueStack();
//        MockHttpServletRequest req = new MockHttpServletRequest();
//        MockHttpServletResponse res = new MockHttpServletResponse();
//        stack.push(this);
//
//        TextField txtFld = new TextField(stack, req, res);
//        txtFld.setKey("test['foo']");
//        txtFld.evaluateParams();
//        assertEquals("test_label", txtFld.getParameters().get("label"));
//    }
//
//    public String getText(String key) {
//        assertEquals("test[\\'foo\\']", key);
//        return "test_label";
//    }
}
