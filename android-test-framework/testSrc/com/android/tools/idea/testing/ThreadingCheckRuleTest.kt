/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.testing

import com.android.tools.instrumentation.threading.agent.callback.ThreadingCheckerTrampoline
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.runner.Description
import org.junit.runners.model.Statement

class ThreadingCheckRuleTest {
  @get:Rule
  val exceptionRule: ExpectedException = ExpectedException.none()

  @Test
  fun ruleThrows_whenTestedMethodThreadVerificationFails() {
    val rule = ThreadingCheckRule()
    val statement = rule.apply(object : Statement() {
      override fun evaluate() {
        ThreadingCheckerTrampoline.verifyOnUiThread()
      }
    }, Description.createSuiteDescription("description"))

    exceptionRule.expect(RuntimeException::class.java)
    exceptionRule.expectMessage("is expected to be called on EventDispatchThread")
    statement.evaluate()
  }

  @Test
  fun ruleDoesNotThrow_whenTestedMethodThreadVerificationSucceeds() {
    val rule = ThreadingCheckRule()
    val statement = rule.apply(object : Statement() {
      override fun evaluate() {
        ThreadingCheckerTrampoline.verifyOnWorkerThread()
      }
    }, Description.createSuiteDescription("description"))

    statement.evaluate()
  }
}