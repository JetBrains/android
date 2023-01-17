/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.lang.proguardR8

import com.android.tools.idea.testing.caret
import com.google.common.truth.Truth
import com.intellij.testFramework.UITestUtil
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ThrowableRunnable

class ProguardR8TypedHandlerTest : ProguardR8TestCase() {
  private lateinit var tester: CompletionAutoPopupTester

  override fun runInDispatchThread(): Boolean = false
  override fun runTestRunnable(testRunnable: ThrowableRunnable<Throwable>) = tester.runWithAutoPopupEnabled(testRunnable)

  override fun setUp() {
    UITestUtil.replaceIdeEventQueueSafely() // See UsefulTestCase#runBare which should be the stack frame above this one.
    runInEdtAndWait { super.setUp() }
    tester = CompletionAutoPopupTester(myFixture)
  }

  override fun tearDown() {
    runInEdtAndWait { super.tearDown() }
  }

  fun testAutoPopupCompletion() {

    myFixture.configureByText(ProguardR8FileType.INSTANCE, "$caret")
    myFixture.type('-')
    tester.joinAutopopup()
    tester.joinCompletion()
    Truth.assertThat(myFixture.lookupElementStrings).isNotEmpty()
    Truth.assertThat(myFixture.lookupElementStrings).contains("keepattributes")
  }

  fun testOpenPopupForInnerClassesAfterDollarSymbol() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      public class MyClass {
        class InnerClass {}
      }
    """.trimIndent()
    )

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE, """
        -keep class test.MyClass$caret {
        }
    """.trimIndent()
    )

    myFixture.type('$')
    tester.joinAutopopup()
    tester.joinCompletion()
    Truth.assertThat(myFixture.lookupElementStrings).isNotEmpty()
    Truth.assertThat(myFixture.lookupElementStrings).contains("InnerClass")
  }
}