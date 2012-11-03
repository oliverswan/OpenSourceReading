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
package com.opensymphony.xwork2;

import com.opensymphony.xwork2.util.ValueStack;

import java.util.List;
import java.util.ResourceBundle;


/**
 * 提供对ResourceBundle的存取，和根本的文本信息
 * 实现类可以委托 TextProviderSupport
 * 
 * Messages会在多个resource bundles中查询, 从与这个类绑定的那个RB开始。
 * 一般是Action类，绑定的TextProvider？然后尝试与父类绑定的RB
 * 
 * 如果找到的话就会停止查找. This gives a cascading style that allow
 * global texts to be defined for an application base class.
 * <p/>
 * 
 * 可以重写LocaleProvider#getLocale()来改变如何为返回的bundles选择locale
 * 
 * 通常你使用LocaleProvider来获取用户配置的Locale
 * <p/>
 * 
 * 如果要使用自己的实现
 * 需要在struts.xml中定义下面的bean和常量:
 * <bean 
 * 	class="org.demo.MyTextProvider" 
 * 	name="myTextProvider" 
 * 	type="com.opensymphony.xwork2.TextProvider" />
 * <constant name="struts.xworkTextProvider" value="myTextProvider" />
 * <p/>
 * 如果需要实现自己的框架信息，需要配置以下常量
 * <constant name="system" value="myTextProvider" />
 * <p/>
 * Take a look on {@link com.opensymphony.xwork2.ActionSupport} for example TextProvider implemntation.
 *
 * @author Jason Carreira
 * @author Rainer Hermanns
 * @see LocaleProvider
 * @see TextProviderSupport
 */
public interface TextProvider {

    /**
     * Checks if a message key exists.
     *
     * @param key message key to check for
     * @return boolean true if key exists, false otherwise.
     */
    boolean hasKey(String key);

    /**
     * Gets a message based on a message key, or null if no message is found.
     *
     * @param key the resource bundle key that is to be searched for
     * @return the message as found in the resource bundle, or null if none is found.
     */
    String getText(String key);

    /**
     * Gets a message based on a key, or, if the message is not found, a supplied
     * default value is returned.
     *
     * @param key          the resource bundle key that is to be searched for
     * @param defaultValue the default value which will be returned if no message is found
     * @return the message as found in the resource bundle, or defaultValue if none is found
     */
    String getText(String key, String defaultValue);

    /**
     * Gets a message based on a key using the supplied obj, as defined in
     * {@link java.text.MessageFormat}, or, if the message is not found, a supplied
     * default value is returned.
     *
     * @param key          the resource bundle key that is to be searched for
     * @param defaultValue the default value which will be returned if no message is found
     * @param obj          obj to be used in a {@link java.text.MessageFormat} message
     * @return the message as found in the resource bundle, or defaultValue if none is found
     */
    String getText(String key, String defaultValue, String obj);

    /**
     * Gets a message based on a key using the supplied args, as defined in
     * {@link java.text.MessageFormat}, or null if no message is found.
     *
     * @param key  the resource bundle key that is to be searched for
     * @param args a list args to be used in a {@link java.text.MessageFormat} message
     * @return the message as found in the resource bundle, or null if none is found.
     */
    String getText(String key, List<?> args);

    /**
     * Gets a message based on a key using the supplied args, as defined in
     * {@link java.text.MessageFormat}, or null if no message is found.
     *
     * @param key  the resource bundle key that is to be searched for
     * @param args an array args to be used in a {@link java.text.MessageFormat} message
     * @return the message as found in the resource bundle, or null if none is found.
     */
    String getText(String key, String[] args);

    /**
     * Gets a message based on a key using the supplied args, as defined in
     * {@link java.text.MessageFormat}, or, if the message is not found, a supplied
     * default value is returned.
     *
     * @param key          the resource bundle key that is to be searched for
     * @param defaultValue the default value which will be returned if no message is found
     * @param args         a list args to be used in a {@link java.text.MessageFormat} message
     * @return the message as found in the resource bundle, or defaultValue if none is found
     */
    String getText(String key, String defaultValue, List<?> args);

    /**
     * Gets a message based on a key using the supplied args, as defined in
     * {@link java.text.MessageFormat}, or, if the message is not found, a supplied
     * default value is returned.
     *
     * @param key          the resource bundle key that is to be searched for
     * @param defaultValue the default value which will be returned if no message is found
     * @param args         an array args to be used in a {@link java.text.MessageFormat} message
     * @return the message as found in the resource bundle, or defaultValue if none is found
     */
    String getText(String key, String defaultValue, String[] args);

    /**
     * Gets a message based on a key using the supplied args, as defined in
     * {@link java.text.MessageFormat}, or, if the message is not found, a supplied
     * default value is returned. Instead of using the value stack in the ActionContext
     * this version of the getText() method uses the provided value stack.
     *
     * @param key          the resource bundle key that is to be searched for
     * @param defaultValue the default value which will be returned if no message is found
     * @param args         a list args to be used in a {@link java.text.MessageFormat} message
     * @param stack        the value stack to use for finding the text
     * @return the message as found in the resource bundle, or defaultValue if none is found
     */
    String getText(String key, String defaultValue, List<?> args, ValueStack stack);

    /**
     * Gets a message based on a key using the supplied args, as defined in
     * {@link java.text.MessageFormat}, or, if the message is not found, a supplied
     * default value is returned. Instead of using the value stack in the ActionContext
     * this version of the getText() method uses the provided value stack.
     *
     * @param key          the resource bundle key that is to be searched for
     * @param defaultValue the default value which will be returned if no message is found
     * @param args         an array args to be used in a {@link java.text.MessageFormat} message
     * @param stack        the value stack to use for finding the text
     * @return the message as found in the resource bundle, or defaultValue if none is found
     */
    String getText(String key, String defaultValue, String[] args, ValueStack stack);

    /**
     * Get the named bundle, such as "com/acme/Foo".
     *
     * @param bundleName the name of the resource bundle, such as <code>"com/acme/Foo"</code>.
     * @return the bundle
     */
    ResourceBundle getTexts(String bundleName);

    /**
     * Get the resource bundle associated with the implementing class (usually an action).
     *
     * @return the bundle
     */
    ResourceBundle getTexts();
}
