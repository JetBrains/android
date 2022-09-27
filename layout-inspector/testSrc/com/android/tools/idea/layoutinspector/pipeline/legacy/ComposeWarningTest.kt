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
package com.android.tools.idea.layoutinspector.pipeline.legacy

import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.layoutinspector.LEGACY_DEVICE
import com.android.tools.idea.layoutinspector.MODERN_DEVICE
import com.android.tools.idea.layoutinspector.createProcess
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.StatusNotification
import com.android.tools.idea.layoutinspector.ui.InspectorBannerService
import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.project.DefaultProjectSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

class ComposeWarningTest {
  private lateinit var model: InspectorModel

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory().initAndroid(true)

  private val moduleSystem: DefaultModuleSystem
    get() = projectRule.module.getModuleSystem() as DefaultModuleSystem

  private var lastNotification: StatusNotification? = null
  private lateinit var composeWarning: ComposeWarning

  @Before
  fun before() {
    model = model(projectRule.project) {}
    projectRule.fixture.addFileToProject("/AndroidManifest.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.example.app">
        <application />
      </manifest>
    """.trimIndent())
    val projectSystem = projectRule.project.getProjectSystem() as DefaultProjectSystem
    val moduleSystem = Mockito.spy(DefaultModuleSystem(projectRule.module))
    projectSystem.setModuleSystem(moduleSystem.module, moduleSystem)
    moduleSystem.usesCompose = true
    InspectorBannerService.getInstance(projectRule.project)?.notificationListeners?.add { lastNotification = it }
    composeWarning = ComposeWarning(projectRule.project)
  }

  @Test
  fun testNonComposeApp() {
    moduleSystem.usesCompose = false
    composeWarning.performCheck(createClientFor(LEGACY_DEVICE, "com.example.app"))
    // We are not using compose so do NOT show a warning:
    assertThat(lastNotification).isNull()
  }

  @Test
  fun testComposeFromDifferentApp() {
    composeWarning.performCheck(createClientFor(LEGACY_DEVICE, "com.example.other"))
    // We cannot tell if the other app is a compose app so do NOT show warning:
    assertThat(lastNotification).isNull()
  }

  @Test
  fun testComposeAppWithLegacyApiLevel() {
    composeWarning.performCheck(createClientFor(LEGACY_DEVICE, "com.example.app"))
    assertThat(lastNotification?.message).isEqualTo("To see compose nodes in the inspector please use a device with API >= 29")
  }

  @Test
  fun testComposeAppWithLegacyDeviceWithModernApiLevel() {
    composeWarning.performCheck(createClientFor(MODERN_DEVICE, "com.example.app"))
    assertThat(lastNotification?.message).isEqualTo("Cannot display compose nodes, try restarting the application")
  }

  private fun createClientFor(device: DeviceDescriptor, appName: String): LegacyClient {
    val client: LegacyClient = mock()
    whenever(client.process).thenReturn(device.createProcess(appName))
    return client
  }
}
