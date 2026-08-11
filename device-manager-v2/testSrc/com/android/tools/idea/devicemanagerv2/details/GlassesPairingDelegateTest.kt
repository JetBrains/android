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

import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceId
import com.android.sdklib.deviceprovisioner.DeviceProperties
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.sdklib.deviceprovisioner.EmptyIcon
import com.android.tools.idea.avd.glassespairing.GlassesPairingWizard
import com.android.tools.idea.deviceprovisioner.GlassesInteractivePairableDeviceHandle
import com.android.tools.idea.flags.StudioFlags
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ApplicationRule
import java.awt.Component
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class GlassesPairingDelegateTest {
  @get:Rule val applicationRule = ApplicationRule()

  private lateinit var project: Project
  private lateinit var parentComponent: Component
  private lateinit var delegate: GlassesPairingDelegate

  @Before
  fun setUp() {
    project = mock(Project::class.java)
    parentComponent = mock(Component::class.java)
    delegate = GlassesPairingDelegate(project, emptyFlow())
    StudioFlags.AI_GLASSES_PHONE_EMULATOR_PAIRING_WIZARD_ENABLED.override(true)
  }

  @After
  fun tearDown() {
    GlassesPairingWizard.resetForTesting()
    StudioFlags.AI_GLASSES_PHONE_EMULATOR_PAIRING_WIZARD_ENABLED.clearOverride()
  }

  private fun createMockGlassesHandle(pairedPhoneId: DeviceId?, isEnabled: Boolean = true): GlassesInteractivePairableDeviceHandle {
    val handle = mock(GlassesInteractivePairableDeviceHandle::class.java)
    val properties =
      DeviceProperties.buildForTest {
        model = "glasses"
        deviceType = DeviceType.AI_GLASSES
        icon = EmptyIcon.DEFAULT
        this.pairedPhoneId = pairedPhoneId
      }
    val state = DeviceState.Disconnected(properties)
    whenever(handle.stateFlow).thenReturn(MutableStateFlow(state))
    whenever(handle.state).thenReturn(state)
    whenever(handle.id).thenReturn(DeviceId("Fake", false, "glasses1"))
    whenever(handle.isPairGlassesEnabled()).thenReturn(isEnabled)
    return handle
  }

  private fun createMockPhoneHandle(isEnabled: Boolean = true): GlassesInteractivePairableDeviceHandle {
    val handle = mock(GlassesInteractivePairableDeviceHandle::class.java)
    val properties =
      DeviceProperties.buildForTest {
        model = "phone"
        deviceType = DeviceType.HANDHELD
        icon = EmptyIcon.DEFAULT
      }
    val state = DeviceState.Disconnected(properties)
    whenever(handle.stateFlow).thenReturn(MutableStateFlow(state))
    whenever(handle.state).thenReturn(state)
    whenever(handle.id).thenReturn(DeviceId("Fake", false, "phone1"))
    whenever(handle.isPairGlassesEnabled()).thenReturn(isEnabled)
    return handle
  }

  @Test
  fun showPairDeviceWizard_glasses_launchesWizard() = runTest {
    val handle = createMockGlassesHandle(pairedPhoneId = null)

    delegate.showPairDeviceWizard(parentComponent, handle)
    verify(handle).pairGlasses(parentComponent, project)
  }

  @Test
  fun showPairDeviceWizard_phone_launchesWizard() = runTest {
    val handle = mock(GlassesInteractivePairableDeviceHandle::class.java)
    val properties =
      DeviceProperties.buildForTest {
        model = "phone"
        deviceType = DeviceType.HANDHELD
        icon = EmptyIcon.DEFAULT
      }
    val state = DeviceState.Disconnected(properties)
    whenever(handle.stateFlow).thenReturn(MutableStateFlow(state))
    whenever(handle.state).thenReturn(state)
    whenever(handle.id).thenReturn(DeviceId("Fake", false, "phone1"))

    delegate.showPairDeviceWizard(parentComponent, handle)
    verify(handle).pairGlasses(parentComponent, project)
  }

  @Test
  fun isPairDeviceWizardSupported_glassesHandle_enabled() {
    val handle = createMockGlassesHandle(pairedPhoneId = null, isEnabled = true)
    assertThat(delegate.isPairDeviceWizardSupported(handle)).isTrue()
  }

  @Test
  fun isPairDeviceWizardSupported_glassesHandle_disabled() {
    val handle = createMockGlassesHandle(pairedPhoneId = null, isEnabled = false)
    assertThat(delegate.isPairDeviceWizardSupported(handle)).isFalse()
  }

  @Test
  fun isPairDeviceWizardSupported_phoneHandle_enabled() {
    val handle = createMockPhoneHandle(isEnabled = true)
    assertThat(delegate.isPairDeviceWizardSupported(handle)).isTrue()
  }

  @Test
  fun isPairDeviceWizardSupported_phoneHandle_disabled() {
    val handle = createMockPhoneHandle(isEnabled = false)
    assertThat(delegate.isPairDeviceWizardSupported(handle)).isFalse()
  }

  @Test
  fun isPairDeviceWizardSupported_phoneHandle_flagDisabled() {
    StudioFlags.AI_GLASSES_PHONE_EMULATOR_PAIRING_WIZARD_ENABLED.override(false)
    val handle = createMockPhoneHandle(isEnabled = true)
    assertThat(delegate.isPairDeviceWizardSupported(handle)).isFalse()
  }

  @Test
  fun isPairDeviceWizardSupported_nonInteractiveHandle() {
    val handle = mock(DeviceHandle::class.java)
    val state =
      DeviceState.Disconnected(
        DeviceProperties.buildForTest {
          deviceType = DeviceType.HANDHELD
          icon = EmptyIcon.DEFAULT
        }
      )
    whenever(handle.state).thenReturn(state)
    assertThat(delegate.isPairDeviceWizardSupported(handle)).isFalse()
  }

  @Test
  fun isPairDeviceWizardSupported_otherDeviceType() {
    val handle = mock(GlassesInteractivePairableDeviceHandle::class.java)
    val state =
      DeviceState.Disconnected(
        DeviceProperties.buildForTest {
          deviceType = DeviceType.WEAR
          icon = EmptyIcon.DEFAULT
        }
      )
    whenever(handle.state).thenReturn(state)
    assertThat(delegate.isPairDeviceWizardSupported(handle)).isFalse()
  }

  @Test
  fun isDelegateForDevice_glassesDevice() = runTest {
    val handle = mock(DeviceHandle::class.java)
    val phoneProperties =
      DeviceProperties.buildForTest {
        model = "phone"
        deviceType = DeviceType.HANDHELD
        icon = EmptyIcon.DEFAULT
      }
    whenever(handle.state).thenReturn(DeviceState.Disconnected(phoneProperties))

    val pairedDevice = mock(DeviceHandle::class.java)
    val glassesProperties =
      DeviceProperties.buildForTest {
        model = "glasses"
        deviceType = DeviceType.AI_GLASSES
        icon = EmptyIcon.DEFAULT
      }
    whenever(pairedDevice.state).thenReturn(DeviceState.Disconnected(glassesProperties))

    assertThat(delegate.isDelegateForDevice(handle, pairedDevice)).isTrue()
    assertThat(delegate.isDelegateForDevice(pairedDevice, handle)).isTrue()
  }

  @Test
  fun isDelegateForDevice_neitherGlasses() = runTest {
    val handle = mock(DeviceHandle::class.java)
    val phoneProperties1 =
      DeviceProperties.buildForTest {
        model = "phone"
        deviceType = DeviceType.HANDHELD
        icon = EmptyIcon.DEFAULT
      }
    whenever(handle.state).thenReturn(DeviceState.Disconnected(phoneProperties1))

    val pairedDevice = mock(DeviceHandle::class.java)
    val phoneProperties2 =
      DeviceProperties.buildForTest {
        model = "phone"
        deviceType = DeviceType.HANDHELD
        icon = EmptyIcon.DEFAULT
      }
    whenever(pairedDevice.state).thenReturn(DeviceState.Disconnected(phoneProperties2))

    assertThat(delegate.isDelegateForDevice(handle, pairedDevice)).isFalse()
  }
}
