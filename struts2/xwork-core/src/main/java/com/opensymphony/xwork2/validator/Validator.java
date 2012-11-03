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
package com.opensymphony.xwork2.validator;

import com.opensymphony.xwork2.util.ValueStack;


/**
 * <!-- START SNIPPET: validatorFlavours -->
 * <p>The validators supplied by the XWork distribution (and any validators you
 * might write yourself) come in two different flavors:</p>
 * <p/>
 * <ol>
 * <li> Plain Validators / Non-Field validators </li>
 * <li> FieldValidators </li>
 * </ol>
 * <p/>
 * <p>Plain Validators (such as the ExpressionValidator) perform 
 * validation checks
 * that are not inherently tied to a single specified field. 
 * 
 * When you declare a
 * plain Validator in your -validation.xml file you do not associate a fieldname
 * attribute with it. (You should avoid using plain Validators within the
 * <field-validator> syntax described below.)</p>
 * <p/>
 * 
 * <p>FieldValidators (such as the EmailValidator) are designed to perform
 * validation checks on a single field. They require that you specify a fieldname
 * attribute in your -validation.xml file. There are two different (but equivalent)
 * XML syntaxes you can use to declare FieldValidators (see "<validator> vs.
 * <field-Validator> syntax" below).</p>
 * <p/>
 * <p>There are two places where the differences between the two validator flavors
 * are important to keep in mind:</p>
 * <p/>
 * <ol>
 * <li> when choosing the xml syntax used for declaring a validator
 * (either <validator> or <field-validator>)</li>
 * <li> when using the short-circuit capability</li>
 * </ol>
 * <p/>
 * <p><b>NOTE:</b>Note that you do not declare what "flavor" of validator you are
 * using in your -validation.xml file, you just declare the name of the validator
 * to use and Struts will know whether it's a "plain Validator" or a "FieldValidator"
 * by looking at the validation class that the validator's programmer chose
 * to implement.</p>
 * <!-- END SNIPPET: validatorFlavours -->
 * <p/>
 * <p/>
 * <p/>
 * <p/>
 * <!-- START SNIPPET: validationRules -->
 * <p>
 * 
 * To define validation rules for an Action, create a file named ActionName-validation.xml
 * in the same package as the Action. 
 * 
 * You may also create alias-specific validation rules which
 * add to the default validation rules defined in ActionName-validation.xml 
 * by creating
 * another file in the same directory named ActionName-aliasName-validation.xml. In both
 * cases, ActionName is the name of the Action class, 
 * and aliasName is the name of the
 * Action alias defined in the xwork.xml configuration for the Action.
 * </p>
 * <p/>
 * <p>The framework will also search up the inheritance tree of the Action to
 * find validation rules for directly implemented interfaces and 
 * parent classes of the Action.
 * 
 * This is particularly powerful when combined with ModelDriven Actions 
 * and the VisitorFieldValidator.
 * 
 * Here's an example of how validation rules are discovered. 
 * Given the following class structure:</p>
 * <p/>
 * <ul>
 * <li>interface Animal;</li>
 * <li>interface Quadraped extends Animal;</li>
 * <li>class AnimalImpl implements Animal;</li>
 * <li>class QuadrapedImpl extends AnimalImpl implements Quadraped;</li>
 * <li>class Dog extends QuadrapedImpl;</li>
 * </ul>
 * <p/>
 * <p>The framework method will look for the following config files 
 * if Dog is to be validated:</p>
 * <p/>
 * <ul>
 * <li>Animal</li>
 * <li>Animal-aliasname</li>
 * <li>AnimalImpl</li>
 * <li>AnimalImpl-aliasname</li>
 * <li>Quadraped</li>
 * <li>Quadraped-aliasname</li>
 * <li>QuadrapedImpl</li>
 * <li>QuadrapedImpl-aliasname</li>
 * <li>Dog</li>
 * <li>Dog-aliasname</li>
 * </ul>
 * <p/>
 * <p>While this process is similar to what the 
 * XW:Localization framework does
 * when finding messages, there are some subtle differences. 
 * The most important
 * difference is that validation rules are discovered 
 * from the parent downwards.
 * </p>
 * <p/>
 * <p><b>NOTE:</b>Child's *-validation.xml will 
 * add on to parent's *-validation.xml
 * according to the class hierarchy defined above. 
 * With this feature, one could have
 * more generic validation rule at the parent and more specific validation rule at
 * the child.</p>
 * <p/>
 * <!-- END SNIPPET: validationRules -->
 * <p/>
 * <p/>
 * <!-- START SNIPPET: validatorVsFieldValidators1 -->
 * <p>
 * There are two ways you can define validators in your -validation.xml file:</p>
 * <ol>
 * <li> <validator> </li>
 * <li> <field-validator> </li>
 * </ol>
 * <p>Keep the following in mind when using either syntax:</p>
 * <p/>
 * <p><b>Non-Field-Validator</b>
 * The <validator> element allows you to declare both types of validators
 * (either a plain Validator a field-specific FieldValidator).</p>
 * <!-- END SNIPPET: validatorVsFieldValidators1 -->
 * <p/>
 * <pre>
 * <!-- START SNIPPET: nonFieldValidatorUsingValidatorSyntax -->
 *    <!-- Declaring a plain Validator using the <validator> syntax: -->
 * <p/>
 *    <validator type="expression>
 *          <param name="expression">foo gt bar</param>
 *          <message>foo must be great than bar.</message>
 *    </validator>
 * <!-- END SNIPPET: nonFieldValidatorUsingValidatorSyntax -->
 * </pre>
 * <p/>
 * <pre>
 * <!-- START SNIPPET: fieldValidatorUsingValidatorSyntax -->
 *    <!-- Declaring a field validator using the <validator> syntax; -->
 * <p/>
 *    <validator type="required">
 *         <param name="fieldName">bar</param>
 *         <message>You must enter a value for bar.</message>
 *    </validator>
 * <!-- END SNIPPET: fieldValidatorUsingValidatorSyntax -->
 * </pre>
 * <p/>
 * <p/>
 * <!-- START SNIPPET: validatorVsFieldValidators2 -->
 * <p><b>field-validator</b>
 * 
 * The <field-validator> elements are basically the same as the <validator> elements
 * except that they inherit the fieldName attribute from the enclosing 
 * <field> element.
 * 
 * FieldValidators defined within a <field-validator> element 
 * will have their fieldNameautomatically filled with the value 
 * of the parent <field> element's fieldName attribute. 
 * 
 * The reason for this structure is to conveniently group the validators
 * for a particular field under one element, 
 * otherwise the fieldName attribute
 * would have to be repeated, over and over, 
 * for each individual <validator>.</p>
 * <p/>
 * 
 * <p><b>HINT:</b>
 * It is always better to defined field-validator inside a <field> tag 
 * instead of
 * using a <validator> tag and supplying fieldName as its param 
 * as the xml code itself
 * is clearer (grouping of field is clearer)</p>
 * <p/>
 * <p><b>NOTE:</b>
 * Note that you should only use FieldValidators (not plain Validators) 
 * within a
 * <field-validator> block. 
 * 
 * A plain Validator inside a <field> will not be
 * allowed and would generate error when parsing the xml, 
 * as it is not allowed in
 * the defined dtd (xwork-validator-1.0.2.dtd)</p>
 * <!-- END SNIPPET: validatorVsFieldValidators2 -->
 * <p/>
 * <pre>
 * <!-- START SNIPPET: fieldValidatorUsingFieldValidatorSyntax -->
 * Declaring a FieldValidator using the <field-validator> syntax:
 * <p/>
 * <field name="email_address">
 *   <field-validator type="required">
 *       <message>You cannot leave the email address field empty.</message>
 *   </field-validator>
 *   <field-validator type="email">
 *       <message>The email address you entered is not valid.</message>
 *   </field-validator>
 * </field>
 * <!-- END SNIPPET: fieldValidatorUsingFieldValidatorSyntax -->
 * </pre>
 * <p/>
 * <p/>
 * <!-- START SNIPPET: validatorVsFieldValidators3 -->
 * <p>The choice is yours. 
 * It's perfectly legal to only use <validator> elements
 * without the <field> elements and set the fieldName attribute for each of them.
 * The following are effectively equal:</P>
 * <!-- END SNIPPET: validatorVsFieldValidators3 -->
 * <p/>
 * <pre>
 * <!-- START SNIPPET: similarVaidatorDeclaredInDiffSyntax -->
 * <field name="email_address">
 *   <field-validator type="required">
 *       <message>You cannot leave the email address field empty.</message>
 *   </field-validator>
 *   <field-validator type="email">
 *       <message>The email address you entered is not valid.</message>
 *   </field-validator>
 * </field>
 * <p/>
 * <p/>
 * <validator type="required">
 *   <param name="fieldName">email_address</param>
 *   <message>You cannot leave the email address field empty.</message>
 * </validator>
 * <validator type="email">
 *   <param name="fieldName">email_address</param>
 *   <message>The email address you entered is not valid.</message>
 * </validator>
 * <!-- END SNIPPET: similarVaidatorDeclaredInDiffSyntax -->
 * </pre>
 * <p/>
 * <p/>
 * <!-- START SNIPPET: shortCircuitingValidators1 -->
 * <p>
 * It is possible to short-circuit a stack of validators.
 * 
 * Here is another sample config file containing validation rules from the
 * Xwork test cases: Notice that some of the <field-validator> and
 * <validator> elements have the short-circuit attribute set to true.</p>
 * <!-- END SNIPPET : shortCircuitingValidators1 -->
 * <p/>
 * <pre>
 * <!-- START SNIPPET: exShortCircuitingValidators -->
 * <!DOCTYPE validators PUBLIC
 *         "-//Apache Struts//XWork Validator 1.0.3//EN"
 *  	   "http://struts.apache.org/dtds/xwork-validator-1.0.3.dtd">
 * <validators>
 *   <!-- Field Validators for email field -->
 *   <field name="email">
 *       <field-validator type="required" short-circuit="true">
 *           <message>You must enter a value for email.</message>
 *       </field-validator>
 *       <field-validator type="email" short-circuit="true">
 *           <message>Not a valid e-mail.</message>
 *       </field-validator>
 *   </field>
 *   <!-- Field Validators for email2 field -->
 *   <field name="email2">
 *      <field-validator type="required">
 *           <message>You must enter a value for email2.</message>
 *       </field-validator>
 *      <field-validator type="email">
 *           <message>Not a valid e-mail2.</message>
 *       </field-validator>
 *   </field>
 *   <!-- Plain Validator 1 -->
 *   <validator type="expression">
 *       <param name="expression">email.equals(email2)</param>
 *       <message>Email not the same as email2</message>
 *   </validator>
 *   <!-- Plain Validator 2 -->
 *   <validator type="expression" short-circuit="true">
 *       <param name="expression">email.startsWith('mark')</param>
 *       <message>Email does not start with mark</message>
 *   </validator>
 * </validators>
 * <!-- END SNIPPET: exShortCircuitingValidators -->
 * </pre>
 * <p/>
 * <!-- START SNIPPET:shortCircuitingValidators2  -->
 * <p><b>short-circuiting and Validator flavors</b></p>
 * <p>
 * 
 * Plain validator takes precedence over field-validator. 
 * 
 * They get validated
 * first in the order they are defined and 
 * then the field-validator in the order
 * they are defined. 
 * 
 * Failure of a particular validator marked as short-circuit
 * will prevent the evaluation of subsequent validators and 
 * an error (action
 * error or field error depending on the type of validator) 
 * will be added to
 * the ValidationContext of the object being validated.</p>
 * <p/>
 * <p>In the example above, the actual execution of validator would be as follows:</p>
 * <p/>
 * <ol>
 * <li> Plain Validator 1</li>
 * <li> Plain Validator 2</li>
 * <li> Field Validators for email field</li>
 * <li> Field Validators for email2 field</li>
 * </ol>
 * <p/>
 * <p>Since Plain Validator 2 is short-circuited, if its validation failed,
 * it will causes Field validators for email field and Field validators for email2
 * field to not be validated as well.</p>
 * <p/>
 * 
 * <p><b>Usefull Information:</b>
 * 
 * More complicated validation should probably be done in the validate()
 * method on the action itself (assuming the action implements Validatable
 * interface which ActionSupport already does).</p>
 * <p/>
 * <p>
 * A plain Validator (non FieldValidator) that gets short-circuited will
 * completely break out of the validation stack. 
 * 
 * No other validators will be
 * evaluated and plain validators takes precedence over field validators meaning
 * that they get evaluated in the order they are defined before field validators
 * get a chance to be evaluated.
 * </p>
 * <!-- END SNIPPET: shortCircuitingValidators2 -->
 * <p/>
 * <p/>
 * <!-- START SNIPPET: scAndValidatorFlavours1 -->
 * <p><b>Short cuircuiting and validator flavours</b></p>
 * <p>A FieldValidator that gets short-circuited will only prevent other
 * FieldValidators for the same field from being evaluated. 
 * 
 * Note that this
 * "same field" behavior applies regardless of whether the <validator> or
 * <field-validator> syntax was used to declare the validation rule.
 * By way of example, given this -validation.xml file:</p>
 * <!-- END SNIPPET: scAndValidatorFlavours1 -->
 * <p/>
 * <pre>
 * <!-- START SNIPPET: exScAndValidatorFlavours -->
 * <validator type="required" short-circuit="true">
 *   <param name="fieldName">bar</param>
 *   <message>You must enter a value for bar.</message>
 * </validator>
 * <p/>
 * <validator type="expression">
 *   <param name="expression">foo gt bar</param>
 *   <message>foo must be great than bar.</message>
 * </validator>
 * <!-- END SNIPPET: exScAndValidatorFlavours -->
 * </pre>
 * <p/>
 * <!-- START SNIPPET: scAndValidatorFlavours2 -->
 * <p>both validators will be run, 
 * even if the "required" validator short-circuits.
 * "required" validators are FieldValidator's and 
 * will not short-circuit the plain
 * ExpressionValidator because FieldValidators only short-circuit 
 * other checks on
 * that same field. 
 * 
 * Since the plain Validator is not field specific, it is
 * not short-circuited.</p>
 * <!-- END SNIPPET: scAndValidatorFlavours2 -->
 * <p/>
 * <p/>
 * <!-- START SNIPPET: howXworkFindsValidatorForAction -->
 * <p>As mentioned above, 
 * the framework will also search up the inheritance tree
 * of the action to find default validations for interfaces and parent classes of
 * the Action. 
 * If you are using the short-circuit attribute and relying on
 * default validators higher up in the inheritance tree, make sure you don't
 * accidentally short-circuit things higher in the tree that you really want!</p>
 * <p>
 * The effect of having common validators on both
 * </p>
 * <ul>
 * 	<li><actionClass>-validation.xml</li>
 *     <li><actionClass>-<actionAlias>-validation.xml</li>
 * </ul>
 * <p>
 * It should be noted that the nett effect will be validation 
 * on both the validators available
 * in both validation configuration file. 
 * For example if we have 'requiredstring' validators defined
 * in both validation xml file for field named 'address', 
 * we will see 2 validation error indicating that
 * the the address cannot be empty (assuming validation failed). 
 * This is due to WebWork
 * will merge validators found in both validation configuration files.
 * </p>
 * <p>
 * The logic behind this design decision is such 
 * that we could have common validators in
 * <actionClass>-validation.xml and more context specific validators to be located
 * in <actionClass>-<actionAlias>-validation.xml
 * </p>
 * <!-- END SNIPPET: howXworkFindsValidatorForAction -->
 *
 * <p/>
 * <!-- START SNIPPET: i18n -->
 * Validator's validation messages could be internatinalized. For example,
 * <pre>
 *   <field-validator type="required">
 *      <message key="required.field" />
 *   </field-validator>
 * </pre>
 * or
 * <pre>
 *   <validator type="expression">
 *      <param name="expression">email.startsWith('Mark')</param>
 *      <message key="email.invalid" />
 *   </validator>
 * </pre>
 * In the first case, WebWork would look for i18n with key 'required.field' as the validation error message if
 * validation fails, and 'email.invalid' in the second case.
 * <p/>
 * We could also provide a default message such that if validation failed and the i18n key for the message
 * cannot be found, WebWork would fall back and use the default message. An example would be as follows :-
 * <pre>
 *   <field-validator type="required">
 *      <message key="required.field">This field is required.</message>
 *   </field-validator>
 * </pre>
 * or
 * <pre>
 *   <validator type="expression">
 *      <param name="expression">email.startsWith('Mark')</param>
 *      <message key="email.invalid">Email needs with starts with Mark</message>
 *   </validator>
 * </pre>
 *
 *
 * <!-- END SNIPPET: i18n -->
 * @author Jason Carreira
 */
