/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.devicemanagerv2.details

import com.android.adblib.testingutils.CoroutineTestUtils.yieldUntil
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import com.android.fakeadbserver.ShellProtocolType
import com.android.fakeadbserver.services.ShellCommandOutput
import com.android.fakeadbserver.shellcommandhandlers.SimpleShellHandler
import com.android.fakeadbserver.shellcommandhandlers.StatusWriter
import com.android.sdklib.AndroidVersion
import com.android.sdklib.deviceprovisioner.DeviceProperties
import com.android.sdklib.deviceprovisioner.Resolution
import com.android.sdklib.deviceprovisioner.testing.DeviceProvisionerRule
import com.android.sdklib.devices.Abi
import com.android.testutils.retryUntilPassing
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.ApplicationRule
import icons.StudioIcons
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class DeviceInfoPanelTest {

  @get:Rule val applicationRule = ApplicationRule()

  val batteryHandler = DumpsysBatteryHandler()

  @get:Rule
  val deviceProvisionerRule = DeviceProvisionerRule {
    installDefaultCommandHandlers()
    installDeviceHandler(batteryHandler)
    installDeviceHandler(DfHandler())
  }

  class DumpsysBatteryHandler : SimpleShellHandler(ShellProtocolType.SHELL_V2, "dumpsys") {
    var batteryLevel = 83

    override fun execute(
      fakeAdbServer: FakeAdbServer,
      statusWriter: StatusWriter,
      shellCommandOutput: ShellCommandOutput,
      device: DeviceState,
      shellCommand: String,
      shellCommandArgs: String?
    ) {
      if (shellCommandArgs == "battery") {
        statusWriter.writeOk()
        shellCommandOutput.writeStdout(
          """
          Current Battery Service state:
            AC powered: false
            USB powered: false
            Wireless powered: false
            Max charging current: 0
            Max charging voltage: 0
            Charge counter: 10000
            status: 4
            health: 2
            present: true
            level: $batteryLevel
            scale: 100
            voltage: 5000
            temperature: 250
            technology: Li-ion
          """
            .trimIndent()
        )
        shellCommandOutput.writeExitCode(0)
      } else {
        statusWriter.writeFail()
        shellCommandOutput.writeExitCode(1)
      }
    }
  }

  class DfHandler : SimpleShellHandler(ShellProtocolType.SHELL_V2, "df") {
    override fun execute(
      fakeAdbServer: FakeAdbServer,
      statusWriter: StatusWriter,
      shellCommandOutput: ShellCommandOutput,
      device: DeviceState,
      shellCommand: String,
      shellCommandArgs: String?
    ) {
      if (shellCommandArgs == "/data") {
        statusWriter.writeOk()
        shellCommandOutput.writeStdout(
          """
            Filesystem       1K-blocks    Used Available Use% Mounted on
            /dev/block/dm-32   6082144 3336860   2603072  57% /storage/emulated/0/Android/obb
            """
            .trimIndent()
        )
        shellCommandOutput.writeExitCode(0)
      } else {
        statusWriter.writeFail()
        shellCommandOutput.writeExitCode(1)
      }
    }
  }

  @Test
  fun testPopulateDeviceInfo() {
    fun makeProps(abi: Abi?) =
      DeviceProperties.buildForTest {
        manufacturer = "Google"
        model = "Pixel 6"
        androidVersion = AndroidVersion(30, null, 0, true)
        androidRelease = "11"
        abiList = listOfNotNull(abi)
        isVirtual = false
        resolution = Resolution(1080, 2280)
        density = 440
        icon = StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE
      }

    val handle = deviceProvisionerRule.deviceProvisionerPlugin.addNewDevice("1", makeProps(null))

    runBlocking {
      handle.activationAction.activate()
      yieldUntil { handle.state.isOnline() }
    }

    lateinit var panel: DeviceInfoPanel
    ApplicationManager.getApplication().invokeAndWait {
      panel =
        DeviceInfoPanel().also {
          it.trackDeviceProperties(handle.scope, handle)
          it.trackDevicePowerAndStorage(handle.scope, handle)
        }
    }

    ApplicationManager.getApplication().invokeAndWait {
      retryUntilPassing(5.seconds) {
        assertThat(panel.apiLevel).isEqualTo("30")
        assertThat(panel.power).isEqualTo("Battery: 83")
        assertThat(panel.resolution).isEqualTo("1080 × 2280")
        assertThat(panel.resolutionDp).isEqualTo("393 × 830")
        assertThat(panel.abiList).isEqualTo("Unknown")
        assertThat(panel.availableStorage).isEqualTo("2,542 MB")
      }
    }

    runBlocking { handle.deactivationAction.deactivate() }

    ApplicationManager.getApplication().invokeAndWait {
      retryUntilPassing(5.seconds) { assertThat(panel.powerLabel.isVisible).isFalse() }
    }

    batteryHandler.batteryLevel = 81
    runBlocking { handle.activationAction.activate() }

    ApplicationManager.getApplication().invokeAndWait {
      retryUntilPassing(5.seconds) {
        assertThat(panel.power).isEqualTo("Battery: 81")
        assertThat(panel.availableStorage).isEqualTo("2,542 MB")
      }
    }

    handle.stateFlow.update {
      (it as com.android.sdklib.deviceprovisioner.DeviceState.Connected).copy(
        makeProps(Abi.ARM64_V8A)
      )
    }

    ApplicationManager.getApplication().invokeAndWait {
      retryUntilPassing(5.seconds) { assertThat(panel.abiList).isEqualTo("arm64-v8a") }
    }
  }

  @Test
  fun infoSectionFormat() = runBlocking {
    withContext(uiThread) {
      val buffer = StringBuilder()
      InfoSection(
          "Properties",
          listOf(
            LabeledValue("Type", "Phone"),
            LabeledValue("System image", "/tmp/foo/system.img"),
            LabeledValue("API", "33")
          )
        )
        .writeTo(buffer)
      assertThat(buffer.toString())
        .isEqualTo(
          String.format(
            "Properties%n" +
              "Type         Phone%n" +
              "System image /tmp/foo/system.img%n" +
              "API          33%n"
          )
        )
    }
  }
}
