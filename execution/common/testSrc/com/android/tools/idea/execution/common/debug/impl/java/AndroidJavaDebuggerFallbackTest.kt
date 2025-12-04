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
package com.android.tools.idea.execution.common.debug.impl.java

import com.android.ddmlib.Client
import com.android.fakeadbserver.DeviceState
import com.android.sdklib.AndroidApiLevel
import com.android.tools.adblib.testutils.FakeAdbServerAdbLibRule
import com.android.tools.idea.execution.common.launchAndWaitForProcess
import com.android.tools.idea.execution.common.debug.AndroidDebuggerState
import com.android.tools.idea.execution.common.debug.DebugSessionStarter
import com.android.tools.idea.execution.common.debug.DebuggerThreadCleanupRule
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.xdebugger.XDebuggerManager
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Tests for [AndroidJavaDebugger] code. */
class AndroidJavaDebuggerFallbackTest {

  @get:Rule(order = 0)
  val projectRule =
    AndroidProjectRule.withAndroidModels(
      AndroidModuleModelBuilder(
        ":",
        "debug",
        AndroidProjectBuilder(
          applicationIdFor = { "com.example.different.application.id.than.fake.process" }
        ),
      )
    )

  @get:Rule(order = 1) val fakeAdbRule = FakeAdbServerAdbLibRule()

  @get:Rule(order = 2)
  val debuggerThreadCleanupRule = DebuggerThreadCleanupRule { fakeAdbRule.adbServer }

  private lateinit var client: Client

  @Before
  fun setUp() = runTest {
    val deviceState = fakeAdbRule.connectDevice(
      "test_device_001",
      "test1",
      "test2",
      "model",
      AndroidApiLevel(26),
      DeviceState.HostConnectionType.USB)
    val appId = "com.test.app"
    client = deviceState.launchAndWaitForProcess(1234, 4321, appId, true)
  }

  @After
  fun tearDown() = runTest {
    XDebuggerManager.getInstance(projectRule.project).debugSessions.forEach { it.stop() }
  }

  @Test
  fun testNoMatchingApplicationId() = runTest {
    val session =
      DebugSessionStarter.attachDebuggerToClientAndShowTab(
        projectRule.project,
        client,
        AndroidJavaDebugger(),
        AndroidDebuggerState(),
      )
    Thread.sleep(250); // Let the virtual machine initialize. Otherwise, JDI Internal Event Handler thread is leaked.

    assertThat(session).isNotNull()
    assertThat(client.clientData.pid).isAtLeast(0)
    assertThat(session.sessionName).isEqualTo("Java Only (${client.clientData.pid})")
  }
}
