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
import com.android.flags.junit.FlagRule
import com.android.sdklib.SystemImageTags
import com.android.sdklib.deviceprovisioner.AbstractAvdScanner
import com.android.sdklib.deviceprovisioner.DeviceAction
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceId
import com.android.sdklib.deviceprovisioner.DeviceProvisioner
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.sdklib.deviceprovisioner.FakeAvdManager
import com.android.sdklib.deviceprovisioner.FakeAvdScanner
import com.android.sdklib.deviceprovisioner.LocalEmulatorDeviceHandle
import com.android.sdklib.deviceprovisioner.LocalEmulatorProperties
import com.android.sdklib.deviceprovisioner.LocalEmulatorProvisionerPlugin
import com.android.sdklib.deviceprovisioner.makeAvdInfo
import com.android.sdklib.deviceprovisioner.testContext
import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.internal.avd.AvdInfo.AvdStatus
import com.android.sdklib.internal.avd.UserSettingsKey
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.testutils.file.createInMemoryFileSystemAndFolder
import com.android.tools.idea.avd.glassespairing.GlassesPairingLockService
import com.android.tools.idea.avd.glassespairing.GlassesPairingResult
import com.android.tools.idea.avd.glassespairing.GlassesPairingWizard
import com.android.tools.idea.avd.glassespairing.WizardController
import com.android.tools.idea.avdmanager.AvdManagerConnection
import com.android.tools.idea.deviceprovisioner.DeviceProvisionerService
import com.android.tools.idea.flags.StudioFlags
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class StudioLocalEmulatorProvisionerPluginTest {
  @get:Rule val projectRule = ProjectRule()
  @get:Rule val temporaryDirectoryRule = TemporaryDirectoryRule()
  @get:Rule val pairingWizardFlagRule = FlagRule(StudioFlags.AI_GLASSES_PHONE_EMULATOR_PAIRING_WIZARD_ENABLED, true)

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
    GlassesPairingWizard.resetForTesting()
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
        edtDispatcher = UnconfinedTestDispatcher(testScheduler),
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
  fun testPairGlassesActionDisabledWhenWizardOpen() = runTest {
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
            initialAvdInfo = makeAvdInfo(createInMemoryFileSystemAndFolder("avds"), 1, tag = SystemImageTags.AI_GLASSES_TAG),
          ),
        context = testContext(this),
        deviceHandleFlow = MutableStateFlow(emptyList()),
        edtDispatcher = UnconfinedTestDispatcher(testScheduler),
      )

    // Wait for the action to become enabled (it might take a moment for the flow to emit)
    handle.pairGlassesAction.presentation.first { it.enabled }

    val completion = CompletableDeferred<Boolean>()
    val job = launch {
      GlassesPairingWizard.showCore(null, null, MutableStateFlow(emptyList<DeviceHandle>()), handle) { _, _, _, _, _, _ ->
        object : WizardController {
          override suspend fun show(): Boolean = completion.await()
        }
      }
    }

    val lockService = ApplicationManager.getApplication().getService(GlassesPairingLockService::class.java)
    yieldUntil { lockService.isWizardOpen.value }
    yieldUntil { !handle.pairGlassesAction.presentation.value.enabled }

    assertThat(handle.pairGlassesAction.presentation.value.detail).isEqualTo("Pairing already in progress")

    completion.complete(false)
    job.join()

    yieldUntil { handle.pairGlassesAction.presentation.value.enabled }

    handleScope.cancel()
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

    activationAction.presentation.first { it.enabled }

    avdManager.avds[0] = avdManager.makeAvdInfo(1, avdStatus = AvdStatus.ERROR_IMAGE_MISSING)
    plugin.refreshDevices()

    // The action should become disabled.
    yieldUntil { activationAction.presentation.value.enabled == false }
  }

  @Test
  fun testWipeDataUnpairsCompanions() = runBlockingWithTimeout {
    val avdRoot = temporaryDirectoryRule.newPath()
    val phonePath = avdRoot.resolve("fake_avd_1.avd").toString()
    val glassesPath = avdRoot.resolve("fake_avd_2.avd").toString()

    val phoneInfo = makeAvdInfo(avdRoot, 1, userSettings = mapOf("paired.glasses.avd.id.1" to deviceId(glassesPath).toString()))
    val glassesInfo =
      makeAvdInfo(
        avdRoot,
        2,
        tag = SystemImageTags.AI_GLASSES_TAG,
        userSettings = mapOf("paired.phone.avd.id.1" to deviceId(phonePath).toString()),
      )

    avdManager.createAvd(phoneInfo)
    avdManager.createAvd(glassesInfo)

    val mockConnection = mock<AvdManagerConnection>().apply { whenever(wipeUserData(any())).thenReturn(true) }
    AvdManagerConnection.setConnectionFactory { _, _ -> mockConnection }
    plugin.refreshDevices()
    yieldUntil { provisioner.devices.value.size == 2 }

    val phoneHandle =
      provisioner.devices.value.first { it.state.properties.deviceType == DeviceType.HANDHELD } as StudioLocalEmulatorDeviceHandle
    val glassesHandle =
      provisioner.devices.value.first { it.state.properties.deviceType == DeviceType.AI_GLASSES } as StudioLocalEmulatorDeviceHandle

    Files.createDirectories((phoneHandle.state.properties as LocalEmulatorProperties).avdPath)
    Files.createDirectories((glassesHandle.state.properties as LocalEmulatorProperties).avdPath)

    val phoneAvdPath = (phoneHandle.state.properties as LocalEmulatorProperties).avdPath
    val glassesAvdPath = (glassesHandle.state.properties as LocalEmulatorProperties).avdPath

    // Ensure memory property is resolved
    yieldUntil { getPairedGlassesInfos(phoneHandle).isNotEmpty() }

    phoneHandle.wipeDataAction.wipeData()

    // The mock avdManager ignores refreshDevices disk updates, so we check disk files directly
    yieldUntil {
      val settings = Files.readAllLines(phoneAvdPath.resolve("user-settings.ini"))
      !settings.any { it.startsWith(UserSettingsKey.PAIRED_GLASSES_AVD_ID_PREFIX) }
    }
    yieldUntil {
      val settings = Files.readAllLines(glassesAvdPath.resolve("user-settings.ini"))
      !settings.any { it.startsWith(UserSettingsKey.PAIRED_PHONE_AVD_ID_PREFIX) }
    }
  }

  @Test
  fun testDeleteActionUnpairsCompanions() = runBlockingWithTimeout {
    val avdRoot = temporaryDirectoryRule.newPath()
    val phonePath = avdRoot.resolve("fake_avd_1.avd").toString()
    val glassesPath = avdRoot.resolve("fake_avd_2.avd").toString()

    val phoneInfo = makeAvdInfo(avdRoot, 1, userSettings = mapOf("paired.glasses.avd.id.1" to deviceId(glassesPath).toString()))
    val glassesInfo =
      makeAvdInfo(
        avdRoot,
        2,
        tag = SystemImageTags.AI_GLASSES_TAG,
        userSettings = mapOf("paired.phone.avd.id.1" to deviceId(phonePath).toString()),
      )

    avdManager.createAvd(phoneInfo)
    avdManager.createAvd(glassesInfo)

    val mockDeleteConnection = mock<AvdManagerConnection>().apply { whenever(deleteAvd(any())).thenReturn(true) }
    AvdManagerConnection.setConnectionFactory { _, _ -> mockDeleteConnection }
    plugin.refreshDevices()
    yieldUntil { provisioner.devices.value.size == 2 }

    val phoneHandle =
      provisioner.devices.value.first { it.state.properties.deviceType == DeviceType.HANDHELD } as StudioLocalEmulatorDeviceHandle
    val glassesHandle =
      provisioner.devices.value.first { it.state.properties.deviceType == DeviceType.AI_GLASSES } as StudioLocalEmulatorDeviceHandle

    Files.createDirectories((phoneHandle.state.properties as LocalEmulatorProperties).avdPath)
    Files.createDirectories((glassesHandle.state.properties as LocalEmulatorProperties).avdPath)

    val glassesAvdPath = (glassesHandle.state.properties as LocalEmulatorProperties).avdPath

    yieldUntil { getPairedGlassesInfos(phoneHandle).isNotEmpty() }

    phoneHandle.deleteAction.delete()

    // Check disk file to verify glasses was unpaired
    yieldUntil {
      val settings = Files.readAllLines(glassesAvdPath.resolve("user-settings.ini"))
      !settings.any { it.startsWith(UserSettingsKey.PAIRED_PHONE_AVD_ID_PREFIX) }
    }
  }

  @Test
  fun testDeleteActionContinuesOnUnpairFailure() =
    runBlockingWithTimeout<Unit> {
      val avdRoot = temporaryDirectoryRule.newPath()
      val phonePath = avdRoot.resolve("fake_avd_1.avd").toString()
      val glassesPath = avdRoot.resolve("fake_avd_2.avd").toString()

      val phoneInfo = makeAvdInfo(avdRoot, 1, userSettings = mapOf("paired.glasses.avd.id.1" to deviceId(glassesPath).toString()))
      val glassesInfo =
        makeAvdInfo(
          avdRoot,
          2,
          tag = SystemImageTags.AI_GLASSES_TAG,
          userSettings = mapOf("paired.phone.avd.id.1" to deviceId(phonePath).toString()),
        )

      avdManager.createAvd(phoneInfo)
      avdManager.createAvd(glassesInfo)

      val mockDeleteConnection = mock<AvdManagerConnection>()
      whenever(mockDeleteConnection.deleteAvd(any())).thenReturn(true)
      AvdManagerConnection.setConnectionFactory { _, _ -> mockDeleteConnection }
      plugin.refreshDevices()
      yieldUntil { provisioner.devices.value.size == 2 }

      val phoneHandle =
        provisioner.devices.value.first { it.state.properties.deviceType == DeviceType.HANDHELD } as StudioLocalEmulatorDeviceHandle
      val glassesHandle =
        provisioner.devices.value.first { it.state.properties.deviceType == DeviceType.AI_GLASSES } as StudioLocalEmulatorDeviceHandle

      Files.createDirectories((phoneHandle.state.properties as LocalEmulatorProperties).avdPath)
      Files.createDirectories((glassesHandle.state.properties as LocalEmulatorProperties).avdPath)

      val glassesAvdPath = (glassesHandle.state.properties as LocalEmulatorProperties).avdPath
      Files.write(glassesAvdPath.resolve("user-settings.ini"), listOf("paired.phone.avd.id.1=${phoneHandle.id}"))

      // Ensure memory property is resolved
      yieldUntil { getPairedGlassesInfos(phoneHandle).isNotEmpty() }

      // Make companion file non-writable to simulate IOException on update
      glassesAvdPath.resolve("user-settings.ini").toFile().setWritable(false)

      try {
        phoneHandle.deleteAction.delete()

        // The mock avdManager ignores refreshDevices disk updates, so we check disk files directly
        // Glasses should STILL HAVE the phone reference because the write failed
        val settings = Files.readAllLines(glassesAvdPath.resolve("user-settings.ini"))
        assertThat(settings.any { it.startsWith("paired.phone.avd") }).isTrue()
      } finally {
        // Clean up: make it writable again so tearDown can delete the folder
        glassesAvdPath.resolve("user-settings.ini").toFile().setWritable(true)
      }
    }

  @Test
  fun testActionFailureIgnoresCompanions() = runBlockingWithTimeout {
    val avdRoot = temporaryDirectoryRule.newPath()
    val phonePath = avdRoot.resolve("fake_avd_1.avd").toString()
    val glassesPath = avdRoot.resolve("fake_avd_2.avd").toString()

    val phoneInfo = makeAvdInfo(avdRoot, 1, userSettings = mapOf("paired.glasses.avd.id.1" to deviceId(glassesPath).toString()))
    val glassesInfo =
      makeAvdInfo(
        avdRoot,
        2,
        tag = SystemImageTags.AI_GLASSES_TAG,
        userSettings = mapOf("paired.phone.avd.id.1" to deviceId(phonePath).toString()),
      )

    avdManager.createAvd(phoneInfo)
    avdManager.createAvd(glassesInfo)

    val mockConnection = mock<AvdManagerConnection>().apply { whenever(wipeUserData(any())).thenReturn(false) }
    AvdManagerConnection.setConnectionFactory { _, _ -> mockConnection }

    plugin.refreshDevices()
    yieldUntil { provisioner.devices.value.size == 2 }

    val phoneHandle =
      provisioner.devices.value.first { it.state.properties.deviceType == DeviceType.HANDHELD } as StudioLocalEmulatorDeviceHandle
    val glassesHandle =
      provisioner.devices.value.first { it.state.properties.deviceType == DeviceType.AI_GLASSES } as StudioLocalEmulatorDeviceHandle

    Files.createDirectories((phoneHandle.state.properties as LocalEmulatorProperties).avdPath)
    Files.createDirectories((glassesHandle.state.properties as LocalEmulatorProperties).avdPath)

    val phoneAvdPath = (phoneHandle.state.properties as LocalEmulatorProperties).avdPath
    Files.write(phoneAvdPath.resolve("user-settings.ini"), listOf("paired.glasses.avd.id.1=${glassesHandle.id}"))

    yieldUntil { getPairedGlassesInfos(phoneHandle).isNotEmpty() }

    val previousDialog = TestDialogManager.setTestDialog(TestDialog.OK)
    try {
      phoneHandle.wipeDataAction.wipeData()
    } finally {
      TestDialogManager.setTestDialog(previousDialog)
    }

    // Assert still paired
    val phoneSettings = Files.readAllLines(phoneAvdPath.resolve("user-settings.ini"))
    assertThat(phoneSettings.any { it.startsWith(UserSettingsKey.PAIRED_GLASSES_AVD_ID_PREFIX) }).isTrue()
    assertThat(glassesHandle.state.properties.pairedPhoneId).isEqualTo(phoneHandle.id)
  }

  private fun deviceId(avdPath: String) = DeviceId(LocalEmulatorProvisionerPlugin.PLUGIN_ID, false, "path=$avdPath")

  @Test
  fun testPairedDevicesLaunchedAutomatically(): Unit = runBlockingWithTimeout {
    val avdRoot = temporaryDirectoryRule.newPath()
    val phonePath = avdRoot.resolve("fake_avd_1.avd").toString()
    val glassesPath = avdRoot.resolve("fake_avd_2.avd").toString()
    val phoneInfo = makeAvdInfo(avdRoot, 1, userSettings = mapOf("paired.glasses.avd.id.1" to deviceId(glassesPath).toString()))
    val glassesInfo =
      makeAvdInfo(
        avdRoot,
        2,
        tag = SystemImageTags.AI_GLASSES_TAG,
        userSettings = mapOf("${UserSettingsKey.PAIRED_PHONE_AVD_ID_PREFIX}1" to deviceId(phonePath).toString()),
      )
    avdManager.createAvd(phoneInfo)
    avdManager.createAvd(glassesInfo)

    plugin.refreshDevices()
    yieldUntil { provisioner.devices.value.size == 2 }

    val phoneHandle =
      provisioner.devices.value.find { it.state.properties.deviceType == DeviceType.HANDHELD } as StudioLocalEmulatorDeviceHandle
    val glassesHandle =
      provisioner.devices.value.find { it.state.properties.deviceType == DeviceType.AI_GLASSES } as StudioLocalEmulatorDeviceHandle

    glassesHandle.activationAction.presentation.first { it.enabled }
    phoneHandle.activationAction.presentation.first { it.enabled }

    // Verify that the glasses device is correctly paired to the phone device
    yieldUntil { (glassesHandle.state.properties as LocalEmulatorProperties).pairedPhoneId != null }
    assertThat((glassesHandle.state.properties as LocalEmulatorProperties).pairedPhoneId).isEqualTo(phoneHandle.id)

    // Actually invoke the activation action and verify that the paired connection triggers
    val mockConnection = mock<AvdManagerConnection>()
    AvdManagerConnection.setConnectionFactory { _, _ -> mockConnection }

    // Note that activation will not actually complete because we don't implement the fake ADB connection, so just verify that startAvd was
    // called.
    val activationJob = launch { glassesHandle.activationAction.activate() }
    yieldUntil {
      kotlin
        .runCatching {
          verify(mockConnection).startAvd(projectRule.project, glassesInfo)
          verify(mockConnection).startAvd(projectRule.project, phoneInfo)
        }
        .isSuccess
    }

    activationJob.cancel()
  }

  @Test
  fun testPairGlassesFetchesMacAddress() = runBlockingWithTimeout {
    val phoneAvdPath = temporaryDirectoryRule.newPath()
    Files.createDirectories(phoneAvdPath.resolve("fake_avd_1.avd"))
    avdManager.createAvd(makeAvdInfo(phoneAvdPath, 1))

    val glassesAvdPath = temporaryDirectoryRule.newPath()
    Files.createDirectories(glassesAvdPath.resolve("fake_avd_2.avd"))
    avdManager.createAvd(makeAvdInfo(glassesAvdPath, 2, tag = SystemImageTags.AI_GLASSES_TAG))

    plugin.refreshDevices()
    yieldUntil { provisioner.devices.value.size == 2 }

    val phoneHandle =
      provisioner.devices.value.first { it.state.properties.deviceType == DeviceType.HANDHELD } as StudioLocalEmulatorDeviceHandle
    val glassesHandle =
      provisioner.devices.value.first { it.state.properties.deviceType == DeviceType.AI_GLASSES } as StudioLocalEmulatorDeviceHandle

    // Mock wizard to return the phone
    glassesHandle.wizardProvider = { _, _, _, _ -> GlassesPairingResult(phoneHandle, "00:11:22:33:44:55") }
    val expectedMac = "00:11:22:33:44:55"

    glassesHandle.pairGlassesAction.pairGlasses(null)

    // Verify MAC was saved
    yieldUntil {
      val phoneAvdPath = (phoneHandle.state.properties as LocalEmulatorProperties).avdPath
      val settings = Files.readAllLines(phoneAvdPath.resolve("user-settings.ini"))
      settings.any { it.contains("paired.glasses.avd.mac.1=$expectedMac") }
    }
  }

  private fun getPairedGlassesInfos(handle: StudioLocalEmulatorDeviceHandle) =
    (handle.state.properties as LocalEmulatorProperties).pairedGlassesInfos
}

private class NullAvdScanner(coroutineScope: CoroutineScope) : AbstractAvdScanner(coroutineScope) {
  override fun scanAvds(): List<AvdInfo> = emptyList()

  override fun logError(message: String, exception: Throwable) = throw exception
}
