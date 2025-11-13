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

package com.android.tools.idea.adblib

import com.android.adblib.ServerStatus
import com.android.adblib.ddmlibcompatibility.testutils.InitAndroidDebugBridgeRule
import com.android.adblib.testingutils.FakeAdbServerRule
import com.android.tools.adblib.testutils.InitAdbLibApplicationServiceRule
import com.intellij.testFramework.ProjectRule
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import com.android.test.testutils.EnsureAndroidProjectRule

class AdbServerStatusReporterTest {
  private val projectRule = ProjectRule()
  private val initAdbLibApplicationServiceRule = InitAdbLibApplicationServiceRule()
  private val fakeAdbRule = FakeAdbServerRule()
  private val initAndroidDebugBridgeRule =
    InitAndroidDebugBridgeRule(alsoCreateBridge = true) { fakeAdbRule.adbServer.port }
  @get:Rule val ensureAndroidProjectRule = EnsureAndroidProjectRule()
  private lateinit var reporter: AdbServerStatusReporter

  private var statusCallbackCalled = false
  private val latch = CountDownLatch(1)

  @get:Rule
  val ruleChain =
    RuleChain.outerRule(projectRule)
      .around(initAdbLibApplicationServiceRule)
      .around(fakeAdbRule)
      .around(initAndroidDebugBridgeRule)!!

  private fun statusReporterCallback(status: ServerStatus) {
    Assert.assertNotNull("No server-status version", status.version)
    Assert.assertNotNull("No server-status executable path", status.absoluteExecutablePath)
    Assert.assertNotNull("No server-status executable log", status.absoluteLogPath)
    statusCallbackCalled = true
    latch.countDown()
  }

  @Test
  fun badUsbCableNotificationTest() {
    reporter = AdbServerStatusReporter(::statusReporterCallback)
    CoroutineScope(Dispatchers.IO).launch { reporter.execute(projectRule.project) }
    latch.await()
    Assert.assertTrue("ServerStatus callback was not called", statusCallbackCalled)
  }
}
