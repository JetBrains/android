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

import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.tools.instrumentation.threading.agent.callback.ThreadingCheckerTrampoline
import com.google.common.truth.Truth
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import kotlin.concurrent.thread

@RunsInEdt
class ThreadingCheckerHookImplTest {

  @get:Rule
  var edtRule = EdtRule()

  @get:Rule
  val exceptionRule = ExpectedException.none()

  private val mockThreadingViolationNotifier = mock<ThreadingViolationNotifier>()
  private val threadingCheckerHook = ThreadingCheckerHookImpl(mockThreadingViolationNotifier)

  init {
    ThreadingCheckerTrampoline.installHook(threadingCheckerHook)
  }

  @Test
  fun testVerifyOnUiThread_addsViolation_whenCalledFromWorkerThread() {
    val expectedViolatingMethod =
      "com.android.tools.idea.instrumentation.threading.ThreadingCheckerHookImplTest\$checkForUiThreadOnWorkerThread\$1#invoke"
    checkForUiThreadOnWorkerThread()

    Truth.assertThat(threadingCheckerHook.threadingViolations.keys).containsExactly(expectedViolatingMethod)
    Truth.assertThat(threadingCheckerHook.threadingViolations[expectedViolatingMethod]!!.get()).isEqualTo(1L)
    verify(mockThreadingViolationNotifier).notify(
      eq("Threading violation: methods annotated with @UiThread should be called on the UI thread"),
      eq(expectedViolatingMethod))

    checkForUiThreadOnWorkerThread()
    Truth.assertThat(threadingCheckerHook.threadingViolations.keys).containsExactly(expectedViolatingMethod)
    Truth.assertThat(threadingCheckerHook.threadingViolations[expectedViolatingMethod]!!.get()).isEqualTo(2L)
    verifyNoMoreInteractions(mockThreadingViolationNotifier)
  }

  private fun checkForUiThreadOnWorkerThread() {
    thread {
      ThreadingCheckerTrampoline.verifyOnUiThread()
    }.join()
  }

  @Test
  fun testVerifyOnWorkerThread_addsViolation_whenCalledFromUiThread() {
    val expectedViolatingMethod =
      "com.android.tools.idea.instrumentation.threading.ThreadingCheckerHookImplTest#testVerifyOnWorkerThread_addsViolation_whenCalledFromUiThread"
    ThreadingCheckerTrampoline.verifyOnWorkerThread()

    Truth.assertThat(threadingCheckerHook.threadingViolations.keys).containsExactly(expectedViolatingMethod)
    Truth.assertThat(threadingCheckerHook.threadingViolations[expectedViolatingMethod]!!.get()).isEqualTo(1L)
    verify(mockThreadingViolationNotifier).notify(
      eq("Threading violation: methods annotated with @WorkerThread should not be called on the UI thread"),
      eq(expectedViolatingMethod))

    ThreadingCheckerTrampoline.verifyOnWorkerThread()
    Truth.assertThat(threadingCheckerHook.threadingViolations.keys).containsExactly(expectedViolatingMethod)
    Truth.assertThat(threadingCheckerHook.threadingViolations[expectedViolatingMethod]!!.get()).isEqualTo(2L)
    verifyNoMoreInteractions(mockThreadingViolationNotifier)
  }

  @Test
  fun testVerifyOnUiThread_doesNotAddViolation_whenCalledFromUiThread() {
    ThreadingCheckerTrampoline.verifyOnUiThread()
    Truth.assertThat(threadingCheckerHook.threadingViolations.keys).isEmpty()
  }

  @Test
  fun testVerifyOnWorkerThread_doesNotAddViolation_whenCalledFromWorkerThread() {
    thread {
      ThreadingCheckerTrampoline.verifyOnWorkerThread()
    }.join()
    Truth.assertThat(threadingCheckerHook.threadingViolations.keys).isEmpty()
  }

  @Test
  fun testUsingSystemPropertyToSuppressNotifications() {
    val propertyName = "android.studio.instrumentation.threading.suppress-notifications"
    val origPropValue = System.getProperty(propertyName)
    try {
      System.setProperty(propertyName, "true")
      ThreadingCheckerTrampoline.verifyOnWorkerThread()

      verifyNoMoreInteractions(mockThreadingViolationNotifier)
    }
    finally {
      if (origPropValue != null) {
        System.setProperty(propertyName, origPropValue)
      }
      else {
        System.clearProperty(propertyName)
      }
    }
  }

  @Test
  fun testUsingSystemPropertyToLogErrorsInsteadOfWarnings() {
    val propertyName = "android.studio.instrumentation.threading.log-errors"
    val origPropValue = System.getProperty(propertyName)
    try {
      System.setProperty(propertyName, "true")
      // Note that logger.error() call inside a unit test results in an exception being thrown
      exceptionRule.expect(AssertionError::class.java)
      exceptionRule.expectMessage("Threading violation")
      ThreadingCheckerTrampoline.verifyOnWorkerThread()
    }
    finally {
      if (origPropValue != null) {
        System.setProperty(propertyName, origPropValue)
      }
      else {
        System.clearProperty(propertyName)
      }
    }
  }
}