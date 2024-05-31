/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.idea.run.tasks

import com.android.ddmlib.IDevice
import com.android.sdklib.AndroidVersion
import com.android.testutils.MockitoKt
import com.android.testutils.MockitoKt.whenever
import com.android.tools.deployer.Deployer
import com.android.tools.deployer.InstallOptions
import com.android.tools.deployer.UIService
import com.android.tools.deployer.model.App
import com.android.tools.deployer.tasks.Canceller
import com.android.tools.idea.run.ApkInfo
import com.android.tools.idea.run.DeploymentService
import com.android.utils.ILogger
import com.intellij.mock.MockApplication
import com.intellij.mock.MockProject
import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.IdeUICustomization
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.any
import org.mockito.Mockito.atLeast
import org.mockito.Mockito.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.Spy

class DeployTaskTest {
  private val rootDisposable: Disposable = Disposer.newDisposable()
  private val application: MockApplication = MockApplication(rootDisposable)
  private val project: MockProject = MockProject(null, rootDisposable)

  @Mock private lateinit var logger: ILogger
  @Mock private lateinit var device: IDevice
  @Mock private lateinit var deployer: Deployer
  @Spy private lateinit var canceller: Canceller
  private val installPathProvider = Computable { "" }

  @Before
  fun setup() {
    ApplicationManager.setApplication(application, rootDisposable)
    application.registerService(IdeUICustomization::class.java)
    application.registerService(DeploymentService::class.java)
    application.registerService(NotificationGroupManager::class.java, TestDeployTaskNotificationGroupManager::class.java)

    MockitoAnnotations.initMocks(this)
    whenever(deployer.install(any(), any(), any())).thenReturn(
      Deployer.Result(false, false, false, App.fromApks("id", emptyList()))
    )
    whenever(canceller.cancelled()).thenReturn(false)
  }

  @After
  fun shutdown() {
    Disposer.dispose(rootDisposable)
  }

