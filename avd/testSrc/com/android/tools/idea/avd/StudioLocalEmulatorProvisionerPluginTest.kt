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
package com.android.tools.idea.avd

import com.android.adblib.testing.FakeAdbSession
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.testingutils.CoroutineTestUtils.yieldUntil
import com.android.adblib.utils.createChildScope
import com.android.sdklib.SystemImageTags
import com.android.sdklib.deviceprovisioner.DeviceAction
import com.android.sdklib.deviceprovisioner.DeviceProvisioner
import com.android.sdklib.deviceprovisioner.FakeAvdManager
import com.android.sdklib.deviceprovisioner.LocalEmulatorDeviceHandle
import com.android.sdklib.deviceprovisioner.makeAvdInfo
import com.android.sdklib.deviceprovisioner.testContext
import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.internal.avd.AvdInfo.AvdStatus
import com.android.testutils.file.createInMemoryFileSystemAndFolder
import com.android.tools.idea.testing.TemporaryDirectoryRule
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.intellij.testFramework.ProjectRule
import icons.StudioIcons
import javax.swing.Icon
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberProperties
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class StudioLocalEmulatorProvisionerPluginTest {
  @get:Rule val projectRule = ProjectRule()
  @get:Rule val temporaryDirectoryRule = TemporaryDirectoryRule()

  private val session = FakeAdbSession()
  private lateinit var avdManager: FakeAvdManager
  private lateinit var plugin: StudioLocalEmulatorProvisionerPlugin
  private lateinit var provisioner: DeviceProvisioner

  @Before
  fun setUp() {
    avdManager = FakeAvdManager(session, temporaryDirectoryRule.newPath())
    plugin =
      LocalEmulatorProvisionerFactory()
        .create(session.scope, session, projectRule.project, avdManager::rescanAvds)
        as StudioLocalEmulatorProvisionerPlugin
    provisioner = DeviceProvisioner.create(session.scope, session, listOf(plugin))
  }

  @After
  fun tearDown() {
    avdManager.close()
    session.close()
  }

  @Test
  fun testIcons(): Unit = runBlockingWithTimeout {
    suspend fun validateIcon(avdInfo: AvdInfo, icon: Icon) {
      avdManager.createAvd(avdInfo)
      plugin.refreshDevices()
      yieldUntil { provisioner.devices.value.size == 1 }

      val handle = provisioner.devices.value[0]
      assertThat(handle.state.properties.icon).isEqualTo(icon)

      avdManager.deleteAvd(avdInfo)
      plugin.refreshDevices()
      yieldUntil { provisioner.devices.value.size == 0 }
    }
    validateIcon(avdManager.makeAvdInfo(1), StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE)
    validateIcon(
      avdManager.makeAvdInfo(2, tag = SystemImageTags.GOOGLE_TV_TAG),
      StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_TV,
    )
    validateIcon(
      avdManager.makeAvdInfo(3, tag = SystemImageTags.WEAR_TAG),
      StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_WEAR,
    )
    validateIcon(
      avdManager.makeAvdInfo(4, tag = SystemImageTags.AUTOMOTIVE_TAG),
      StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_CAR,
    )
    validateIcon(
      avdManager.makeAvdInfo(5, tag = SystemImageTags.XR_HEADSET_TAG),
      StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_HEADSET,
    )
  }

  /** Verify that DeviceActions are implemented as fields rather than via getters. */
  @Test
  fun actionPresentationIdentity() = runTest {
    val handle =
      StudioLocalEmulatorDeviceHandle(
        null,
        baseDeviceHandle =
          LocalEmulatorDeviceHandle(
            context = testContext(this),
            refreshDevices = {},
            scope = this.createChildScope(),
            extensions = emptyList(),
            initialAvdInfo = makeAvdInfo(createInMemoryFileSystemAndFolder("avds"), 1),
          ),
        context = testContext(this),
        deviceHandleFlow = MutableStateFlow(emptyList()),
      )

    for (property in StudioLocalEmulatorDeviceHandle::class.memberProperties) {
      val classType = property.returnType.classifier as? KClass<*> ?: continue
      if (classType.isSubclassOf(DeviceAction::class)) {
        val action = property.getter.call(handle) as? DeviceAction
        assertWithMessage(property.name).that(action).isSameAs(property.getter.call(handle))
        if (action != null) {
          assertWithMessage("${property.name}.presentation")
            .that(action.presentation)
            .isSameAs(action.presentation)
        }
      }
    }

    handle.scope.cancel()
  }

  @Test
  fun deviceWrapping(): Unit = runBlockingWithTimeout {
    avdManager.createAvd()

    plugin.refreshDevices()
    yieldUntil { provisioner.devices.value.size == 1 }
    val handle = provisioner.devices.value[0]

    avdManager.createAvd()

    plugin.refreshDevices()
    yieldUntil { provisioner.devices.value.size == 2 }
    val handles = provisioner.devices.value

    // We shouldn't re-wrap the original handle
    assertThat(handles).contains(handle)

    avdManager.deleteAvd(avdManager.avds[0])

    plugin.refreshDevices()
    yieldUntil { provisioner.devices.value.size == 1 }

    assertThat(provisioner.devices.value).containsExactlyElementsIn(handles - handle)
  }

  @Test
  fun isActivatable() = runBlockingWithTimeout {
    avdManager.createAvd()
    plugin.refreshDevices()

    yieldUntil { provisioner.devices.value.size == 1 }

    val handle = provisioner.devices.value[0]
    val activationAction = handle.activationAction!!

    activationAction.presentation.takeWhile { !it.enabled }.collect()

    avdManager.avds[0] = avdManager.makeAvdInfo(1, avdStatus = AvdStatus.ERROR_IMAGE_MISSING)
    plugin.refreshDevices()

    // The action should become disabled.
    yieldUntil { activationAction.presentation.value.enabled == false }
  }
}
