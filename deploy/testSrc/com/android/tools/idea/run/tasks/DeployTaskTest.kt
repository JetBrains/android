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
import com.android.tools.deployer.Deployer
import com.android.tools.deployer.InstallOptions
import com.android.tools.deployer.model.App
import com.android.tools.deployer.tasks.Canceller
import com.android.tools.idea.run.ApkInfo
import com.android.utils.ILogger
import com.intellij.mock.MockApplication
import com.intellij.notification.NotificationGroupManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.IdeUICustomization
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.atLeast
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.Spy
import org.mockito.kotlin.whenever

class DeployTaskTest {
  private val rootDisposable: Disposable = Disposer.newDisposable()
  private val application: MockApplication = MockApplication(rootDisposable)

  @Mock private lateinit var project: Project
  @Mock private lateinit var logger: ILogger
  @Mock private lateinit var device: IDevice
  @Mock private lateinit var deployer: Deployer
  @Mock private lateinit var notificationGroupManager: NotificationGroupManager
  @Spy private lateinit var canceller: Canceller

  @Before
  fun setup() {
    ApplicationManager.setApplication(application, rootDisposable)
    application.registerService(IdeUICustomization::class.java)
    MockitoAnnotations.initMocks(this)
    application.registerService(NotificationGroupManager::class.java, notificationGroupManager)
    whenever(deployer.install(any(), any(), any())).thenReturn(
      Deployer.Result(false, false, false, App.fromApks("id", emptyList()))
    )
    whenever(canceller.cancelled()).thenReturn(false)
  }

  @After
  fun shutdown() {
    Disposer.dispose(rootDisposable)

    // Null out the static reference in [ApplicationManager].
    // Keeping a reference to a disposed object can cause problems for other tests.
    val field = ApplicationManager::class.java.getDeclaredField("ourApplication")
    field.isAccessible = true
    field.set(null, null)
  }

