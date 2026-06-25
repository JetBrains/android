/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.devicemanagerv2.details

import com.android.adblib.utils.createChildScope
import com.android.sdklib.deviceprovisioner.DeviceId
import com.android.sdklib.deviceprovisioner.DeviceProperties
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.idea.devicemanagerv2.FakeDeviceHandle
import com.android.tools.idea.devicemanagerv2.FakeDeviceTemplate
import com.android.tools.idea.flags.StudioFlags
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ApplicationRule
import icons.StudioIcons
import java.awt.Component
import javax.swing.JTabbedPane
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceDetailsPanelTest {
  @get:Rule val applicationRule = ApplicationRule()

  private lateinit var project: Project

  @Before
  fun setUp() {
    project = mock(Project::class.java)
  }

  private fun createPhoneHandle(wearPairingId: String? = null) = runBlocking {
    val properties =
      DeviceProperties.buildForTest {
        model = "phone"
        deviceType = DeviceType.HANDHELD
        icon = StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE
        this.wearPairingId = wearPairingId
      }
    FakeDeviceHandle(this, FakeDeviceTemplate(properties), properties)
  }

  private fun createGlassesHandle() = runBlocking {
    val properties =
      DeviceProperties.buildForTest {
        model = "glasses"
        deviceType = DeviceType.AI_GLASSES
        icon = StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE
        pairedPhoneId = DeviceId("Fake", false, "phone1")
      }
    FakeDeviceHandle(this, FakeDeviceTemplate(properties), properties)
  }

  @Test
  fun testPhone_NoWear_NoGlasses() = runTest {
    val handle = createPhoneHandle(wearPairingId = null)
    StudioFlags.AI_GLASSES_PAIRING_DETAILS_ENABLED.override(false)
    val panelScope = createChildScope()
    var panel: DeviceDetailsPanel? = null
    try {
      ApplicationManager.getApplication().invokeAndWait {
        panel = DeviceDetailsPanel.create(project, panelScope, handle, emptyFlow(), emptyFlow())
      }
      val tabbedPane = findTabbedPane(panel!!)
      assertThat(tabbedPane).isNull()
    } finally {
      panel?.let { Disposer.dispose(it) }
      StudioFlags.AI_GLASSES_PAIRING_DETAILS_ENABLED.clearOverride()
    }
  }

  @Test
  fun testPhone_WithWear_NoGlasses() = runTest {
    val handle = createPhoneHandle(wearPairingId = "wear1")
    StudioFlags.AI_GLASSES_PAIRING_DETAILS_ENABLED.override(false)
    val panelScope = createChildScope()
    var panel: DeviceDetailsPanel? = null
    try {
      ApplicationManager.getApplication().invokeAndWait {
        panel = DeviceDetailsPanel.create(project, panelScope, handle, emptyFlow(), emptyFlow())
      }
      val tabbedPane = findTabbedPane(panel!!)
      assertThat(tabbedPane).isNotNull()
      assertThat(tabbedPane!!.tabCount).isEqualTo(2)
      assertThat(tabbedPane.getTitleAt(0)).isEqualTo("Device Info")
      assertThat(tabbedPane.getTitleAt(1)).isEqualTo("Paired Devices")
    } finally {
      panel?.let { Disposer.dispose(it) }
      StudioFlags.AI_GLASSES_PAIRING_DETAILS_ENABLED.clearOverride()
    }
  }

  @Test
  fun testPhone_NoWear_WithGlasses() = runTest {
    val handle = createPhoneHandle(wearPairingId = null)
    StudioFlags.AI_GLASSES_PAIRING_DETAILS_ENABLED.override(true)
    val panelScope = createChildScope()
    var panel: DeviceDetailsPanel? = null
    try {
      ApplicationManager.getApplication().invokeAndWait {
        panel = DeviceDetailsPanel.create(project, panelScope, handle, emptyFlow(), emptyFlow())
      }
      val tabbedPane = findTabbedPane(panel!!)
      assertThat(tabbedPane).isNotNull()
      assertThat(tabbedPane!!.tabCount).isEqualTo(2)
      assertThat(tabbedPane.getTitleAt(0)).isEqualTo("Device Info")
      assertThat(tabbedPane.getTitleAt(1)).isEqualTo("Paired Devices")
    } finally {
      panel?.let { Disposer.dispose(it) }
      StudioFlags.AI_GLASSES_PAIRING_DETAILS_ENABLED.clearOverride()
    }
  }

  @Test
  fun testPhone_GlassesPairingDetailsEnabled_WizardDisabled_NoPairedGlasses() = runTest {
    val handle = createPhoneHandle(wearPairingId = null)
    assertThat(handle.state.properties.pairedGlassesInfos).isEmpty()
    StudioFlags.AI_GLASSES_PAIRING_DETAILS_ENABLED.override(true)
    StudioFlags.AI_GLASSES_PHONE_EMULATOR_PAIRING_WIZARD_ENABLED.override(false)
    val panelScope = createChildScope()
    var panel: DeviceDetailsPanel? = null
    try {
      ApplicationManager.getApplication().invokeAndWait {
        panel = DeviceDetailsPanel.create(project, panelScope, handle, emptyFlow(), emptyFlow())
      }
      val tabbedPane = findTabbedPane(panel!!)
      assertThat(tabbedPane).isNotNull()
      assertThat(tabbedPane!!.tabCount).isEqualTo(2)
      assertThat(tabbedPane.getTitleAt(0)).isEqualTo("Device Info")
      assertThat(tabbedPane.getTitleAt(1)).isEqualTo("Paired Devices")
    } finally {
      panel?.let { Disposer.dispose(it) }
      StudioFlags.AI_GLASSES_PAIRING_DETAILS_ENABLED.clearOverride()
      StudioFlags.AI_GLASSES_PHONE_EMULATOR_PAIRING_WIZARD_ENABLED.clearOverride()
    }
  }

  @Test
  fun testPhone_WithWear_WithGlasses() = runTest {
    val handle = createPhoneHandle(wearPairingId = "wear1")
    StudioFlags.AI_GLASSES_PAIRING_DETAILS_ENABLED.override(true)
    val panelScope = createChildScope()
    var panel: DeviceDetailsPanel? = null
    try {
      ApplicationManager.getApplication().invokeAndWait {
        panel = DeviceDetailsPanel.create(project, panelScope, handle, emptyFlow(), emptyFlow())
      }
      val tabbedPane = findTabbedPane(panel!!)
      assertThat(tabbedPane).isNotNull()
      assertThat(tabbedPane!!.tabCount).isEqualTo(2)
      assertThat(tabbedPane.getTitleAt(0)).isEqualTo("Device Info")
      assertThat(tabbedPane.getTitleAt(1)).isEqualTo("Paired Devices")
    } finally {
      panel?.let { Disposer.dispose(it) }
      StudioFlags.AI_GLASSES_PAIRING_DETAILS_ENABLED.clearOverride()
    }
  }

  @Test
  fun testGlasses_FlagEnabled() = runTest {
    val handle = createGlassesHandle()
    StudioFlags.AI_GLASSES_PAIRING_DETAILS_ENABLED.override(true)
    val panelScope = createChildScope()
    var panel: DeviceDetailsPanel? = null
    try {
      ApplicationManager.getApplication().invokeAndWait {
        panel = DeviceDetailsPanel.create(project, panelScope, handle, emptyFlow(), emptyFlow())
      }
      val tabbedPane = findTabbedPane(panel!!)
      assertThat(tabbedPane).isNotNull()
      assertThat(tabbedPane!!.tabCount).isEqualTo(2)
      assertThat(tabbedPane.getTitleAt(0)).isEqualTo("Device Info")
      assertThat(tabbedPane.getTitleAt(1)).isEqualTo("Paired Devices")
    } finally {
      panel?.let { Disposer.dispose(it) }
      StudioFlags.AI_GLASSES_PAIRING_DETAILS_ENABLED.clearOverride()
    }
  }

  @Test
  fun testGlasses_FlagDisabled() = runTest {
    val handle = createGlassesHandle()
    StudioFlags.AI_GLASSES_PAIRING_DETAILS_ENABLED.override(false)
    val panelScope = createChildScope()
    var panel: DeviceDetailsPanel? = null
    try {
      ApplicationManager.getApplication().invokeAndWait {
        panel = DeviceDetailsPanel.create(project, panelScope, handle, emptyFlow(), emptyFlow())
      }
      val tabbedPane = findTabbedPane(panel!!)
      assertThat(tabbedPane).isNull()
    } finally {
      panel?.let { Disposer.dispose(it) }
      StudioFlags.AI_GLASSES_PAIRING_DETAILS_ENABLED.clearOverride()
    }
  }

  private fun findTabbedPane(component: Component): JTabbedPane? {
    if (component is JTabbedPane) {
      return component
    }
    if (component is java.awt.Container) {
      for (child in component.components) {
        val found = findTabbedPane(child)
        if (found != null) {
          return found
        }
      }
    }
    return null
  }
}
