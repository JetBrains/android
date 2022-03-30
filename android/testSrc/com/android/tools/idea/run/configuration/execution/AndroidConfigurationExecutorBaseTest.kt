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
import com.android.testutils.MockitoKt.any
import com.android.tools.idea.logcat.AndroidLogcatService
import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.run.ApkInfo
import com.android.tools.idea.run.ApkProvider
import com.android.tools.idea.run.ApkProvisionException
import com.android.tools.idea.run.ApplicationIdProvider
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.collect.ImmutableList
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.replaceService
import com.intellij.xdebugger.XDebuggerManager
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import java.io.File


internal typealias Command = String
internal typealias CommandHandler = (mockDevice: IDevice, receiver: AndroidConfigurationExecutorBaseTest.TestReceiver) -> Unit

fun Map<Command, String>.toCommandHandlers(): MutableMap<Command, CommandHandler> {
  return entries.associate {
    it.key to { _: IDevice, receiver: AndroidConfigurationExecutorBaseTest.TestReceiver -> receiver.addOutput(it.value) }
  }.toMutableMap()
}

abstract class AndroidConfigurationExecutorBaseTest {
  protected val appId = "com.example.app"
  protected val componentName = "com.example.app.Component"

  @get:Rule
  val projectRule = AndroidProjectRule.onDisk()
  val project: Project
    get() = projectRule.project
  val testRootDisposable: Disposable
    get() = projectRule.testRootDisposable
  val myModule: com.intellij.openapi.module.Module
    get() = projectRule.module

  @Before
  fun setUp() {
    val projectSystemMock = createProjectSystemMock()
    `when`(projectSystemMock.getApkProvider(any(RunConfiguration::class.java))).thenReturn(TestApksProvider(appId))
    `when`(projectSystemMock.getApplicationIdProvider(
      any(RunConfiguration::class.java))).thenReturn(TestApplicationIdProvider(appId))

    val emptyLogcatService = Mockito.mock(AndroidLogcatService::class.java)
    ApplicationManager.getApplication().replaceService(AndroidLogcatService::class.java, emptyLogcatService, testRootDisposable)
  }

  @After
  fun after() {
    XDebuggerManager.getInstance(project).debugSessions.forEach {
      it.stop()
    }
  }

  private fun createProjectSystemMock(): AndroidProjectSystem {
    val projectSystemMock = Mockito.mock(AndroidProjectSystem::class.java)
    val projectSystemService = Mockito.mock(ProjectSystemService::class.java)
    `when`(projectSystemService.projectSystem).thenReturn(projectSystemMock)
    project.replaceService(ProjectSystemService::class.java, projectSystemService, testRootDisposable)
    return projectSystemMock
  }

  protected fun getMockDevice(commandHandlers: Map<Command, CommandHandler> = emptyMap()): IDevice {
    val device = Mockito.mock(IDevice::class.java)
    `when`(device.version).thenReturn(AndroidVersion(20, null))
    `when`(device.density).thenReturn(640)
    `when`(device.abis).thenReturn(ImmutableList.of(Abi.ARMEABI, Abi.X86).map { it.toString() })
    `when`(
      device.executeShellCommand(Mockito.anyString(), Mockito.any(), Mockito.anyLong(), Mockito.any())
    ).thenAnswer { invocation ->
      val command = invocation.arguments[0] as String
      val receiver = TestReceiver(invocation.arguments[1] as? IShellOutputReceiver)
      val handler = commandHandlers[command]
      handler?.invoke(device, receiver)
    }
    `when`(device.isOnline).thenReturn(true)
    return device
  }

  class TestReceiver(private val receiver: IShellOutputReceiver?) {
    fun addOutput(commandOutput: String) {
      val byteArray = "$commandOutput\n".toByteArray(Charsets.UTF_8)
      receiver?.addOutput(byteArray, 0, byteArray.size)
    }
  }

  private class TestApksProvider(private val appId: String) : ApkProvider {
    @Throws(ApkProvisionException::class)
    override fun getApks(device: IDevice): Collection<ApkInfo> {
      return listOf(ApkInfo(File("file"), appId))
    }
  }

  private class TestApplicationIdProvider(private val appId: String) : ApplicationIdProvider {
    override fun getPackageName() = appId

    override fun getTestPackageName(): String? = null
  }
}
