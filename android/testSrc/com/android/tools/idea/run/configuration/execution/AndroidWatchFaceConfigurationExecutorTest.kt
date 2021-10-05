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

import com.android.ddmlib.IShellOutputReceiver
import com.android.testutils.MockitoKt
import com.android.tools.deployer.model.App
import com.android.tools.deployer.model.component.AppComponent
import com.android.tools.deployer.model.component.ComponentType
import com.android.tools.idea.run.configuration.AndroidWatchFaceConfiguration
import com.android.tools.idea.run.configuration.AndroidWatchFaceConfigurationType
import com.android.tools.idea.run.configuration.AndroidWearProgramRunner
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.progress.ProgressIndicator
import org.mockito.Mockito

class AndroidWatchFaceConfigurationExecutorTest : AndroidWearConfigurationExecutorBaseTest() {

  override fun setUp() {
    super.setUp()
    mockConsole()
  }

  fun test() {
    val configSettings = RunManager.getInstance(project).createConfiguration(
      "run WatchFace", AndroidWatchFaceConfigurationType().configurationFactories.single())
    val androidWatchFaceConfiguration = configSettings.configuration as AndroidWatchFaceConfiguration
    androidWatchFaceConfiguration.setModule(myModule)
    androidWatchFaceConfiguration.componentName = "p1.p2.MyComponent"
    // Use run executor
    val env = ExecutionEnvironment(DefaultRunExecutor.getRunExecutorInstance(), AndroidWearProgramRunner(), configSettings, project)

    val executor = Mockito.spy(AndroidWatchFaceConfigurationExecutor(env))
    // Mock installation that returns app.
    // Mock app component activation.
    val app = Mockito.mock(App::class.java)
    val appInstaller = getMockApplicationInstaller(app)
    Mockito.`when`(executor.getApplicationInstaller()).thenReturn(appInstaller)

    val device = getMockDevice()
    executor.doOnDevices(listOf(device), Mockito.mock(ProgressIndicator::class.java))

    Mockito.verify(app).activateComponent(MockitoKt.eq(ComponentType.WATCH_FACE), MockitoKt.eq("p1.p2.MyComponent"),
                                          MockitoKt.eq(AppComponent.Mode.RUN),
                                          MockitoKt.any(IShellOutputReceiver::class.java))

    // Test final command of showing WatchFace after installation and activation.
    Mockito.verify(device).executeShellCommand(
      MockitoKt.eq("am broadcast -a com.google.android.wearable.app.DEBUG_SYSUI --es operation show-watchface"),
      MockitoKt.any(IShellOutputReceiver::class.java),
      MockitoKt.any(),
      MockitoKt.any()
    )
  }

  fun testDebug() {
    val configSettings = RunManager.getInstance(project).createConfiguration(
      "run WatchFace", AndroidWatchFaceConfigurationType().configurationFactories.single())
    val androidWatchFaceConfiguration = configSettings.configuration as AndroidWatchFaceConfiguration
    androidWatchFaceConfiguration.setModule(myModule)
    androidWatchFaceConfiguration.componentName = "p1.p2.MyComponent"
    // Use debug executor
    val env = ExecutionEnvironment(DefaultDebugExecutor.getDebugExecutorInstance(), AndroidWearProgramRunner(), configSettings, project)

    // Mock app component activation.
    val app = Mockito.mock(App::class.java)

    // Mock installation that returns app.
    val appInstaller = getMockApplicationInstaller(app)

    val executor = Mockito.spy(AndroidWatchFaceConfigurationExecutor(env))
    Mockito.`when`(executor.getApplicationInstaller()).thenReturn(appInstaller)
    // Mock debugSessionStarter.
    Mockito.`when`(executor.getDebugSessionStarter()).thenReturn(Mockito.mock(DebugSessionStarter::class.java))

    val device = getMockDevice()
    executor.doOnDevices(listOf(device), Mockito.mock(ProgressIndicator::class.java))

    Mockito.verify(app).activateComponent(MockitoKt.eq(ComponentType.WATCH_FACE), MockitoKt.eq("p1.p2.MyComponent"),
                                          MockitoKt.eq(AppComponent.Mode.DEBUG),
                                          MockitoKt.any(IShellOutputReceiver::class.java))

    // Test final command of showing WatchFace after installation and activation.
    Mockito.verify(device).executeShellCommand(
      MockitoKt.eq("am broadcast -a com.google.android.wearable.app.DEBUG_SYSUI --es operation show-watchface"),
      MockitoKt.any(IShellOutputReceiver::class.java),
      MockitoKt.any(),
      MockitoKt.any()
    )
  }
}