  @Test
  fun testDeploy() {
    whenever(device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)).thenReturn(false)
    whenever(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.BASE))
    val expectedOptions = InstallOptions.builder().setAllowDebuggable().build()

    val deployTask = DeployTask(project, listOf(), null, true, false, installPathProvider)
    deployTask.perform(device, deployer, mock(ApkInfo::class.java), canceller )
    verify(deployer, atLeast(1)).install(any(), eq(expectedOptions), any())
  }

  @Test
  fun testDeployTaskRunUsesDynamicUIServiceWhichThrowsExceptionOnInit() {
    val deployTask = DeployTask(project, listOf(), null, true, false, installPathProvider)
    project.registerService(UIService::class.java, ThrowingUIService::class.java)

    val throwable = assertThrows(RuntimeException::class.java) {
      deployTask.run(device, EmptyProgressIndicator())
    }
    assertEquals(ThrowingUIService.ExpectedOnInitException, throwable.cause)
  }

  @Test
  fun testDeployWithUserPmOptions() {
    whenever(device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)).thenReturn(false)
    whenever(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.BASE))
    val expectedOptions = InstallOptions.builder().setAllowDebuggable().setUserInstallOptions("-v").build()

    val deployTask = DeployTask(project, listOf(), "-v", true, false, installPathProvider)
    deployTask.perform(device, deployer, mock(ApkInfo::class.java), canceller)
    verify(deployer, atLeast(1)).install(any(), eq(expectedOptions), any())
  }

  @Test
  fun testDeployEmbedded() {
    whenever(device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)).thenReturn(true)
    whenever(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.BASE))
    val expectedOptions = InstallOptions.builder().setAllowDebuggable().setGrantAllPermissions().build()

    val deployTask = DeployTask(project, listOf(), null, true, false, installPathProvider)
    deployTask.perform(device, deployer, mock(ApkInfo::class.java), canceller)
    verify(deployer, atLeast(1)).install(any(), eq(expectedOptions), any())
  }

  @Test
  fun testDeployEmbeddedWithUserPmOptions() {
    whenever(device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)).thenReturn(true)
    whenever(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.BASE))
    val expectedOptions = InstallOptions.builder().setAllowDebuggable().setGrantAllPermissions().setUserInstallOptions("-v").build()

    val deployTask = DeployTask(project, listOf(), "-v", true, false, installPathProvider)
    deployTask.perform(device, deployer, mock(ApkInfo::class.java), canceller)
    verify(deployer, atLeast(1)).install(any(), eq(expectedOptions), any())
  }

  @Test
  fun testDeployApi28() {
    whenever(device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)).thenReturn(false)
    whenever(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.P))
    val expectedOptions = InstallOptions.builder().setAllowDebuggable().setInstallFullApk().setDontKill().build()

    val deployTask = DeployTask(project, listOf(), null, true, false, installPathProvider)
    deployTask.perform(device, deployer, mock(ApkInfo::class.java), canceller)
    verify(deployer, atLeast(1)).install(any(), eq(expectedOptions), any())
  }

  @Test
  fun testDeployApi28WithUserPmOptions() {
    whenever(device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)).thenReturn(false)
    whenever(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.P))
    val expectedOptions =
      InstallOptions.builder().setAllowDebuggable().setInstallFullApk().setDontKill().setUserInstallOptions("-v").build()

    val deployTask = DeployTask(project, listOf(), "-v", true, false, installPathProvider)
    deployTask.perform(device, deployer, mock(ApkInfo::class.java), canceller)
    verify(deployer, atLeast(1)).install(any(), eq(expectedOptions), any())
  }

  @Test
  fun testDeployToCurrentUserOnly() {
    whenever(device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)).thenReturn(false)
    whenever(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.P))
    val expectedOptions = InstallOptions.builder().setAllowDebuggable().setInstallOnUser(InstallOptions.CURRENT_USER).setInstallFullApk().setDontKill().build()

    val deployTask = DeployTask(project, listOf(), null, false, false, installPathProvider)
    deployTask.perform(device, deployer, mock(ApkInfo::class.java), canceller)
    verify(deployer, atLeast(1)).install(any(), eq(expectedOptions), any())
  }

  @Test
  fun testDeployPostInstallForceStopPreN() {
    whenever(device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)).thenReturn(false)
    whenever(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.BASE))

    val deployTask = DeployTask(project, listOf(), null, true, false, installPathProvider)
    deployTask.perform(device, deployer, mock(ApkInfo::class.java), canceller)
    verify(device, never()).forceStop(any())
  }

  @Test
  fun testDeployPostInstallForceStopPostN() {
    whenever(device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)).thenReturn(false)
    whenever(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.N))

    val deployTask = DeployTask(project, listOf(), null, true, false, installPathProvider)
    deployTask.perform(device, deployer, mock(ApkInfo::class.java), canceller)
    verify(device, times(1)).forceStop(any())
  }

  @Test
  fun testDeployApkWithRequiredInstallOptionsOnApi30() {
    val expectedOptions = InstallOptions.builder()
      .setAllowDebuggable()
      .setGrantAllPermissions()
      .setForceQueryable()
      .setInstallFullApk()
      .setDontKill()
      .build()

    deployApkWithRequiredInstallOptions(AndroidVersion.VersionCodes.R)

    verify(deployer).install(any(), eq(expectedOptions), any())
  }

  @Test
  fun testDeployApkWithRequiredInstallOptionsOnApi29() {
    val expectedOptions = InstallOptions.builder()
      .setAllowDebuggable()
      .setGrantAllPermissions()
      .setInstallFullApk()
      .setDontKill()
      .build()

    deployApkWithRequiredInstallOptions(AndroidVersion.VersionCodes.Q)

    verify(deployer).install(any(), eq(expectedOptions), any())
  }

  @Test
  fun testDeployApkWithRequiredInstallOptionsOnApi22() {
    val expectedOptions = InstallOptions.builder()
      .setAllowDebuggable()
      .build()

    deployApkWithRequiredInstallOptions(AndroidVersion.VersionCodes.LOLLIPOP_MR1)

    verify(deployer).install(any(), eq(expectedOptions), any())
  }

  private fun deployApkWithRequiredInstallOptions(deviceApiLevel: Int) {
    whenever(device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)).thenReturn(false)
    whenever(device.version).thenReturn(AndroidVersion(deviceApiLevel))
    val mockApkInfo = mock(ApkInfo::class.java)
    whenever(mockApkInfo.requiredInstallOptions).thenReturn(
      setOf(ApkInfo.AppInstallOption.FORCE_QUERYABLE, ApkInfo.AppInstallOption.GRANT_ALL_PERMISSIONS))

    val deployTask = DeployTask(project, listOf(mockApkInfo), null, true, false, installPathProvider)
    deployTask.perform(device, deployer, mockApkInfo, canceller)
  }
}

private class ThrowingUIService : UIService {
  object ExpectedOnInitException : Exception()

  init {
    throw ExpectedOnInitException
  }

  override fun prompt(result: String?): Boolean = true
  override fun message(message: String?) {}
}

private class TestDeployTaskNotificationGroupManager : NotificationGroupManager {
  val mockNotification = MockitoKt.mock<Notification>()
  val mockNotificationGroup = MockitoKt.mock<NotificationGroup>()

  init {
    whenever(
      mockNotificationGroup.createNotification(
        MockitoKt.any<String>(),
        MockitoKt.any<NotificationType>()
      )
    )
      .thenAnswer { mockNotification }
  }

  override fun getNotificationGroup(groupId: String): NotificationGroup = when (groupId) {
    "Deploy" -> mockNotificationGroup
    else -> throw IllegalArgumentException("Unexpected groupId: $groupId")
  }

  override fun isGroupRegistered(groupId: String): Boolean = when (groupId) {
    "Deploy" -> true
    else -> false
  }

  override fun getRegisteredNotificationGroups() = mutableListOf(mockNotificationGroup)

  override fun isRegisteredNotificationId(notificationId: String) = false
}