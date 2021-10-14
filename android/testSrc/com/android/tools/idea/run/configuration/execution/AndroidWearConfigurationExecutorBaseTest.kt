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
import com.android.sdklib.AndroidVersion
import com.android.sdklib.devices.Abi
import com.android.testutils.MockitoKt
import com.android.tools.deployer.model.App
import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.run.ApkInfo
import com.android.tools.idea.run.ApkProvider
import com.android.tools.idea.run.ApkProvisionException
import com.android.tools.idea.run.ApplicationIdProvider
import com.android.tools.idea.run.ValidationError
import com.android.tools.manifest.parser.XmlNode
import com.android.tools.manifest.parser.components.ManifestServiceInfo
import com.google.common.collect.ImmutableList
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.filters.TextConsoleBuilder
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.replaceService
import org.jetbrains.android.AndroidTestCase
import org.mockito.Mockito
import java.io.File

abstract class AndroidWearConfigurationExecutorBaseTest : AndroidTestCase() {
  protected val appId = "com.example.app"
  protected val componentName = "com.example.app.Component"
  protected lateinit var projectSystemMock: AndroidProjectSystem

  override fun setUp() {
    super.setUp()
    projectSystemMock = createProjectSystemMock()
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

  protected fun getMockDevice(): IDevice {
    val device = Mockito.mock(IDevice::class.java)
    Mockito.`when`(device.version).thenReturn(AndroidVersion(20, null))
    Mockito.`when`(device.density).thenReturn(640)
    Mockito.`when`(device.abis).thenReturn(ImmutableList.of(Abi.ARMEABI, Abi.X86).map { it.toString() })
    return device
  }

  protected fun mockConsole() {
    val testConsoleBuilderFactory = Mockito.mock(TextConsoleBuilderFactory::class.java)
    val textConsoleBuilder = Mockito.mock(TextConsoleBuilder::class.java)
    val consoleView = Mockito.mock(ConsoleView::class.java)
    Mockito.`when`(textConsoleBuilder.console).thenReturn(consoleView)
    Mockito.`when`(testConsoleBuilderFactory.createBuilder(MockitoKt.any(Project::class.java))).thenReturn(textConsoleBuilder)
    ApplicationManager.getApplication().replaceService(TextConsoleBuilderFactory::class.java, testConsoleBuilderFactory, testRootDisposable)
  }

  protected fun createManifestServiceInfo(serviceName: String,
                                          appId: String,
                                          attrs: Map<String, String> = emptyMap()): ManifestServiceInfo {
    val node = XmlNode()
    node.attributes()["name"] = serviceName
    for ((attr, value) in attrs) {
      node.attributes()[attr] = value
    }
    return ManifestServiceInfo(node, appId)
  }
}

internal class TestApplicationInstaller : ApplicationInstaller {

  private var appIdToApp: HashMap<String, App>

  constructor(appId: String, app: App) : this(hashMapOf<String, App>(Pair(appId, app)))

  constructor(appIdToApp: HashMap<String, App>) {
    this.appIdToApp = appIdToApp
  }

  override fun installAppOnDevice(device: IDevice,
                                  appId: String,
                                  apksPaths: List<String>,
                                  installFlags: String,
                                  infoReceiver: (String) -> Unit): App {
    return appIdToApp[appId]!!
  }

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