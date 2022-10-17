/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.pipeline.appinspection

import com.android.ddmlib.testing.FakeAdbRule
import com.android.fakeadbserver.DeviceState
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.appinspection.api.AppInspectionApiServices
import com.android.tools.idea.appinspection.ide.InspectorArtifactService
import com.android.tools.idea.appinspection.inspector.api.AppInspectionArtifactNotFoundException
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.appinspection.inspector.api.launch.ArtifactCoordinate
import com.android.tools.idea.appinspection.inspector.api.launch.LibraryCompatbilityInfo
import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.internal.AppInspectionTarget
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.COMPOSE_INSPECTION_NOT_AVAILABLE
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.ComposeLayoutInspectorClient
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.INSPECTOR_NOT_FOUND_USE_SNAPSHOT
import com.android.tools.idea.layoutinspector.ui.InspectorBanner
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.project.Project
import com.intellij.testFramework.registerServiceInstance
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.runBlocking
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.UnknownCommandResponse
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Paths
import java.util.EnumSet

class ComposeLayoutInspectorClientTest {
  private val processDescriptor = object : ProcessDescriptor {
    override val device = object : DeviceDescriptor {
      override val manufacturer = "mfg"
      override val model = "model"
      override val serial = "emulator-1234"
      override val isEmulator = true
      override val apiLevel = 30
      override val version = "10.0.0"
      override val codename: String? = null
    }
    override val abiCpuArch = "x86_64"
    override val name = "my name"
    override val isRunning = true
    override val pid = 1234
    override val streamId = 4321L
  }

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @get:Rule
  val adbRule = FakeAdbRule()

  @Test
  fun testClientCreation() = runBlocking {
    adbRule.attachDevice(processDescriptor.device.serial, processDescriptor.device.manufacturer, processDescriptor.device.model,
                         processDescriptor.device.version, processDescriptor.device.apiLevel.toString(),
                         "arm64-v8a", emptyMap(), DeviceState.HostConnectionType.LOCAL, "myAvd", "/android/avds/myAvd")

    val artifactService = mock<InspectorArtifactService>()
    val messenger = mock<AppInspectorMessenger>()
    whenever(artifactService.getOrResolveInspectorArtifact(any(), any())).thenReturn(Paths.get("/foo/bar"))
    ApplicationManager.getApplication().registerServiceInstance(InspectorArtifactService::class.java, artifactService)
    val apiServices = mock<AppInspectionApiServices>()
    whenever(apiServices.launchInspector(any())).thenReturn(messenger)
    val target = mock<AppInspectionTarget>()
    whenever(messenger.sendRawCommand(any())).thenReturn(UnknownCommandResponse.getDefaultInstance().toByteArray())
    whenever(target.getLibraryVersions(any())).thenReturn(listOf(LibraryCompatbilityInfo(mock(), mock(), "1", "")))
    whenever(apiServices.attachToProcess(processDescriptor, projectRule.project.name)).thenReturn(target)

    val capabilities = EnumSet.noneOf(InspectorClient.Capability::class.java)
    val client = ComposeLayoutInspectorClient.launch(apiServices, processDescriptor, model(projectRule.project) {}, mock(), capabilities,
                                                     mock())
    assertThat(client).isNotNull()

    // TODO: probably we should add more checks to make sure the client is functional
  }

  @Test
  fun inspectorArtifactNotFound_showUseSnapshotBanner() = runBlocking {
    adbRule.attachDevice(processDescriptor.device.serial, processDescriptor.device.manufacturer, processDescriptor.device.model,
                         processDescriptor.device.version, processDescriptor.device.apiLevel.toString(),
                         "arm64-v8a", emptyMap(), DeviceState.HostConnectionType.LOCAL, "myAvd", "/android/avds/myAvd")

    val artifactService = object : InspectorArtifactService {
      override suspend fun getOrResolveInspectorArtifact(artifactCoordinate: ArtifactCoordinate, project: Project): Path {
        throw AppInspectionArtifactNotFoundException("not found")
      }
    }
    ApplicationManager.getApplication().registerServiceInstance(InspectorArtifactService::class.java, artifactService)
    val target = mock<AppInspectionTarget>()
    whenever(target.getLibraryVersions(any())).thenReturn(listOf(LibraryCompatbilityInfo(mock(), mock(), "1.0.0-SNAPSHOT", "")))
    val apiServices = mock<AppInspectionApiServices>()
    whenever(apiServices.attachToProcess(processDescriptor, projectRule.project.name)).thenReturn(target)
    val capabilities = EnumSet.noneOf(InspectorClient.Capability::class.java)

    val banner = InspectorBanner(projectRule.project)
    val client = ComposeLayoutInspectorClient.launch(apiServices, processDescriptor, model(projectRule.project) {}, mock(), capabilities,
                                                     mock())
    assertThat(client).isNull()

    invokeAndWaitIfNeeded { UIUtil.dispatchAllInvocationEvents() }
    assertThat(banner.text.text).isEqualTo(INSPECTOR_NOT_FOUND_USE_SNAPSHOT)
  }

  @Test
  fun inspectorArtifactNotFound_showComposeInspectionNotAvailableBanner() = runBlocking {
    adbRule.attachDevice(processDescriptor.device.serial, processDescriptor.device.manufacturer, processDescriptor.device.model,
                         processDescriptor.device.version, processDescriptor.device.apiLevel.toString(),
                         "arm64-v8a", emptyMap(), DeviceState.HostConnectionType.LOCAL, "myAvd", "/android/avds/myAvd")

    val artifactService = object : InspectorArtifactService {
      override suspend fun getOrResolveInspectorArtifact(artifactCoordinate: ArtifactCoordinate, project: Project): Path {
        throw AppInspectionArtifactNotFoundException("not found")
      }
    }
    ApplicationManager.getApplication().registerServiceInstance(InspectorArtifactService::class.java, artifactService)
    val target = mock<AppInspectionTarget>()
    whenever(target.getLibraryVersions(any())).thenReturn(listOf(LibraryCompatbilityInfo(mock(), mock(), "1.0.0", "")))
    val apiServices = mock<AppInspectionApiServices>()
    whenever(apiServices.attachToProcess(processDescriptor, projectRule.project.name)).thenReturn(target)
    val capabilities = EnumSet.noneOf(InspectorClient.Capability::class.java)

    val banner = InspectorBanner(projectRule.project)
    val client = ComposeLayoutInspectorClient.launch(apiServices, processDescriptor, model(projectRule.project) {}, mock(), capabilities,
                                                     mock())
    assertThat(client).isNull()

    invokeAndWaitIfNeeded { UIUtil.dispatchAllInvocationEvents() }
    assertThat(banner.text.text).isEqualTo(COMPOSE_INSPECTION_NOT_AVAILABLE)
  }
}