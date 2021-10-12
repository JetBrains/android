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
import com.android.ddmlib.MultiLineReceiver
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.android.tools.deployer.model.App
import com.android.tools.deployer.model.component.AppComponent
import com.android.tools.deployer.model.component.ComponentType
import com.android.tools.idea.run.configuration.AndroidTileConfiguration
import com.android.tools.idea.run.configuration.AndroidTileConfigurationType
import com.android.tools.idea.run.configuration.AndroidWearProgramRunner
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.progress.ProgressIndicator
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock

class AndroidTileConfigurationExecutorTest : AndroidWearConfigurationExecutorBaseTest() {
  override fun setUp() {
    super.setUp()
    mockConsole()
  }

  fun test() {
    val configSettings = RunManager.getInstance(project).createConfiguration(
      "run tile", AndroidTileConfigurationType().configurationFactories.single())
    val androidTileConfiguration = configSettings.configuration as AndroidTileConfiguration
    androidTileConfiguration.setModule(myModule)
    androidTileConfiguration.componentName = "p1.p2.MyComponent"
    // Use run executor
    val env = ExecutionEnvironment(DefaultRunExecutor.getRunExecutorInstance(), AndroidWearProgramRunner(), configSettings, project)

    // Mock app component activation.
    val app = Mockito.mock(App::class.java)
    Mockito.doAnswer { invocation: InvocationOnMock ->
      // get the 4th arg (the receiver to feed it the lines).
      val receiver = invocation.getArgument<MultiLineReceiver>(3)
      // Test TileIndexReceiver.
      receiver.processNewLines(arrayOf("Index=[1]"))
    }.`when`(app)
      // Test that we call activateComponent with right params.
      .activateComponent(eq(ComponentType.TILE), eq("p1.p2.MyComponent"), eq(AppComponent.Mode.RUN), any(IShellOutputReceiver::class.java))

    val executor = Mockito.spy(AndroidTileConfigurationExecutor(env))
    // Mock installation that returns app.
    val appInstaller = getMockApplicationInstaller(app)
    Mockito.`when`(executor.getApplicationInstaller()).thenReturn(appInstaller)

    val device = getMockDevice()
    executor.doOnDevices(listOf(device), Mockito.mock(ProgressIndicator::class.java))

    // Test final command of showing Tile after installation and activation.
    Mockito.verify(device).executeShellCommand(
      eq("am broadcast -a com.google.android.wearable.app.DEBUG_SYSUI --es operation show-tile --ei index 1"),
      any(IShellOutputReceiver::class.java),
      any(),
      any()
    )
  }

  fun testDebug() {
    val configSettings = RunManager.getInstance(project).createConfiguration(
      "run tile", AndroidTileConfigurationType().configurationFactories.single())
    val androidTileConfiguration = configSettings.configuration as AndroidTileConfiguration
    androidTileConfiguration.setModule(myModule)
    androidTileConfiguration.componentName = "p1.p2.MyComponent"
    // Use debug executor
    val env = ExecutionEnvironment(DefaultDebugExecutor.getDebugExecutorInstance(), AndroidWearProgramRunner(), configSettings, project)

    // Mock app component activation.
    val app = Mockito.mock(App::class.java)
    Mockito.doAnswer { invocation: InvocationOnMock ->
      // get the 4th arg (the receiver to feed it the lines).
      val receiver = invocation.getArgument<MultiLineReceiver>(3)
      // Test TileIndexReceiver.
      receiver.processNewLines(arrayOf("Index=[1]"))
    }.`when`(app)
      // Test that we call activateComponent with right params.
      .activateComponent(eq(ComponentType.TILE), eq("p1.p2.MyComponent"), eq(AppComponent.Mode.DEBUG),
                         any(IShellOutputReceiver::class.java))

    val executor = Mockito.spy(AndroidTileConfigurationExecutor(env))
    // Mock installation that returns app.
    val applicationInstaller = getMockApplicationInstaller(app)
    Mockito.`when`(executor.getApplicationInstaller()).thenReturn(applicationInstaller)
    // Mock debugSessionStarter.
    Mockito.`when`(executor.getDebugSessionStarter()).thenReturn(Mockito.mock(DebugSessionStarter::class.java))

    val device = getMockDevice()
    executor.doOnDevices(listOf(device), Mockito.mock(ProgressIndicator::class.java))

    // Test final command of showing Tile after installation and activation.
    Mockito.verify(device).executeShellCommand(
      eq("am broadcast -a com.google.android.wearable.app.DEBUG_SYSUI --es operation show-tile --ei index 1"),
      any(IShellOutputReceiver::class.java),
      any(),
      any()
    )
  }
}
