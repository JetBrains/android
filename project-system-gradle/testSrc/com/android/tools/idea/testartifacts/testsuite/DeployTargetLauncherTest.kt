/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.testsuite

import com.android.ddmlib.IDevice
import com.android.tools.idea.run.FakeAndroidDevice
import com.android.tools.idea.run.editor.DeployTarget
import com.android.tools.idea.run.editor.DeployTargetContext
import com.android.tools.idea.run.editor.DeployTargetProvider
import com.android.tools.idea.run.editor.DeployTargetState.DEFAULT_STATE
import com.intellij.testFramework.ProjectRule
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(JUnit4::class)
class DeployTargetLauncherTest {
  @get:Rule
  val projectRule = ProjectRule()

  val mockDevice: IDevice = mock()
  val mockDeployTarget: DeployTarget = mock()
  val mockDeployTargetProvider: DeployTargetProvider = mock()

  @Before
  fun setUp() {
    whenever(mockDevice.serialNumber).thenReturn("test-device-1")
    whenever(mockDeployTarget.launchDevices(projectRule.project)).thenReturn(FakeAndroidDevice.forDevices(listOf(mockDevice)))
    whenever(mockDeployTargetProvider.getDeployTarget(projectRule.project)).thenReturn(mockDeployTarget)
    whenever(mockDeployTargetProvider.requiresRuntimePrompt(projectRule.project)).thenReturn(false)
    whenever(mockDeployTargetProvider.getId()).thenReturn("DEVICE_AND_SNAPSHOT_COMBO_BOX")
    whenever(mockDeployTargetProvider.createState()).thenReturn(DEFAULT_STATE)
  }

  @Test
  fun testLaunchDevices_noDevicesLaunched() {
    val devices = launchDevices(projectRule.project, DeployTargetContext())
    assertTrue(devices.isEmpty())
  }

  @Test
  fun testLaunchDevices_singleDeviceLaunched() {
    val context = DeployTargetContext(listOf(mockDeployTargetProvider))
    val devices = launchDevices(projectRule.project, context)
    assertEquals(listOf("test-device-1"), devices)
  }

  @Test
  fun testLaunchDevices_multipleDevicesLaunched() {
    val mockDevice1 = mock(IDevice::class.java)
    whenever(mockDevice1.serialNumber).thenReturn("test-device-1")
    val mockDevice2 = mock(IDevice::class.java)
    whenever(mockDevice2.serialNumber).thenReturn("test-device-2")
    whenever(mockDeployTarget.launchDevices(projectRule.project)).thenReturn(
      FakeAndroidDevice.forDevices(listOf(mockDevice1, mockDevice2)))

    val context = DeployTargetContext(listOf(mockDeployTargetProvider))
    val devices = launchDevices(projectRule.project, context)
    assertEquals(listOf("test-device-1", "test-device-2"), devices)
  }

  @Test
  fun testLaunchDevices_showsRuntimePromptIfNeeded() {
    whenever(mockDeployTargetProvider.requiresRuntimePrompt(projectRule.project)).thenReturn(true)
    whenever(mockDeployTargetProvider.showPrompt(projectRule.project)).thenReturn(mockDeployTarget)

    val context = DeployTargetContext(listOf(mockDeployTargetProvider))
    val devices = launchDevices(projectRule.project, context)
    assertEquals(listOf("test-device-1"), devices)

    verify(mockDeployTargetProvider).showPrompt(projectRule.project)
  }
}