  @Test
  fun testDeploy() {
    whenever(device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)).thenReturn(false)
    whenever(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.BASE))
    val expectedOptions = InstallOptions.builder().setAllowDebuggable().build()

    val deployTask = makeDeployTaskForTesting()
    deployTask.perform(device, deployer, mock(ApkInfo::class.java), canceller )
    verify(deployer, atLeast(1)).install(any(), eq(expectedOptions), any())
  }

  @Test
  fun testDeployWithUserPmOptions() {
    whenever(device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)).thenReturn(false)
    whenever(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.BASE))
    val expectedOptions = InstallOptions.builder().setAllowDebuggable().setUserInstallOptions("-v").build()

    val deployTask = makeDeployTaskForTesting(userInstallOptions = "-v")
    deployTask.perform(device, deployer, mock(ApkInfo::class.java), canceller)
    verify(deployer, atLeast(1)).install(any(), eq(expectedOptions), any())
  }

  @Test
  fun testDeployEmbedded() {
    whenever(device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)).thenReturn(true)
    whenever(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.BASE))
    val expectedOptions = InstallOptions.builder().setAllowDebuggable().setGrantAllPermissions().build()

    val deployTask = makeDeployTaskForTesting()
    deployTask.perform(device, deployer, mock(ApkInfo::class.java), canceller)
    verify(deployer, atLeast(1)).install(any(), eq(expectedOptions), any())
  }

  @Test
  fun testDeployEmbeddedWithUserPmOptions() {
    whenever(device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)).thenReturn(true)
    whenever(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.BASE))
    val expectedOptions = InstallOptions.builder().setAllowDebuggable().setGrantAllPermissions().setUserInstallOptions("-v").build()

    val deployTask = makeDeployTaskForTesting(userInstallOptions = "-v")
    deployTask.perform(device, deployer, mock(ApkInfo::class.java), canceller)
    verify(deployer, atLeast(1)).install(any(), eq(expectedOptions), any())
  }

  @Test
  fun testDeployApi28() {
    whenever(device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)).thenReturn(false)
    whenever(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.P))
    val expectedOptions = InstallOptions.builder().setAllowDebuggable().setInstallFullApk().setDontKill().build()

    val deployTask = makeDeployTaskForTesting()
    deployTask.perform(device, deployer, mock(ApkInfo::class.java), canceller)
    verify(deployer, atLeast(1)).install(any(), eq(expectedOptions), any())
  }

  @Test
  @Ignore("b/389067070")
  fun testDeployApi33() {
    whenever(device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)).thenReturn(false)
    whenever(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.TIRAMISU))
    val expectedOptions = InstallOptions.builder().setAllowDebuggable().setInstallFullApk().build()

    val deployTask = makeDeployTaskForTesting()
    deployTask.perform(device, deployer, mock(ApkInfo::class.java), canceller)
    verify(deployer, atLeast(1)).install(any(), eq(expectedOptions), any())
    verify(device, never()).forceStop(any())
  }

  @Test
  fun testDeployApi28WithUserPmOptions() {
    whenever(device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)).thenReturn(false)
    whenever(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.P))
    val expectedOptions =
      InstallOptions.builder().setAllowDebuggable().setInstallFullApk().setDontKill().setUserInstallOptions("-v").build()

    val deployTask = makeDeployTaskForTesting(userInstallOptions = "-v")
    deployTask.perform(device, deployer, mock(ApkInfo::class.java), canceller)
    verify(deployer, atLeast(1)).install(any(), eq(expectedOptions), any())
  }

  @Test
  fun testDeployToCurrentUserOnly() {
    whenever(device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)).thenReturn(false)
    whenever(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.P))
    val expectedOptions = InstallOptions.builder().setAllowDebuggable().setInstallOnUser(InstallOptions.CURRENT_USER).setInstallFullApk().setDontKill().build()

    val deployTask = makeDeployTaskForTesting(installOnAllUsers = false)
    deployTask.perform(device, deployer, mock(ApkInfo::class.java), canceller)
    verify(deployer, atLeast(1)).install(any(), eq(expectedOptions), any())
  }

  @Test
  fun testDeployPostInstallForceStopPreN() {
    whenever(device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)).thenReturn(false)
    whenever(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.BASE))

    val deployTask = makeDeployTaskForTesting()
    deployTask.perform(device, deployer, mock(ApkInfo::class.java), canceller)
    verify(device, never()).forceStop(any())
  }

  @Test
  fun testDeployPostInstallForceStopPostN() {
    whenever(device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)).thenReturn(false)
    whenever(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.N))

    val deployTask = makeDeployTaskForTesting()
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
    verify(device, times(1)).forceStop(any())
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
    verify(device, never()).forceStop(any())
  }

  private fun deployApkWithRequiredInstallOptions(deviceApiLevel: Int) {
    whenever(device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)).thenReturn(false)
    whenever(device.version).thenReturn(AndroidVersion(deviceApiLevel))
    val mockApkInfo = mock(ApkInfo::class.java)
    whenever(mockApkInfo.requiredInstallOptions).thenReturn(
      setOf(ApkInfo.AppInstallOption.FORCE_QUERYABLE, ApkInfo.AppInstallOption.GRANT_ALL_PERMISSIONS))

    val deployTask = makeDeployTaskForTesting(listOf(mockApkInfo))
    deployTask.perform(device, deployer, mockApkInfo, canceller)
  }

  private fun makeDeployTaskForTesting(
    packages: Collection<ApkInfo> = listOf(),
    userInstallOptions: String? = null,
    installOnAllUsers: Boolean = true,
    alwaysInstallWithPm: Boolean = false,
    allowAssumeVerified: Boolean = false,
    makeBeforeRun: Boolean = true) =
    DeployTask(project, packages, userInstallOptions, installOnAllUsers, alwaysInstallWithPm, allowAssumeVerified, makeBeforeRun)
}
