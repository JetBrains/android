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

import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.instrumentation.threading.agent.callback.ThreadingCheckerTrampoline
import com.google.common.truth.Truth
import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.Collections

class ThreadingCheckerHookImplTest : LightPlatformTestCase() {
  private val threadingCheckerHook: ThreadingCheckerHookImpl = ThreadingCheckerHookImpl()
  private val notifications: MutableList<Notification> = Collections.synchronizedList(mutableListOf())

  override fun setUp() {
    super.setUp()
    ThreadingCheckerTrampoline.installHook(threadingCheckerHook)
    threadingCheckerHook.threadingViolations.clear()

    ApplicationManager.getApplication().messageBus.connect(testRootDisposable).subscribe(Notifications.TOPIC, object : Notifications {
      override fun notify(notification: Notification) {
        if (notification.groupId == "Threading Violation Notification") {
          notifications += notification
        }
      }
    })
    notifications.clear()
  }

  @Test
  fun testVerifyOnUiThread_addsViolation_whenCalledFromWorkerThread() {
    val expectedViolatingMethod =
      "com.android.tools.idea.instrumentation.threading.ThreadingCheckerHookImplTest\$checkForUiThreadOnWorkerThread\$1#invokeSuspend"
    checkForUiThreadOnWorkerThread()

    Truth.assertThat(threadingCheckerHook.threadingViolations.keys).containsExactly(expectedViolatingMethod)
    Truth.assertThat(threadingCheckerHook.threadingViolations[expectedViolatingMethod]!!.get()).isEqualTo(1L)
    UIUtil.dispatchAllInvocationEvents()
    Truth.assertThat(notifications).hasSize(1)

    checkForUiThreadOnWorkerThread()
    Truth.assertThat(threadingCheckerHook.threadingViolations.keys).containsExactly(expectedViolatingMethod)
    Truth.assertThat(threadingCheckerHook.threadingViolations[expectedViolatingMethod]!!.get()).isEqualTo(2L)
    UIUtil.dispatchAllInvocationEvents()
    Truth.assertThat(notifications).hasSize(1)
  }

  private fun checkForUiThreadOnWorkerThread() {
    runBlocking (workerThread) {
      ThreadingCheckerTrampoline.verifyOnUiThread()
    }
  }

  @Test
  fun testVerifyOnWorkerThread_addsViolation_whenCalledFromUiThread() {
    val expectedViolatingMethod =
      "com.android.tools.idea.instrumentation.threading.ThreadingCheckerHookImplTest#testVerifyOnWorkerThread_addsViolation_whenCalledFromUiThread"
    ThreadingCheckerTrampoline.verifyOnWorkerThread()

    Truth.assertThat(threadingCheckerHook.threadingViolations.keys).containsExactly(expectedViolatingMethod)
    Truth.assertThat(threadingCheckerHook.threadingViolations[expectedViolatingMethod]!!.get()).isEqualTo(1L)
    UIUtil.dispatchAllInvocationEvents()
    Truth.assertThat(notifications).hasSize(1)

    ThreadingCheckerTrampoline.verifyOnWorkerThread()
    Truth.assertThat(threadingCheckerHook.threadingViolations.keys).containsExactly(expectedViolatingMethod)
    Truth.assertThat(threadingCheckerHook.threadingViolations[expectedViolatingMethod]!!.get()).isEqualTo(2L)
    UIUtil.dispatchAllInvocationEvents()
    Truth.assertThat(notifications).hasSize(1)
  }

  @Test
  fun testVerifyOnUiThread_doesNotAddViolation_whenCalledFromUiThread() {
    ThreadingCheckerTrampoline.verifyOnUiThread()
    Truth.assertThat(threadingCheckerHook.threadingViolations.keys).isEmpty()
    Truth.assertThat(notifications).isEmpty()
  }

  @Test
  fun testVerifyOnWorkerThread_doesNotAddViolation_whenCalledFromWorkerThread() {
    runBlocking (workerThread) {
      ThreadingCheckerTrampoline.verifyOnWorkerThread()
    }
    Truth.assertThat(threadingCheckerHook.threadingViolations.keys).isEmpty()
    Truth.assertThat(notifications).isEmpty()
  }
}