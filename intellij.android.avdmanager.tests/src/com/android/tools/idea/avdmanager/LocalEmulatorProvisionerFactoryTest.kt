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
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.testingutils.CoroutineTestUtils.yieldUntil
import com.android.sdklib.SystemImageTags
import com.android.sdklib.deviceprovisioner.DeviceProvisioner
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.sdklib.deviceprovisioner.FakeAvdManager
import com.android.sdklib.deviceprovisioner.LocalEmulatorProvisionerPlugin
import com.android.sdklib.internal.avd.AvdInfo
import com.android.tools.idea.deviceprovisioner.StudioDefaultDeviceIcons
import com.android.tools.idea.testing.TemporaryDirectoryRule
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ProjectRule
import icons.StudioIcons
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.swing.Icon

class LocalEmulatorProvisionerFactoryTest {
  @get:Rule val projectRule = ProjectRule()
  @get:Rule val temporaryDirectoryRule = TemporaryDirectoryRule()

  private val session = FakeAdbSession()
  private lateinit var avdManager: FakeAvdManager
  private lateinit var plugin: LocalEmulatorProvisionerPlugin
  private lateinit var provisioner: DeviceProvisioner

  @Before
  fun setUp() {
    avdManager = FakeAvdManager(session, temporaryDirectoryRule.newPath())
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
  fun testIcons(): Unit = runBlockingWithTimeout {
    suspend fun validateIcon(avdInfo: AvdInfo, icon: Icon) {
      println("e")
      avdManager.createAvd(avdInfo)
      plugin.refreshDevices()
      yieldUntil { provisioner.devices.value.size == 1 }

      val handle = provisioner.devices.value[0]
      handle.activationAction?.activate()
      assertThat(handle.state.properties.icon).isEqualTo(icon)

      handle.deactivationAction?.deactivate()
      yieldUntil { handle.state is DeviceState.Disconnected }
      handle.deleteAction?.delete()
      yieldUntil { provisioner.devices.value.isEmpty() }
    }
    validateIcon(avdManager.makeAvdInfo(1), StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE)
    validateIcon(
      avdManager.makeAvdInfo(2, tag = SystemImageTags.GOOGLE_TV_TAG),
      StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_TV
    )
    validateIcon(
      avdManager.makeAvdInfo(3, tag = SystemImageTags.WEAR_TAG),
      StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_WEAR
    )
    validateIcon(
      avdManager.makeAvdInfo(4, tag = SystemImageTags.AUTOMOTIVE_TAG),
      StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_CAR
    )
  }
}
