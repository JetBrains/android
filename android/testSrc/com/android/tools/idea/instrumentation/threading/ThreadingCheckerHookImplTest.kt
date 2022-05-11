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
package com.android.tools.idea.instrumentation.threading

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.instrumentation.threading.agent.callback.ThreadingCheckerTrampoline
import com.google.common.truth.Truth
import com.intellij.openapi.components.service
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicLong

class ThreadingCheckerHookImplTest {
  @get:Rule
  var androidProjectRule = AndroidProjectRule.inMemory()

  @get:Rule
  val edtRule = EdtRule()

  @Before
  fun setUp() {
    ThreadingCheckerHookImpl.initialize()
    violations().clear()
  }

  @Test
  fun verifyOnUiThread_addsViolation_whenCalledFromNonUiThread() {
    // Current method is a violating method since it calls
    // ThreadingCheckerTrampoline.verifyOnUiThread() not on the UI thread.
    val expectedViolatingMethod =
      "${ThreadingCheckerHookImplTest::class.qualifiedName}#${object{}.javaClass.enclosingMethod.name}"

    ThreadingCheckerTrampoline.verifyOnUiThread()
    Truth.assertThat(violations().keys).containsExactly(expectedViolatingMethod)
    Truth.assertThat(violations()[expectedViolatingMethod]!!.get()).isEqualTo(1L)

    ThreadingCheckerTrampoline.verifyOnUiThread()
    Truth.assertThat(violations().keys).containsExactly(expectedViolatingMethod)
    Truth.assertThat(violations()[expectedViolatingMethod]!!.get()).isEqualTo(2L)
  }

  @Test
  @RunsInEdt
  fun verifyOnUiThread_doesNotAddViolation_whenCalledFromUiThread() {
    ThreadingCheckerTrampoline.verifyOnUiThread()
    Truth.assertThat(violations().keys).isEmpty()
  }

  private fun violations(): ConcurrentMap<String, AtomicLong> {
    return service<ThreadingCheckerHookImpl>().threadingViolations
  }
}