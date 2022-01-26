/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.run.configuration.execution

import com.android.ddmlib.IDevice
import com.android.ddmlib.IShellOutputReceiver
import com.android.sdklib.AndroidVersion
import com.android.sdklib.devices.Abi
import com.android.testutils.MockitoKt
import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.run.ApkInfo
import com.android.tools.idea.run.ApkProvider
import com.android.tools.idea.run.ApkProvisionException
import com.android.tools.idea.run.ApplicationIdProvider
import com.android.tools.idea.run.ValidationError
import com.google.common.collect.ImmutableList
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.testFramework.replaceService
import org.jetbrains.android.AndroidTestCase
import org.mockito.Mockito
import java.io.File

abstract class AndroidConfigurationExecutorBaseTest : AndroidTestCase() {
  protected val appId = "com.example.app"
  protected val componentName = "com.example.app.Component"

  override fun setUp() {
    super.setUp()
    val projectSystemMock = createProjectSystemMock()
    Mockito.`when`(projectSystemMock.getApkProvider(MockitoKt.any(RunConfiguration::class.java))).thenReturn(TestApksProvider(appId))
    Mockito.`when`(projectSystemMock.getApplicationIdProvider(
      MockitoKt.any(RunConfiguration::class.java))).thenReturn(TestApplicationIdProvider(appId))
  }

  private fun createProjectSystemMock(): AndroidProjectSystem {
    val projectSystemMock = Mockito.mock(AndroidProjectSystem::class.java)
    val projectSystemService = Mockito.mock(ProjectSystemService::class.java)
    Mockito.`when`(projectSystemService.projectSystem).thenReturn(projectSystemMock)
    project.replaceService(ProjectSystemService::class.java, projectSystemService, testRootDisposable)
    return projectSystemMock
  }

  protected fun getMockDevice(replies: (request: String) -> String = { request -> "Mock reply: $request" }): IDevice {
    val device = Mockito.mock(IDevice::class.java)
    Mockito.`when`(device.version).thenReturn(AndroidVersion(20, null))
    Mockito.`when`(device.density).thenReturn(640)
    Mockito.`when`(device.abis).thenReturn(ImmutableList.of(Abi.ARMEABI, Abi.X86).map { it.toString() })
    Mockito.`when`(
      device.executeShellCommand(Mockito.anyString(), Mockito.any(), Mockito.anyLong(), Mockito.any())
    ).thenAnswer { invocation ->
      val request = invocation.arguments[0] as String
      val receiver = invocation.arguments[1] as IShellOutputReceiver
      val reply = replies(request)
      val byteArray = "$reply\n".toByteArray(Charsets.UTF_8)
      receiver.addOutput(byteArray, 0, byteArray.size)
    }
    return device
  }

  private class TestApksProvider(private val appId: String) : ApkProvider {
    @Throws(ApkProvisionException::class)
    override fun getApks(device: IDevice): Collection<ApkInfo> {
      return listOf(ApkInfo(File("file"), appId))
    }

    override fun validate(): List<ValidationError> {
      return ArrayList()
    }
  }

  private class TestApplicationIdProvider(private val appId: String) : ApplicationIdProvider {
    override fun getPackageName() = appId

    override fun getTestPackageName(): String? = null
  }
}