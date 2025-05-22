/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.ddmlib.AndroidDebugBridge
import com.android.fakeadbserver.services.ShellCommandOutput
import com.android.tools.idea.run.AndroidDeclarativeWatchFaceProgramRunner
import com.android.tools.idea.run.FakeAndroidDevice
import com.android.tools.idea.run.ShowLogcatListener
import com.android.tools.idea.run.configuration.AndroidDeclarativeWatchFaceConfigurationType
import com.android.tools.idea.testing.disposable
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.Executor
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.progress.EmptyProgressIndicator
import java.nio.file.Path
import org.junit.Test

class AndroidDeclarativeWatchFaceConfigurationExecutorTest :
  AndroidConfigurationExecutorBaseTest() {

  private val checkVersion =
    "broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation version"
  private val setWatchFace =
    "broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation set-watchface --es watchFaceId com.example.app"
  private val showWatchFace =
    "broadcast -a com.google.android.wearable.app.DEBUG_SYSUI --es operation show-watchface"

  private fun getExecutionEnvironment(executorInstance: Executor): ExecutionEnvironment {
    val configSettings =
      RunManager.getInstance(project)
        .createConfiguration(
          "run WatchFace",
          AndroidDeclarativeWatchFaceConfigurationType().configurationFactories.single(),
        )
    return ExecutionEnvironment(
      executorInstance,
      AndroidDeclarativeWatchFaceProgramRunner(),
      configSettings,
      project,
    )
  }

  @Test
  fun testRun() {
    // Use DefaultRunExecutor, equivalent of pressing run button.
    val env = getExecutionEnvironment(DefaultRunExecutor.getRunExecutorInstance())

    val deviceState = fakeAdbRule.connectAndWaitForDevice()
    val receivedAmCommands = ArrayList<String>()

    deviceState.setActivityManager { args: List<String>, shellCommandOutput: ShellCommandOutput ->
      val wholeCommand = args.joinToString(" ")

      receivedAmCommands.add(wholeCommand)

      when (wholeCommand) {
        checkVersion ->
          shellCommandOutput.writeStdout(
            "Broadcasting: Intent { act=com.google.android.wearable.app.DEBUG_SURFACE flg=0x400000 (has extras) }\n" +
              "Broadcast completed: result=1, data=\"4\""
          )
        setWatchFace ->
          shellCommandOutput.writeStdout(
            "Broadcasting: Intent { act=com.google.android.wearable.app.DEBUG_SURFACE flg=0x400000 (has extras) }\n" +
              "Broadcast completed: result=1, data=\"Favorite Id=[2] Runtime=[1]\""
          )
      }
    }

    val device = AndroidDebugBridge.getBridge()!!.devices.single()

    val deviceFutures = FakeAndroidDevice.forDevices(listOf(device))
    val app = createApp(device, appId, servicesName = emptyList(), activitiesName = emptyList())
    val appInstaller = TestApplicationInstaller(appId, app)
    val executor =
      AndroidDeclarativeWatchFaceConfigurationExecutor(
        env,
        deviceFutures,
        TestApplicationIdProvider(appId),
        TestApksProvider(appId),
        appInstaller,
      )

    var shownLogcatDeviceInfo: ShowLogcatListener.DeviceInfo? = null
    var shownLogcatAppId: String? = null
    projectRule.project.messageBus
      .connect(projectRule.disposable)
      .subscribe(
        ShowLogcatListener.TOPIC,
        object : ShowLogcatListener {
          override fun showLogcat(
            deviceInfo: ShowLogcatListener.DeviceInfo,
            applicationId: String?,
          ) {
            shownLogcatDeviceInfo = deviceInfo
            shownLogcatAppId = applicationId
          }

          override fun showLogcatFile(path: Path, displayName: String?) {}
        },
      )

    getRunContentDescriptorForTests { executor.run(EmptyProgressIndicator()) }

    // Verify commands sent to device.
    // check WatchFace API version.
    assertThat(receivedAmCommands[0]).isEqualTo(checkVersion)
    // Set WatchFace.
    assertThat(receivedAmCommands[1]).isEqualTo(setWatchFace)
    // Showing WatchFace.
    assertThat(receivedAmCommands[2]).isEqualTo(showWatchFace)

    // Verify that a logcat window for the watch face runtime application is shown
    assertThat(shownLogcatDeviceInfo?.serialNumber).isEqualTo(device.serialNumber)
    assertThat(shownLogcatAppId).isEqualTo("com.google.wear.watchface.runtime")
  }

  @Test
  fun `debug is not supported`() {
    val env = getExecutionEnvironment(DefaultRunExecutor.getRunExecutorInstance())

    fakeAdbRule.connectAndWaitForDevice()
    val device = AndroidDebugBridge.getBridge()!!.devices.single()

    val deviceFutures = FakeAndroidDevice.forDevices(listOf(device))
    val app = createApp(device, appId, servicesName = emptyList(), activitiesName = emptyList())
    val appInstaller = TestApplicationInstaller(appId, app)
    val executor =
      AndroidDeclarativeWatchFaceConfigurationExecutor(
        env,
        deviceFutures,
        TestApplicationIdProvider(appId),
        TestApksProvider(appId),
        appInstaller,
      )

    val result = runCatching { executor.debug(EmptyProgressIndicator()) }
    assertThat(result.isFailure).isTrue()
    assertThat(result.exceptionOrNull()?.message).isEqualTo("Unsupported operation")
  }
}
