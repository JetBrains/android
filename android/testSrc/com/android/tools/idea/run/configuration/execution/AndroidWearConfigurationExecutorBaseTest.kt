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
import com.google.common.collect.ImmutableList
import com.intellij.execution.filters.TextConsoleBuilder
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.testFramework.replaceService
import org.jetbrains.android.AndroidTestCase
import org.mockito.Mockito

abstract class AndroidWearConfigurationExecutorBaseTest : AndroidTestCase() {
  protected fun getMockApplicationInstaller(app: App?): ApplicationInstaller {
    val appInstaller = Mockito.mock(ApplicationInstaller::class.java)
    Mockito
      .`when`(appInstaller.installAppOnDevice(MockitoKt.any(IDevice::class.java), MockitoKt.any(ProgressIndicator::class.java),
                                              MockitoKt.any(ConsoleView::class.java)))
      .thenReturn(app)
    return appInstaller
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
}