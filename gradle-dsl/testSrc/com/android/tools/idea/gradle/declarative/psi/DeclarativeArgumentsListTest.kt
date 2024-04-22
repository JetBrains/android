/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.declarative.psi

import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.LightPlatformTestCase

class DeclarativeArgumentsListTest : LightPlatformTestCase() {
  fun testGetStringArgument() {
    val psiFactory = DeclarativePsiFactory(project)
    val factory = psiFactory.createFactory("abc")
    factory.argumentsList!!.add(psiFactory.createLiteral("def"))
    val arguments = factory.argumentsList!!.arguments
    assertThat(arguments.map { it.text }).containsExactly("\"def\"")
  }

  fun testGetIntArgument() {
    val psiFactory = DeclarativePsiFactory(project)
    val factory = psiFactory.createFactory("abc")
    factory.argumentsList!!.add(psiFactory.createLiteral(123))
    val arguments = factory.argumentsList!!.arguments
    assertThat(arguments.map { it.text }).containsExactly("123")
  }

  fun testGetMultipleArguments() {
    val psiFactory = DeclarativePsiFactory(project)
    val factory = psiFactory.createFactory("abc")
    factory.argumentsList!!.add(psiFactory.createLiteral(123))
    factory.argumentsList!!.add(psiFactory.createLiteral("hello, world"))
    factory.argumentsList!!.add(psiFactory.createLiteral(true))
    val arguments = factory.argumentsList!!.arguments
    assertThat(arguments.map { it.text }).containsExactly("123", "\"hello, world\"", "true").inOrder()
  }

  fun testOneParameterFactory() {
    val psiFactory = DeclarativePsiFactory(project)
    val factory = psiFactory.createOneParameterFactory("abc", "\"hello, world\"")
    val arguments = factory.argumentsList!!.arguments
    assertThat(arguments.map { it.text }).containsExactly("\"hello, world\"")
  }
}