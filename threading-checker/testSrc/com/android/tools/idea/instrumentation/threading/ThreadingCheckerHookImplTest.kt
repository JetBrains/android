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

import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.instrumentation.threading.agent.callback.ThreadingCheckerTrampoline
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.ProductDetails
import com.google.wireless.android.sdk.stats.ThreadingAgentUsageEvent
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import kotlin.concurrent.thread

@RunsInEdt
class ThreadingCheckerHookImplTest {

  @get:Rule
  var edtRule = EdtRule()

  @get:Rule
  val applicationRule = ApplicationRule()

  @get:Rule
  val exceptionRule = ExpectedException.none()

  private val mockThreadingViolationNotifier = mock<ThreadingViolationNotifier>()
  private val threadingCheckerHook = ThreadingCheckerHookImpl(mockThreadingViolationNotifier)
  private val tracker = TestUsageTracker(VirtualTimeScheduler())

  @Before
  fun setUp() {
    UsageTracker.setWriterForTest(tracker)
    ThreadingCheckerTrampoline.installHook(threadingCheckerHook)
  }

  @After
  fun cleanUp() {
    UsageTracker.cleanAfterTesting()
    ThreadingCheckerTrampoline.removeHook(threadingCheckerHook)
  }

  init {
    // Do not log errors as to not throw exceptions by default
    System.setProperty("android.studio.instrumentation.threading.log-errors", "false")
  }

  @Test
  fun testVerifyOnUiThread_addsViolation_whenCalledFromWorkerThread() {
    System.setProperty("android.studio.instrumentation.threading.suppress-notifications", "false")
    val expectedViolatingMethod =
      "com.android.tools.idea.instrumentation.threading.ThreadingCheckerHookImplTest#checkForUiThreadOnWorkerThread\$lambda\$1"
    checkForUiThreadOnWorkerThread()
    Truth.assertThat(threadingCheckerHook.threadingViolations.keys).containsExactly(expectedViolatingMethod)
    Truth.assertThat(threadingCheckerHook.threadingViolations[expectedViolatingMethod]!!.get()).isEqualTo(1L)
    verify(mockThreadingViolationNotifier).notify(
      eq("Threading violation: methods annotated with @UiThread or @RequiresEdt should be called on the UI thread"),
      eq(expectedViolatingMethod))

    checkForUiThreadOnWorkerThread()
    Truth.assertThat(threadingCheckerHook.threadingViolations.keys).containsExactly(expectedViolatingMethod)
    Truth.assertThat(threadingCheckerHook.threadingViolations[expectedViolatingMethod]!!.get()).isEqualTo(2L)
    verifyNoMoreInteractions(mockThreadingViolationNotifier)

    Truth.assertThat(tracker.usages.map { u -> u.studioEvent.toBuilder().clearStudioSessionId().clearIdeBrand().build() })
      .contains(AndroidStudioEvent.newBuilder()
                         .setKind(AndroidStudioEvent.EventKind.THREADING_AGENT_STATS)
                         .setThreadingAgentUsageEvent(
                           ThreadingAgentUsageEvent.newBuilder()
                             .setVerifyUiThreadCount(1) // The value is 1 and not 2 since we limit the frequency of logged events
                             .setVerifyWorkerThreadCount(0))
                         .setIdeaIsInternal(true)
                         .setProductDetails(ProductDetails.newBuilder().setVersion(UsageTracker.version!!))
                         .build())
  }

  private fun checkForUiThreadOnWorkerThread() {
    thread {
      ThreadingCheckerTrampoline.verifyOnUiThread()
    }.join()
  }

