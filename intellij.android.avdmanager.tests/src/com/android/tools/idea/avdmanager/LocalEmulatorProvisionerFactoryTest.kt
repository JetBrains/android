/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.avdmanager

import com.android.adblib.testing.FakeAdbSession
import com.android.adblib.testingutils.CoroutineTestUtils
import com.android.sdklib.deviceprovisioner.DeviceProvisioner
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.sdklib.deviceprovisioner.FakeAvdManager
import com.android.sdklib.deviceprovisioner.LocalEmulatorProvisionerPlugin
import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.repository.targets.SystemImage
import com.android.tools.idea.deviceprovisioner.StudioDefaultDeviceIcons
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ProjectRule
import icons.StudioIcons
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.swing.Icon

class LocalEmulatorProvisionerFactoryTest {
  @get:Rule val projectRule = ProjectRule()

  private val session = FakeAdbSession()
  private val avdManager = FakeAvdManager(session)
  private lateinit var plugin: LocalEmulatorProvisionerPlugin
  private lateinit var provisioner: DeviceProvisioner

  @Before
  fun setUp() {
    plugin =
      LocalEmulatorProvisionerFactory()
        .create(session.scope, session, projectRule.project, avdManager)
        as LocalEmulatorProvisionerPlugin
    provisioner = DeviceProvisioner.create(session, listOf(plugin), StudioDefaultDeviceIcons)
  }

  @After
  fun tearDown() {
    avdManager.close()
    session.close()
  }

  @Test
  fun testIcons(): Unit = runBlocking {
    suspend fun validateIcon(avdInfo: AvdInfo, icon: Icon) {
      println("e")
      avdManager.createAvd(avdInfo)
      plugin.refreshDevices()
      CoroutineTestUtils.yieldUntil { provisioner.devices.value.size == 1 }

      val handle = provisioner.devices.value[0]
      handle.activationAction?.activate()
      assertThat(handle.state.properties.icon).isEqualTo(icon)

      handle.deactivationAction?.deactivate()
      CoroutineTestUtils.yieldUntil { handle.state is DeviceState.Disconnected }
      handle.deleteAction?.delete()
      CoroutineTestUtils.yieldUntil { provisioner.devices.value.isEmpty() }
    }
    validateIcon(avdManager.makeAvdInfo(1), StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE)
    validateIcon(
      avdManager.makeAvdInfo(2, tag = SystemImage.GOOGLE_TV_TAG),
      StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_TV
    )
    validateIcon(
      avdManager.makeAvdInfo(3, tag = SystemImage.WEAR_TAG),
      StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_WEAR
    )
    validateIcon(
      avdManager.makeAvdInfo(4, tag = SystemImage.AUTOMOTIVE_TAG),
      StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_CAR
    )
  }
}
