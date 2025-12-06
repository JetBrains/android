/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.streaming.emulator

import com.android.sdklib.deviceprovisioner.ProcessHandleProvider
import com.android.testutils.ProcessHandleProviderRule
import com.android.tools.idea.avdmanager.EmulatorLogListener
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import java.nio.file.Paths
import org.junit.Rule
import org.junit.Test

/** Tests for [EmulatorNotificationDispatcher]. */
@RunsInEdt
class EmulatorNotificationDispatcherTest {

  private val disposableRule = DisposableRule()
  @get:Rule
  val ruleChain = RuleChain(ApplicationRule(), disposableRule, ProcessHandleProviderRule(), EdtRule())

  private val testRootDisposable
    get() = disposableRule.disposable

  @Test
  fun testDispatcher() {
    val notificationDispatcher = EmulatorNotificationDispatcher().also { Disposer.register(testRootDisposable, it) }
    val receivedMessages = mutableListOf<String>()
    val avdFolder = Paths.get("/tmp/myAvd")
    val processHandle = ProcessHandleProvider.getProcessHandle(12345)!!
    val unrelatedProcessHandle = ProcessHandleProvider.getProcessHandle(67890)!!
    val publisher = ApplicationManager.getApplication().messageBus.syncPublisher(EmulatorLogListener.TOPIC)
    publisher.messageLogged(processHandle, avdFolder, EmulatorLogListener.Severity.WARNING, true, "warning 1")
    publisher.messageLogged(processHandle, avdFolder, EmulatorLogListener.Severity.WARNING, false, "warning 2")
    publisher.messageLogged(unrelatedProcessHandle, avdFolder, EmulatorLogListener.Severity.WARNING, true, "warning 3")
    publisher.messageLogged(processHandle, avdFolder, EmulatorLogListener.Severity.INFO, true, "info 1")
    notificationDispatcher.addListener(processHandle, object : EmulatorNotificationDispatcher.Listener {
      override fun notificationMessageLogged(severity: EmulatorLogListener.Severity, message: String) {
        receivedMessages.add(message)
      }
    })
    assertThat(receivedMessages).containsExactly("warning 1", "info 1").inOrder()
    publisher.messageLogged(processHandle, avdFolder, EmulatorLogListener.Severity.INFO, true, "info 2")
    assertThat(receivedMessages).containsExactly("warning 1", "info 1", "info 2").inOrder()
  }
}