  @Test
  fun testVerifyOnWorkerThread_addsViolation_whenCalledFromUiThread() {
    System.setProperty("android.studio.instrumentation.threading.suppress-notifications", "false")
    val expectedViolatingMethod =
      "com.android.tools.idea.instrumentation.threading.ThreadingCheckerHookImplTest#testVerifyOnWorkerThread_addsViolation_whenCalledFromUiThread"
    ThreadingCheckerTrampoline.verifyOnWorkerThread()

    Truth.assertThat(threadingCheckerHook.threadingViolations.keys).containsExactly(expectedViolatingMethod)
    Truth.assertThat(threadingCheckerHook.threadingViolations[expectedViolatingMethod]!!.get()).isEqualTo(1L)
    verify(mockThreadingViolationNotifier).notify(
      eq("Threading violation: methods annotated with @WorkerThread or @RequiresBackgroundThread should not be called on the UI thread"),
      eq(expectedViolatingMethod))

    ThreadingCheckerTrampoline.verifyOnWorkerThread()
    Truth.assertThat(threadingCheckerHook.threadingViolations.keys).containsExactly(expectedViolatingMethod)
    Truth.assertThat(threadingCheckerHook.threadingViolations[expectedViolatingMethod]!!.get()).isEqualTo(2L)
    verifyNoMoreInteractions(mockThreadingViolationNotifier)

    Truth.assertThat(tracker.usages.map { u -> u.studioEvent.toBuilder().clearStudioSessionId().clearIdeBrand().build() })
      .contains(AndroidStudioEvent.newBuilder()
                         .setKind(AndroidStudioEvent.EventKind.THREADING_AGENT_STATS)
                         .setThreadingAgentUsageEvent(
                           ThreadingAgentUsageEvent.newBuilder()
                             .setVerifyUiThreadCount(0)
                             .setVerifyWorkerThreadCount(1)) // The value is 1 and not 2 since we limit the frequency of logged events
                         .setIdeaIsInternal(true)
                         .setProductDetails(ProductDetails.newBuilder().setVersion(UsageTracker.version!!))
                         .build())
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
  fun `verify the read lock check adds a violation when accessed without a read lock`() {
    Truth.assertThat(threadingCheckerHook.threadingViolations.keys).hasSize(0)
    thread {
      ThreadingCheckerTrampoline.verifyReadLock()
    }.join()
    Truth.assertThat(threadingCheckerHook.threadingViolations.keys).hasSize(1)
  }

  @Test
  fun `verify the read lock check does not add a violation when accessed with a read lock`() {
    Truth.assertThat(threadingCheckerHook.threadingViolations.keys).hasSize(0)
    runReadAction {
      ThreadingCheckerTrampoline.verifyReadLock()
      Truth.assertThat(threadingCheckerHook.threadingViolations.keys).hasSize(0)
    }
  }

  @Test
  fun `verify the write lock check adds a violation when called without a write lock`() {
    Truth.assertThat(threadingCheckerHook.threadingViolations.keys).hasSize(0)
    thread {
      ThreadingCheckerTrampoline.verifyWriteLock()
      Truth.assertThat(threadingCheckerHook.threadingViolations.keys).hasSize(1)
    }.join()
  }
  @Test
  fun `verify the write lock check does not add a violation when called with a write lock`() {
    Truth.assertThat(threadingCheckerHook.threadingViolations.keys).hasSize(0)
    runWriteAction {
      ThreadingCheckerTrampoline.verifyWriteLock()
      Truth.assertThat(threadingCheckerHook.threadingViolations.keys).hasSize(0)
    }

  }

  @Test
  fun `verify the no read lock check adds a violation when called without a read lock`() {
    Truth.assertThat(threadingCheckerHook.threadingViolations.keys).hasSize(0)
    runReadAction {
      ThreadingCheckerTrampoline.verifyNoReadLock()
      Truth.assertThat(threadingCheckerHook.threadingViolations.keys).hasSize(1)
    }
  }

  @Test
  fun `verify the no read lock check does not add a violation when called without a read lock`() {
    Truth.assertThat(threadingCheckerHook.threadingViolations.keys).hasSize(0)
    thread {
      ThreadingCheckerTrampoline.verifyNoReadLock()
      Truth.assertThat(threadingCheckerHook.threadingViolations.keys).hasSize(0)
    }
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