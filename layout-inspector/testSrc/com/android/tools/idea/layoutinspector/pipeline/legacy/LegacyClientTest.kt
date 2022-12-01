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

import com.android.testutils.MockitoKt
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.whenever
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.layoutinspector.InspectorClientProvider
import com.android.tools.idea.layoutinspector.LEGACY_DEVICE
import com.android.tools.idea.layoutinspector.LayoutInspectorBundle
import com.android.tools.idea.layoutinspector.LayoutInspectorRule
import com.android.tools.idea.layoutinspector.LegacyClientProvider
import com.android.tools.idea.layoutinspector.createProcess
import com.android.tools.idea.layoutinspector.pipeline.CONNECT_TIMEOUT_MESSAGE_KEY
import com.android.tools.idea.layoutinspector.pipeline.CONNECT_TIMEOUT_SECONDS
import com.android.tools.idea.layoutinspector.pipeline.DisconnectedClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientLaunchMonitor
import com.android.tools.idea.layoutinspector.resource.ResourceLookup
import com.android.tools.idea.layoutinspector.ui.InspectorBannerService
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.ListenerCollection
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.DisposableRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.argThat
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class LegacyClientTest {
  private val windowIds = mutableListOf<String>()

  private val disposableRule = DisposableRule()
  private val projectRule: AndroidProjectRule = AndroidProjectRule.onDisk()
  private val scheduler = VirtualTimeScheduler()

  private val legacyClientProvider = InspectorClientProvider { params, inspector ->
    val loader = mock(LegacyTreeLoader::class.java)
    doAnswer { windowIds }.whenever(loader).getAllWindowIds(ArgumentMatchers.any())
    val client = LegacyClientProvider(disposableRule.disposable, loader).create(params, inspector) as LegacyClient
    client.launchMonitor = InspectorClientLaunchMonitor(projectRule.project, ListenerCollection.createWithDirectExecutor(), scheduler)
    client
  }
  private val inspectorRule = LayoutInspectorRule(listOf(legacyClientProvider), projectRule)

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(inspectorRule).around(disposableRule)!!

  @Before
  fun setUp() {
    inspectorRule.attachDevice(LEGACY_DEVICE)
  }

  @Test
  fun testReloadAllWindows() {
    windowIds.addAll(listOf("window1", "window2", "window3"))
    inspectorRule.processes.selectedProcess = LEGACY_DEVICE.createProcess() // This causes the tree to get loaded as a side effect
    val client = inspectorRule.inspectorClient as LegacyClient

    verify(client.treeLoader).loadComponentTree(
      argThat { event: LegacyEvent -> event.windowId == "window1" },
      any(ResourceLookup::class.java),
      eq(client.process)
    )
    verify(client.treeLoader).loadComponentTree(
      argThat { event: LegacyEvent -> event.windowId == "window2" },
      any(ResourceLookup::class.java),
      eq(client.process)
    )
    verify(client.treeLoader).loadComponentTree(
      argThat { event: LegacyEvent -> event.windowId == "window3" },
      any(ResourceLookup::class.java),
      eq(client.process)
    )
  }

  @Test
  fun testReloadAllWindowsWithNone() {
    val executor = Executors.newSingleThreadExecutor()
    executor.execute {
      inspectorRule.processes.selectedProcess = LEGACY_DEVICE.createProcess()
    }
    waitForCondition(5, TimeUnit.SECONDS) { inspectorRule.inspectorClient is LegacyClient }
    assertThat((inspectorRule.inspectorClient as LegacyClient).reloadAllWindows()).isFalse()
    scheduler.advanceBy(CONNECT_TIMEOUT_SECONDS + 1, TimeUnit.SECONDS)
    val banner = InspectorBannerService.getInstance(projectRule.project) ?: error("no banner")
    val notification1 = banner.notifications.single()
    assertThat(notification1.message).isEqualTo(LayoutInspectorBundle.message(CONNECT_TIMEOUT_MESSAGE_KEY))

    // User disconnects:
    notification1.actions.last().actionPerformed(MockitoKt.mock())
    waitForCondition(5, TimeUnit.SECONDS) { inspectorRule.inspectorClient === DisconnectedClient }
    executor.shutdownNow()
  }

  @Test
  fun testConnect() {
    Executors.newSingleThreadExecutor().execute {
      Thread.sleep(1000)
      windowIds.add("window1")
    }
    inspectorRule.processes.selectedProcess = LEGACY_DEVICE.createProcess()
    val client = inspectorRule.inspectorClient as LegacyClient
    verify(client.treeLoader).loadComponentTree(
      argThat { event: LegacyEvent -> event.windowId == "window1" },
      any(ResourceLookup::class.java),
      eq(client.process)
    )
  }
}
