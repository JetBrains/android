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
import com.android.sdklib.deviceprovisioner.AbstractAvdScanner
import com.android.sdklib.deviceprovisioner.DeviceAction
import com.android.sdklib.deviceprovisioner.DeviceProvisioner
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.sdklib.deviceprovisioner.FakeAvdManager
import com.android.sdklib.deviceprovisioner.FakeAvdScanner
import com.android.sdklib.deviceprovisioner.LocalEmulatorDeviceHandle
import com.android.sdklib.deviceprovisioner.LocalEmulatorProperties
import com.android.sdklib.deviceprovisioner.makeAvdInfo
import com.android.sdklib.deviceprovisioner.testContext
import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.internal.avd.AvdInfo.AvdStatus
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.testutils.file.createInMemoryFileSystemAndFolder
import com.android.tools.idea.avdmanager.AvdManagerConnection
import com.android.tools.idea.deviceprovisioner.DeviceProvisionerService
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.testing.TemporaryDirectoryRule
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.replaceService
import icons.StudioIcons
import java.nio.file.Files
import javax.swing.Icon
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

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
        .create(session.scope, session, projectRule.project, avdScanner = FakeAvdScanner(avdManager, session.scope))
        as StudioLocalEmulatorProvisionerPlugin
    provisioner = DeviceProvisioner.create(session.scope, session, listOf(plugin))

    val mockService: DeviceProvisionerService = mock()
    whenever(mockService.deviceProvisioner).thenReturn(provisioner)
    projectRule.project.replaceService(DeviceProvisionerService::class.java, mockService, projectRule.project)

    setupMockAndroidSdks()
  }

  // Setup to facilitate AvdManagerConnection.setConnectionFactory during testing.
  private fun setupMockAndroidSdks() {
    val mockAndroidSdks = mock<AndroidSdks>()
    val mockSdkHandler = mock<AndroidSdkHandler>()
    val sdkLocation = temporaryDirectoryRule.newPath()
    whenever(mockSdkHandler.location).thenReturn(sdkLocation)
    whenever(mockAndroidSdks.tryToChooseSdkHandler()).thenReturn(mockSdkHandler)
    ApplicationManager.getApplication().replaceService(AndroidSdks::class.java, mockAndroidSdks, projectRule.project)
  }

  @After
  fun tearDown() {
    avdManager.close()
    session.close()
    AvdManagerConnection.resetConnectionFactory()
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
    validateIcon(avdManager.makeAvdInfo(2, tag = SystemImageTags.GOOGLE_TV_TAG), StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_TV)
    validateIcon(avdManager.makeAvdInfo(3, tag = SystemImageTags.WEAR_TAG), StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_WEAR)
    validateIcon(avdManager.makeAvdInfo(4, tag = SystemImageTags.AUTOMOTIVE_TAG), StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_CAR)
    validateIcon(avdManager.makeAvdInfo(5, tag = SystemImageTags.XR_HEADSET_TAG), StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_HEADSET)
  }

  /** Verify that DeviceActions are implemented as fields rather than via getters. */
  @Test
  fun actionPresentationIdentity() = runTest {
    val handleScope = this.createChildScope()
    val handle =
      StudioLocalEmulatorDeviceHandle(
        null,
        baseDeviceHandle =
          LocalEmulatorDeviceHandle(
            context = testContext(this),
            avdScanner = NullAvdScanner(handleScope),
            scope = handleScope,
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
          assertWithMessage("${property.name}.presentation").that(action.presentation).isSameAs(action.presentation)
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

  @Test
  fun testWipeDataUnpairsCompanions() = runBlockingWithTimeout {
    avdManager.createAvd(makeAvdInfo(temporaryDirectoryRule.newPath(), 1))
    avdManager.createAvd(makeAvdInfo(temporaryDirectoryRule.newPath(), 2, tag = SystemImageTags.AI_GLASSES_TAG))

    val mockConnection = mock<AvdManagerConnection>().apply { whenever(wipeUserData(any())).thenReturn(true) }
    AvdManagerConnection.setConnectionFactory { _, _ -> mockConnection }
    plugin.refreshDevices()
    yieldUntil { provisioner.devices.value.size == 2 }

    val phoneHandle =
      provisioner.devices.value.find { it.state.properties.deviceType == DeviceType.HANDHELD } as StudioLocalEmulatorDeviceHandle
    val glassesHandle =
      provisioner.devices.value.find { it.state.properties.deviceType == DeviceType.AI_GLASSES } as StudioLocalEmulatorDeviceHandle

    val phoneBase = phoneHandle.baseDeviceHandle as LocalEmulatorDeviceHandle
    val glassesBase = glassesHandle.baseDeviceHandle as LocalEmulatorDeviceHandle

    Files.createDirectories((phoneHandle.state.properties as LocalEmulatorProperties).avdPath)
    Files.createDirectories((glassesHandle.state.properties as LocalEmulatorProperties).avdPath)

    fun getPairedGlassesInfos(handle: StudioLocalEmulatorDeviceHandle) =
      (handle.state.properties as LocalEmulatorProperties).pairedGlassesInfos

    // Set up pairing on disk
    phoneBase.addPairedGlasses(glassesBase.id, null)
    glassesBase.updatePairedPhone(phoneBase)

    // FakeAvdScanner does not re-read from disk, so we manually mock the memory state
    val phoneInfo = avdManager.avds[0]
    val glassesInfo = avdManager.avds[1]
    val phoneAvdPath = (phoneHandle.state.properties as LocalEmulatorProperties).avdPath
    val glassesAvdPath = (glassesHandle.state.properties as LocalEmulatorProperties).avdPath

    avdManager.avdEditor = {
      if (it == phoneInfo) it.copy(userSettings = it.userSettings + ("paired.glasses.avd.id.1" to glassesHandle.id.toString()))
      else if (it == glassesInfo) it.copy(userSettings = it.userSettings + ("paired.phone.avd" to phoneHandle.id.toString())) else it
    }
    avdManager.editAvd(phoneInfo)
    avdManager.editAvd(glassesInfo)
    avdManager.avdEditor = { it } // Reset editor so unpairing writes succeed
    plugin.refreshDevices()

    // Ensure memory property is resolved
    yieldUntil { getPairedGlassesInfos(phoneHandle).isNotEmpty() }

    phoneHandle.wipeDataAction.wipeData()

    // The mock avdManager ignores refreshDevices disk updates, so we check disk files directly
    yieldUntil {
      val settings = Files.readAllLines(phoneAvdPath.resolve("user-settings.ini"))
      !settings.any { it.startsWith("paired.glasses.avd.id") }
    }
    yieldUntil {
      val settings = Files.readAllLines(glassesAvdPath.resolve("user-settings.ini"))
      !settings.any { it.startsWith("paired.phone.avd") }
    }
  }

  @Test
  fun testDeleteActionUnpairsCompanions() = runBlockingWithTimeout {
    avdManager.createAvd(makeAvdInfo(temporaryDirectoryRule.newPath(), 1))
    avdManager.createAvd(makeAvdInfo(temporaryDirectoryRule.newPath(), 2, tag = SystemImageTags.AI_GLASSES_TAG))

    val mockDeleteConnection = mock<AvdManagerConnection>().apply { whenever(deleteAvd(any())).thenReturn(true) }
    AvdManagerConnection.setConnectionFactory { _, _ -> mockDeleteConnection }
    plugin.refreshDevices()
    yieldUntil { provisioner.devices.value.size == 2 }

    val phoneHandle =
      provisioner.devices.value.find { it.state.properties.deviceType == DeviceType.HANDHELD } as StudioLocalEmulatorDeviceHandle
    val glassesHandle =
      provisioner.devices.value.find { it.state.properties.deviceType == DeviceType.AI_GLASSES } as StudioLocalEmulatorDeviceHandle

    val phoneBase = phoneHandle.baseDeviceHandle as LocalEmulatorDeviceHandle
    val glassesBase = glassesHandle.baseDeviceHandle as LocalEmulatorDeviceHandle

    Files.createDirectories((phoneHandle.state.properties as LocalEmulatorProperties).avdPath)
    Files.createDirectories((glassesHandle.state.properties as LocalEmulatorProperties).avdPath)

    fun getPairedGlassesInfos(handle: StudioLocalEmulatorDeviceHandle) =
      (handle.state.properties as LocalEmulatorProperties).pairedGlassesInfos

    // Set up pairing on disk
    phoneBase.addPairedGlasses(glassesBase.id, null)
    glassesBase.updatePairedPhone(phoneBase)

    // FakeAvdScanner does not re-read from disk, so we manually mock the memory state
    val phoneInfo = avdManager.avds[0]
    val glassesInfo = avdManager.avds[1]
    val glassesAvdPath = (glassesHandle.state.properties as LocalEmulatorProperties).avdPath

    avdManager.avdEditor = {
      if (it == phoneInfo) it.copy(userSettings = it.userSettings + ("paired.glasses.avd.id.1" to glassesHandle.id.toString()))
      else if (it == glassesInfo) it.copy(userSettings = it.userSettings + ("paired.phone.avd" to phoneHandle.id.toString())) else it
    }
    avdManager.editAvd(phoneInfo)
    avdManager.editAvd(glassesInfo)
    avdManager.avdEditor = { it } // Reset editor so unpairing writes succeed
    plugin.refreshDevices()

    yieldUntil { getPairedGlassesInfos(phoneHandle).isNotEmpty() }

    phoneHandle.deleteAction.delete()

    // Check disk file to verify glasses was unpaired
    yieldUntil {
      val settings = Files.readAllLines(glassesAvdPath.resolve("user-settings.ini"))
      !settings.any { it.startsWith("paired.phone.avd") }
    }
  }

  @Test
  fun testActionFailureIgnoresCompanions() = runBlockingWithTimeout {
    avdManager.createAvd(makeAvdInfo(temporaryDirectoryRule.newPath(), 1))
    avdManager.createAvd(makeAvdInfo(temporaryDirectoryRule.newPath(), 2, tag = SystemImageTags.AI_GLASSES_TAG))

    val mockConnection = mock<AvdManagerConnection>().apply { whenever(wipeUserData(any())).thenReturn(false) }
    AvdManagerConnection.setConnectionFactory { _, _ -> mockConnection }

    plugin.refreshDevices()
    yieldUntil { provisioner.devices.value.size == 2 }

    val phoneHandle =
      provisioner.devices.value.find { it.state.properties.deviceType == DeviceType.HANDHELD } as StudioLocalEmulatorDeviceHandle
    val glassesHandle =
      provisioner.devices.value.find { it.state.properties.deviceType == DeviceType.AI_GLASSES } as StudioLocalEmulatorDeviceHandle

    val phoneBase = phoneHandle.baseDeviceHandle as LocalEmulatorDeviceHandle
    val glassesBase = glassesHandle.baseDeviceHandle as LocalEmulatorDeviceHandle

    Files.createDirectories((phoneHandle.state.properties as LocalEmulatorProperties).avdPath)
    Files.createDirectories((glassesHandle.state.properties as LocalEmulatorProperties).avdPath)

    fun getPairedGlassesInfos(handle: StudioLocalEmulatorDeviceHandle) =
      (handle.state.properties as LocalEmulatorProperties).pairedGlassesInfos

    // Set up pairing on disk
    phoneBase.addPairedGlasses(glassesBase.id, null)
    glassesBase.updatePairedPhone(phoneBase)

    // FakeAvdScanner does not re-read from disk, so we manually mock the memory state
    val phoneInfo = avdManager.avds[0]
    val glassesInfo = avdManager.avds[1]
    val phoneAvdPath = (phoneHandle.state.properties as LocalEmulatorProperties).avdPath

    avdManager.avdEditor = {
      if (it == phoneInfo) it.copy(userSettings = it.userSettings + ("paired.glasses.avd.id.1" to glassesHandle.id.toString()))
      else if (it == glassesInfo) it.copy(userSettings = it.userSettings + ("paired.phone.avd" to phoneHandle.id.toString())) else it
    }
    avdManager.editAvd(phoneInfo)
    avdManager.editAvd(glassesInfo)
    avdManager.avdEditor = { it } // Reset editor so unpairing writes succeed
    plugin.refreshDevices()

    yieldUntil { getPairedGlassesInfos(phoneHandle).isNotEmpty() }

    val previousDialog = TestDialogManager.setTestDialog(TestDialog.OK)
    try {
      phoneHandle.wipeDataAction.wipeData()
    } finally {
      TestDialogManager.setTestDialog(previousDialog)
    }

    // Assert still paired
    val phoneSettings = Files.readAllLines(phoneAvdPath.resolve("user-settings.ini"))
    assertThat(phoneSettings.any { it.startsWith("paired.glasses.avd.id") }).isTrue()
    assertThat(glassesHandle.state.properties.pairedPhoneId).isEqualTo(phoneHandle.id)
  }
}

private class NullAvdScanner(coroutineScope: CoroutineScope) : AbstractAvdScanner(coroutineScope) {
  override fun scanAvds(): List<AvdInfo> = emptyList()

  override fun logError(message: String, exception: Throwable) = throw exception
}
