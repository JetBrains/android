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
import com.android.tools.idea.appinspection.api.AppInspectionApiServices
import com.android.tools.idea.appinspection.ide.InspectorArtifactService
import com.android.tools.idea.appinspection.inspector.api.launch.LibraryCompatbilityInfo
import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.internal.AppInspectionTarget
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.ComposeLayoutInspectorClient
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.registerServiceInstance
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import java.nio.file.Paths

class ComposeLayoutInspectorClientTest {
  @get:Rule
  val projectRule = ProjectRule()

  @get:Rule
  val adbRule = FakeAdbRule()

  @Test
  fun testClientCreation() = runBlocking {
    val processDescriptor = object : ProcessDescriptor {
      override val device = object: DeviceDescriptor {
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

    adbRule.attachDevice(processDescriptor.device.serial, processDescriptor.device.manufacturer, processDescriptor.device.model,
                         processDescriptor.device.version, processDescriptor.device.apiLevel.toString(),
                         DeviceState.HostConnectionType.LOCAL, "myAvd", "/android/avds/myAvd")

    val artifactService = mock<InspectorArtifactService>()
    `when`(artifactService.getOrResolveInspectorArtifact(any(), any())).thenReturn(Paths.get("/foo/bar"))
    ApplicationManager.getApplication().registerServiceInstance(InspectorArtifactService::class.java, artifactService)
    val apiServices = mock<AppInspectionApiServices>()
    `when`(apiServices.launchInspector(any())).thenReturn(mock())
    val target = mock<AppInspectionTarget>()
    `when`(target.getLibraryVersions(any())).thenReturn(listOf(LibraryCompatbilityInfo(mock(), mock(), "1", "")))
    `when`(apiServices.attachToProcess(processDescriptor, projectRule.project.name)).thenReturn(target)

    val client = ComposeLayoutInspectorClient.launch(apiServices, processDescriptor, model(projectRule.project) {}, mock())
    assertThat(client).isNotNull()

    // Reset the mock or else the project is leaked.
    Mockito.reset(artifactService)

    // TODO: probably we should add more checks to make sure the client is functional
  }
}