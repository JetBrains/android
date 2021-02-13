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
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.atLeast
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

class DeployTaskTest {
  @Mock private lateinit var project: Project
  @Mock private lateinit var device: IDevice
  @Mock private lateinit var deployer: Deployer
  private var installPathProvider = Computable { "" }

  @Before
  fun setup() {
    MockitoAnnotations.initMocks(this)
  }

  @Test
  fun testDeploy() {
    Mockito.`when`(device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)).thenReturn(false)
    Mockito.`when`(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.BASE))
    val expectedOptions = InstallOptions.builder().setAllowDebuggable().build()

    val deployTask = DeployTask(project, mapOf(), null, true, installPathProvider)
    deployTask.perform(device, deployer, "", listOf())
    verify(deployer, atLeast(1)).install(any(), any(), eq(expectedOptions), any())
  }

  @Test
  fun testDeployWithUserPmOptions() {
    Mockito.`when`(device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)).thenReturn(false)
    Mockito.`when`(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.BASE))
    val expectedOptions = InstallOptions.builder().setAllowDebuggable().setUserInstallOptions("-v").build()

    val deployTask = DeployTask(project, mapOf(), "-v", true, installPathProvider)
    deployTask.perform(device, deployer, "", listOf())
    verify(deployer, atLeast(1)).install(any(), any(), eq(expectedOptions), any())
  }

  @Test
  fun testDeployEmbedded() {
    Mockito.`when`(device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)).thenReturn(true)
    Mockito.`when`(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.BASE))
    val expectedOptions = InstallOptions.builder().setAllowDebuggable().setGrantAllPermissions().build()

    val deployTask = DeployTask(project, mapOf(), null, true, installPathProvider)
    deployTask.perform(device, deployer, "", listOf())
    verify(deployer, atLeast(1)).install(any(), any(), eq(expectedOptions), any())
  }

  @Test
  fun testDeployEmbeddedWithUserPmOptions() {
    Mockito.`when`(device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)).thenReturn(true)
    Mockito.`when`(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.BASE))
    val expectedOptions = InstallOptions.builder().setAllowDebuggable().setGrantAllPermissions().setUserInstallOptions("-v").build()

    val deployTask = DeployTask(project, mapOf(), "-v", true, installPathProvider)
    deployTask.perform(device, deployer, "", listOf())
    verify(deployer, atLeast(1)).install(any(), any(), eq(expectedOptions), any())
  }

  @Test
  fun testDeployApi28() {
    Mockito.`when`(device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)).thenReturn(false)
    Mockito.`when`(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.P))
    val expectedOptions = InstallOptions.builder().setAllowDebuggable().setInstallFullApk().setDontKill().build()

    val deployTask = DeployTask(project, mapOf(), null, true, installPathProvider)
    deployTask.perform(device, deployer, "", listOf())
    verify(deployer, atLeast(1)).install(any(), any(), eq(expectedOptions), any())
  }

  @Test
  fun testDeployApi28WithUserPmOptions() {
    Mockito.`when`(device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)).thenReturn(false)
    Mockito.`when`(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.P))
    val expectedOptions =
      InstallOptions.builder().setAllowDebuggable().setInstallFullApk().setDontKill().setUserInstallOptions("-v").build()

    val deployTask = DeployTask(project, mapOf(), "-v", true, installPathProvider)
    deployTask.perform(device, deployer, "", listOf())
    verify(deployer, atLeast(1)).install(any(), any(), eq(expectedOptions), any())
  }

  @Test
  fun testDeployToCurrentUserOnly() {
    Mockito.`when`(device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)).thenReturn(false)
    Mockito.`when`(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.P))
    val expectedOptions = InstallOptions.builder().setAllowDebuggable().setInstallOnCurrentUser().setInstallFullApk().setDontKill().build()

    val deployTask = DeployTask(project, mapOf(), null, false, installPathProvider)
    deployTask.perform(device, deployer, "", listOf())
    verify(deployer, atLeast(1)).install(any(), any(), eq(expectedOptions), any())
  }
}