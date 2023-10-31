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
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import icons.StudioIcons
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Rule
import org.junit.Test

class DeviceInfoPanelTest {

  @get:Rule val applicationRule = ApplicationRule()

  @get:Rule
  val deviceProvisionerRule = DeviceProvisionerRule {
    installDefaultCommandHandlers()
    installDeviceHandler(DumpsysBatteryHandler())
    installDeviceHandler(DfHandler())
  }

  class DumpsysBatteryHandler : SimpleShellHandler(ShellProtocolType.SHELL, "dumpsys") {
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
            level: 83
            scale: 100
            voltage: 5000
            temperature: 250
            technology: Li-ion
          """
            .trimIndent()
        )
      } else {
        statusWriter.writeFail()
      }
    }
  }

  class DfHandler : SimpleShellHandler(ShellProtocolType.SHELL, "df") {
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
      } else {
        statusWriter.writeFail()
      }
    }
  }

  @Test
  fun testPopulateDeviceInfo() = runBlocking {
    val handle =
      deviceProvisionerRule.deviceProvisionerPlugin.addNewDevice(
        "1",
        DeviceProperties.build {
          manufacturer = "Google"
          model = "Pixel 6"
          androidVersion = AndroidVersion(33, null, 4, false)
          androidRelease = "11"
          abi = Abi.ARM64_V8A
          isVirtual = false
          resolution = Resolution(1080, 2280)
          density = 440
          icon = StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE
        }
      )

    handle.activationAction.activate()

    yieldUntil { handle.state.isOnline() }

    withContext(uiThread) {
      val panel = DeviceInfoPanel()

      populateDeviceInfo(panel, handle)

      assertThat(panel.apiLevel).isEqualTo("33-ext4")
      assertThat(panel.power).isEqualTo("Battery: 83")
      assertThat(panel.resolution).isEqualTo("1080 × 2280")
      assertThat(panel.resolutionDp).isEqualTo("393 × 830")
      assertThat(panel.abiList).isEqualTo("arm64-v8a")
      assertThat(panel.availableStorage).isEqualTo("2,542 MB")
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
