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
package com.android.tools.idea.layoutinspector

import com.android.adblib.DeviceSelector
import com.android.adblib.testing.FakeAdbSession
import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.layoutinspector.model.NotificationModel
import com.android.tools.idea.layoutinspector.pipeline.AbstractInspectorClient
import com.android.tools.idea.layoutinspector.pipeline.DisconnectedClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientLaunchMonitor
import com.android.tools.idea.layoutinspector.pipeline.TreeLoader
import com.android.tools.idea.layoutinspector.properties.PropertiesProvider
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorAttachToProcess.ClientType
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

class AbstractInspectorClientTest {
  @get:Rule val disposableRule = DisposableRule()
  @get:Rule val projectRule = ProjectRule()

  private val adbSession = FakeAdbSession()
  private val process = MODERN_DEVICE.createProcess()

  @Test
  fun clientWithAdbResponseConnects() {
    adbSession.deviceServices.configureShellCommand(
      DeviceSelector.fromSerialNumber(process.device.serial),
      "echo ok",
      stdout = "ok",
    )

    val project = projectRule.project
    val client =
      MyClient(project, process, NotificationModel(project), adbSession, disposableRule.disposable)
    val monitor = mock<InspectorClientLaunchMonitor>()
    client.launchMonitor = monitor
    runBlocking { client.connect(projectRule.project) }
    assertThat(client.isConnected).isTrue()
    verify(monitor).updateProgress(DynamicLayoutInspectorErrorInfo.AttachErrorState.ADB_PING)
  }

  @Test
  fun clientWithNoAdbResponseFailsToConnect() {
    adbSession.deviceServices.configureShellCommand(
      DeviceSelector.fromSerialNumber(process.device.serial),
      "echo ok",
      stdout = "",
      stderr = "error",
    )

    val project = projectRule.project
    val client =
      MyClient(project, process, NotificationModel(project), adbSession, disposableRule.disposable)
    val monitor = mock<InspectorClientLaunchMonitor>()
    client.launchMonitor = monitor
    runBlocking { client.connect(projectRule.project) }
    assertThat(client.isConnected).isFalse()
    verify(monitor, times(0))
      .updateProgress(DynamicLayoutInspectorErrorInfo.AttachErrorState.ADB_PING)
  }
}

private class MyClient(
  project: Project,
  process: ProcessDescriptor,
  notificationModel: NotificationModel,
  adbSession: FakeAdbSession,
  disposable: Disposable,
) :
  AbstractInspectorClient(
    ClientType.UNKNOWN_CLIENT_TYPE,
    project,
    notificationModel,
    process,
    DisconnectedClient.stats,
    AndroidCoroutineScope(disposable),
    disposable,
    adbSession,
  ) {
  override suspend fun doConnect() {}

  override suspend fun doDisconnect() {}

  override suspend fun startFetching() {}

  override suspend fun stopFetching() {}

  override fun refresh() {}

  override suspend fun saveSnapshot(path: Path) {}

  override val treeLoader: TreeLoader = mock()
  override val inLiveMode = false
  override val provider: PropertiesProvider = mock()
}
