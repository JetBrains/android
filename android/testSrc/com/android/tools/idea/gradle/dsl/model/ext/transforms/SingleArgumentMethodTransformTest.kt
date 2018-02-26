/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.ext.transforms

import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo
import com.android.tools.idea.gradle.dsl.parser.elements.*
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.sameInstance
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class SingleArgumentMethodTransformTest : TransformTestCase() {
  private val methodName = "method"
  private val transform = SingleArgumentMethodTransform(methodName)

  @Test
  fun testConditionOnNull() {
    assertTrue(transform.test(null))
  }

  @Test
  fun testConditionOnNoneMethodCall() {
    val inputElement = createLiteral()
    assertFalse(transform.test(inputElement))
  }

  @Test
  fun testConditionOnWrongMethodCall() {
    val inputElement = createMethodCall("wrongMethod")
    assertFalse(transform.test(inputElement))
  }

  @Test
  fun testConditionOnCorrectMethodCallNoArguments() {
    val inputElement = createMethodCall(methodName)
    assertFalse(transform.test(inputElement))
  }

  @Test
  fun testConditionOnWrongMethodCallOneArgument() {
    val inputElement = createMethodCall("wrongMethod")
    inputElement.addParsedExpression(createLiteral())
    assertFalse(transform.test(inputElement))
  }

  @Test
  fun testConditionOnCorrectMethodCallOneArgument() {
    val inputElement = createMethodCall(methodName)
    inputElement.addParsedExpression(createLiteral())
    assertTrue(transform.test(inputElement))
  }

  @Test
  fun testTransformOnCorrectForm() {
    val inputElement = createMethodCall(methodName)
    val literal = createLiteral()
    inputElement.addParsedExpression(literal)
    assertThat(transform.transform(inputElement), equalTo(literal as GradleDslElement))
  }

  @Test
  fun testTransformOnIncorrectForm() {
    val inputElement = createMethodCall(methodName)
    val expressionMap = GradleDslExpressionMap(inputElement, GradleNameElement.create("unusedListName"))
    inputElement.addParsedExpressionMap(expressionMap)
    assertNull(transform.transform(inputElement))
  }

  @Test
  fun testBindCreatesNewElement() {
    val inputElement = null
    val resultElement = transform.bind(gradleDslFile, inputElement, true, "statementName") as GradleDslMethodCall
    assertThat(resultElement.name, equalTo("statementName"))
    assertThat(resultElement.methodName, equalTo(methodName))
    assertThat(resultElement.arguments.size, equalTo(1))
    val argumentElement = resultElement.arguments[0] as GradleDslLiteral
    assertThat(argumentElement.value as Boolean, equalTo(true))
    assertThat(argumentElement.name, equalTo("statementName"))
    assertThat(argumentElement.parent as GradleDslMethodCall, equalTo(resultElement))
  }

  @Test
  fun testBindIncorrectFormCreateNewElement() {
    val inputElement = createMethodCall(methodName)
    val expressionMap = GradleDslExpressionMap(inputElement, GradleNameElement.create("unusedListName"))
    inputElement.addParsedExpressionMap(expressionMap)
    val resultElement = transform.bind(gradleDslFile, inputElement, "32", "statementName") as GradleDslMethodCall
    assertThat(resultElement.name, equalTo("statementName"))
    assertThat(resultElement.methodName, equalTo(methodName))
    assertThat(resultElement.arguments.size, equalTo(1))
    val argumentElement = resultElement.arguments[0] as GradleDslLiteral
    assertThat(argumentElement.value as String, equalTo("32"))
    assertThat(argumentElement.name, equalTo("statementName"))
    assertThat(argumentElement.parent as GradleDslMethodCall, equalTo(resultElement))
  }

  @Test
  fun testBindElementReplacesArgumentValue() {
    val inputElement = createMethodCall(methodName, "statement")
    val literal = createLiteral("")
    literal.setValue(78)
    inputElement.addParsedExpression(literal)
    val resultElement = transform.bind(gradleDslFile, inputElement, "32", "newName") as GradleDslMethodCall
    assertThat(resultElement, sameInstance(inputElement))
    // Method call element name doesn't change, we are re-using the element
    assertThat(resultElement.name, equalTo("statement"))
    assertThat(resultElement.methodName, equalTo(methodName))
    assertThat(resultElement.arguments.size, equalTo(1))
    val argumentElement = resultElement.arguments[0] as GradleDslLiteral
    // Element instance should be reused.
    assertThat(argumentElement, sameInstance(literal))
    assertThat(argumentElement.value as String, equalTo("32"))
    // Name is kept form the literal.
    assertTrue(argumentElement.nameElement.isEmpty)
    assertThat(argumentElement.parent as GradleDslMethodCall, equalTo(resultElement))
  }

  @Test
  fun testBindReferenceReplaceArgumentElement() {
    val inputElement = createMethodCall(methodName, "statement")
    val literal = createLiteral("")
    literal.setValue("Hello")
    inputElement.addParsedExpression(literal)
    val resultElement = transform.bind(gradleDslFile, inputElement, ReferenceTo("prop"), "newName") as GradleDslMethodCall
    assertThat(resultElement, sameInstance(inputElement))
    // Method call element name doesn't change, we are re-using the element instance
    assertThat(resultElement.name, equalTo("statement"))
    assertThat(resultElement.methodName, equalTo(methodName))
    assertThat(resultElement.arguments.size, equalTo(1))
    val argumentElement = resultElement.arguments[0] as GradleDslReference
    assertThat(argumentElement.referenceText as String, equalTo("prop"))
    // Name is kept form the literal.
    assertTrue(argumentElement.nameElement.isEmpty)
    assertThat(argumentElement.parent as GradleDslMethodCall, equalTo(resultElement))
  }
}