public interface Validator<T> {

    /**
     * Sets the default message to use for validation failure
     *
     * @param message the default message
     */
    void setDefaultMessage(String message);

    /**
     * Gets the default message used for validation failures
     *
     * @return the default message
     */
    String getDefaultMessage();

    /**
     * Gets the validation failure message for the given object
     *
     * @param object object being validated (eg. a domain model object)
     * @return the validation failure message
     */
    String getMessage(Object object);

    /**
     * Sets a resource bundle key to be used for lookup of validation failure message
     *
     * @param key the resource bundle key
     */
    void setMessageKey(String key);

    /**
     * Gets the resource bundle key used for lookup of validation failure message
     *
     * @return the resource bundle key
     */
    String getMessageKey();

    /**
     * Sets the messsage parameters to be used when parsing i18n messages
     *
     * @param messageParameters  the messsage parameters
     */
    void setMessageParameters(String[] messageParameters);

    /**
     * Gets the messsage parameters to be used when parsing i18n messages
     *
     * @return the messsage parameters
     */
    String[] getMessageParameters();

    /**
     * This method will be called before validate with a non-null ValidatorContext.
     *
     * @param validatorContext the validation context to use.
     */
    void setValidatorContext(ValidatorContext validatorContext);

    /**
     * Gets the validation context used
     *
     * @return the validation context
     */
    ValidatorContext getValidatorContext();

    /**
     * The validation implementation must guarantee that setValidatorContext will
     * be called with a non-null ValidatorContext before validate is called.
     *
     * @param object the object to be validated.
     * @throws ValidationException is thrown if there is validation error(s).
     */
    void validate(Object object) throws ValidationException;

    /**
     * Sets the validator type to use (see class javadoc).
     *
     * @param type the type to use.
     */
    void setValidatorType(String type);

    /**
     * Gets the vaildator type used (see class javadoc).
     *
     * @return the type used
     */
    String getValidatorType();

    /**
     * Sets the value stack to use to resolve values and parameters
     *
     * @param stack The value stack for the request
     * @since 2.1.1
     */
    void setValueStack(ValueStack stack);

}
