/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.pipeline.legacy

import com.android.testutils.waitForCondition
import com.android.tools.idea.layoutinspector.InspectorClientProvider
import com.android.tools.idea.layoutinspector.LEGACY_DEVICE
import com.android.tools.idea.layoutinspector.LayoutInspectorBundle
import com.android.tools.idea.layoutinspector.LayoutInspectorRule
import com.android.tools.idea.layoutinspector.LegacyClientProvider
import com.android.tools.idea.layoutinspector.createProcess
import com.android.tools.idea.layoutinspector.pipeline.CONNECT_TIMEOUT_MESSAGE_KEY
import com.android.tools.idea.layoutinspector.pipeline.DisconnectedClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientLaunchMonitor
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.ListenerCollection
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo.AttachErrorState
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.argThat
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class LegacyClientTest {
  private val windowIds = mutableListOf<String>()
  private val projectRule: AndroidProjectRule = AndroidProjectRule.onDisk()
  private val unused = TestScope(StandardTestDispatcher(TestCoroutineScheduler()))
  private val timeoutScope = TestScope(StandardTestDispatcher(TestCoroutineScheduler()))
  private val debuggerScope = TestScope(StandardTestDispatcher(TestCoroutineScheduler()))
  private val legacyClientProvider = InspectorClientProvider { params, inspector ->
    val loader = mock(LegacyTreeLoader::class.java)
    doAnswer { windowIds }.whenever(loader).getAllWindowIds(ArgumentMatchers.any())
    val client =
      LegacyClientProvider({ projectRule.testRootDisposable }, loader).create(params, inspector)
        as LegacyClient
    val notificationModel = inspector.notificationModel
    client.launchMonitor =
      InspectorClientLaunchMonitor(
        projectRule.project,
        notificationModel,
        ListenerCollection.createWithDirectExecutor(),
        client.stats,
        unused,
        timeoutScope,
        debuggerScope,
      )
    client
  }
  private val inspectorRule = LayoutInspectorRule(listOf(legacyClientProvider), projectRule)

  @get:Rule val ruleChain = RuleChain.outerRule(projectRule).around(inspectorRule)!!

  @Before
  fun setUp() {
    inspectorRule.attachDevice(LEGACY_DEVICE)
  }

  @Test
  fun testReloadAllWindows() {
    windowIds.addAll(listOf("window1", "window2", "window3"))
    inspectorRule.processes.selectedProcess =
      LEGACY_DEVICE.createProcess() // This causes the tree to get loaded as a side effect
    val client = inspectorRule.inspectorClient as LegacyClient

    verify(client.treeLoader)
      .loadComponentTree(
        argThat { event: LegacyEvent -> event.windowId == "window1" },
        any(),
        eq(client.process),
      )
    verify(client.treeLoader)
      .loadComponentTree(
        argThat { event: LegacyEvent -> event.windowId == "window2" },
        any(),
        eq(client.process),
      )
    verify(client.treeLoader)
      .loadComponentTree(
        argThat { event: LegacyEvent -> event.windowId == "window3" },
        any(),
        eq(client.process),
      )
  }

  @Test
  fun testReloadAllWindowsWithNone() {
    // This test may end up in a deadlock if a synchronized launcher is used.
    inspectorRule.launchSynchronously = false
    // The launch will attempt to launch an app inspection client and the a legacy client both on
    // connect and the later disconnect.
    // i.e. a total of 4 launches.
    // The legacy connection will never succeed (because there are no windows). Do not call
    // awaitLaunch before the end of the test.
    inspectorRule.startLaunch(4)
    val executor = Executors.newSingleThreadExecutor()
    executor.execute { inspectorRule.processes.selectedProcess = LEGACY_DEVICE.createProcess() }
    waitForCondition(5, TimeUnit.SECONDS) { inspectorRule.inspectorClient is LegacyClient }
    val client = inspectorRule.inspectorClient as LegacyClient
    waitForCondition(5, TimeUnit.SECONDS) {
      client.launchMonitor.currentProgress == AttachErrorState.ADB_PING
    }
    waitForCondition(5, TimeUnit.SECONDS) { client.launchMonitor.timeoutHandlerScheduled }
    assertThat(client.reloadAllWindows()).isFalse()
    timeoutScope.testScheduler.advanceUntilIdle()
    val notificationModel = inspectorRule.notificationModel
    val notification1 = notificationModel.notifications.single()
    assertThat(notification1.message)
      .isEqualTo(LayoutInspectorBundle.message(CONNECT_TIMEOUT_MESSAGE_KEY))

    // User disconnects:
    notification1.actions.last().invoke(mock())
    waitForCondition(5, TimeUnit.SECONDS) { inspectorRule.inspectorClient === DisconnectedClient }
    executor.shutdownNow()
    inspectorRule.awaitLaunch()
  }

  @Test
  fun testConnect() {
    Executors.newSingleThreadExecutor().execute {
      Thread.sleep(1000)
      windowIds.add("window1")
    }
    inspectorRule.processes.selectedProcess = LEGACY_DEVICE.createProcess()
    val client = inspectorRule.inspectorClient as LegacyClient
    verify(client.treeLoader)
      .loadComponentTree(
        argThat { event: LegacyEvent -> event.windowId == "window1" },
        any(),
        eq(client.process),
      )
  }
}
