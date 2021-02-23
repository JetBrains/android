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
import com.intellij.mock.MockApplication
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.IdeUICustomization
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.atLeast
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

class DeployTaskTest {
  private val rootDisposable: Disposable = Disposer.newDisposable()
  private val application: MockApplication = MockApplication(rootDisposable)

  @Mock private lateinit var project: Project
  @Mock private lateinit var device: IDevice
  @Mock private lateinit var deployer: Deployer

  @Before
  fun setup() {
    ApplicationManager.setApplication(application, rootDisposable)
    application.registerService(IdeUICustomization::class.java)
    MockitoAnnotations.initMocks(this)
    Mockito.`when`(deployer.install(any(), any(), any(), any())).thenReturn(Deployer.Result())
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
    Mockito.`when`(device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)).thenReturn(false)
    Mockito.`when`(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.BASE))
    val expectedOptions = InstallOptions.builder().setAllowDebuggable().build()

    val deployTask = DeployTask(project, mapOf(), null, true, false)
    deployTask.perform(device, deployer, "", listOf())
    verify(deployer, atLeast(1)).install(any(), any(), eq(expectedOptions), any())
  }

  @Test
  fun testDeployWithUserPmOptions() {
    Mockito.`when`(device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)).thenReturn(false)
    Mockito.`when`(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.BASE))
    val expectedOptions = InstallOptions.builder().setAllowDebuggable().setUserInstallOptions("-v").build()

    val deployTask = DeployTask(project, mapOf(), "-v", true, false)
    deployTask.perform(device, deployer, "", listOf())
    verify(deployer, atLeast(1)).install(any(), any(), eq(expectedOptions), any())
  }

  @Test
  fun testDeployEmbedded() {
    Mockito.`when`(device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)).thenReturn(true)
    Mockito.`when`(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.BASE))
    val expectedOptions = InstallOptions.builder().setAllowDebuggable().setGrantAllPermissions().build()

    val deployTask = DeployTask(project, mapOf(), null, true, false)
    deployTask.perform(device, deployer, "", listOf())
    verify(deployer, atLeast(1)).install(any(), any(), eq(expectedOptions), any())
  }

  @Test
  fun testDeployEmbeddedWithUserPmOptions() {
    Mockito.`when`(device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)).thenReturn(true)
    Mockito.`when`(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.BASE))
    val expectedOptions = InstallOptions.builder().setAllowDebuggable().setGrantAllPermissions().setUserInstallOptions("-v").build()

    val deployTask = DeployTask(project, mapOf(), "-v", true, false)
    deployTask.perform(device, deployer, "", listOf())
    verify(deployer, atLeast(1)).install(any(), any(), eq(expectedOptions), any())
  }

  @Test
  fun testDeployApi28() {
    Mockito.`when`(device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)).thenReturn(false)
    Mockito.`when`(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.P))
    val expectedOptions = InstallOptions.builder().setAllowDebuggable().setInstallFullApk().setDontKill().build()

    val deployTask = DeployTask(project, mapOf(), null, true, false)
    deployTask.perform(device, deployer, "", listOf())
    verify(deployer, atLeast(1)).install(any(), any(), eq(expectedOptions), any())
  }

  @Test
  fun testDeployApi28WithUserPmOptions() {
    Mockito.`when`(device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)).thenReturn(false)
    Mockito.`when`(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.P))
    val expectedOptions =
      InstallOptions.builder().setAllowDebuggable().setInstallFullApk().setDontKill().setUserInstallOptions("-v").build()

    val deployTask = DeployTask(project, mapOf(), "-v", true, false)
    deployTask.perform(device, deployer, "", listOf())
    verify(deployer, atLeast(1)).install(any(), any(), eq(expectedOptions), any())
  }

  @Test
  fun testDeployToCurrentUserOnly() {
    Mockito.`when`(device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)).thenReturn(false)
    Mockito.`when`(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.P))
    val expectedOptions = InstallOptions.builder().setAllowDebuggable().setInstallOnCurrentUser().setInstallFullApk().setDontKill().build()

    val deployTask = DeployTask(project, mapOf(), null, false, false)
    deployTask.perform(device, deployer, "", listOf())
    verify(deployer, atLeast(1)).install(any(), any(), eq(expectedOptions), any())
  }

  @Test
  fun testDeployPostInstallForceStopPreN() {
    Mockito.`when`(device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)).thenReturn(false)
    Mockito.`when`(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.BASE))

    val deployTask = DeployTask(project, mapOf(), null, true, false)
    deployTask.perform(device, deployer, "", listOf())
    verify(device, never()).forceStop(any())
  }

  @Test
  fun testDeployPostInstallForceStopPostN() {
    Mockito.`when`(device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)).thenReturn(false)
    Mockito.`when`(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.N))

    val deployTask = DeployTask(project, mapOf(), null, true, false)
    deployTask.perform(device, deployer, "", listOf())
    verify(device, times(1)).forceStop(any())
  }

  @Test
  fun testDeployAndroidXTestServicesOnApi30() {
    Mockito.`when`(device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)).thenReturn(false)
    Mockito.`when`(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.R))
    val expectedOptions = InstallOptions.builder()
      .setAllowDebuggable()
      .setForceQueryable()
      .setInstallFullApk()
      .setDontKill()
      .build()

    val deployTask = DeployTask(project, mapOf(), null, true, false)
    deployTask.perform(device, deployer, "androidx.test.services", listOf())

    verify(deployer).install(eq("androidx.test.services"), any(), eq(expectedOptions), any())
  }

  @Test
  fun testDeployAndroidXTestServicesOnApi29() {
    Mockito.`when`(device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)).thenReturn(false)
    Mockito.`when`(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.Q))
    val expectedOptions = InstallOptions.builder()
      .setAllowDebuggable()
      .setInstallFullApk()
      .setDontKill()
      .build()

    val deployTask = DeployTask(project, mapOf(), null, true, false)
    deployTask.perform(device, deployer, "androidx.test.services", listOf())

    verify(deployer).install(eq("androidx.test.services"), any(), eq(expectedOptions), any())
  }
}