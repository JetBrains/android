/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea

import com.android.tools.asdriver.tests.AndroidProject
import com.android.tools.asdriver.tests.AndroidSystem
import com.android.tools.asdriver.tests.Emulator
import com.android.tools.asdriver.tests.MavenRepo
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes


@Ignore("b/328255748")
class MultipleDevicesInstrumentedTest {
  @JvmField
  @Rule
  val system: AndroidSystem = AndroidSystem.standard()

  @Test
  fun deployInstrumentedTest() {
    val project = AndroidProject("tools/adt/idea/android/integration/testData/InstrumentedTestApp")
    system.installRepo(MavenRepo("tools/adt/idea/android/integration/run_instrumented_test_project_deps.manifest"))

    system.runAdb { adb ->
      system.runEmulator(Emulator.SystemImage.API_33_ATD) { emulator1 ->
        system.runEmulator(Emulator.SystemImage.API_33_ATD) { emulator2 ->
          setDeviceSelection(project, emulator1, emulator2)
          emulator1.waitForBoot()
          emulator2.waitForBoot()
          adb.waitForDevice(emulator1)
          adb.waitForDevice(emulator2)
          system.runStudio(project) { studio ->
            studio.waitForSync()
            studio.waitForIndex()

            studio.executeAction("MakeGradleProject")
            studio.waitForBuild()
            studio.waitForIndex()
            studio.executeAction("Run")
            adb.runCommand("logcat", emulator = emulator1).waitForLog(".*Instrumented Test Success!!.*", 5.minutes)
            adb.runCommand("logcat", emulator = emulator2).waitForLog(".*Instrumented Test Success!!.*", 5.minutes)
            assert(system.installation.ideaLog.findMatchingLines(
              ".*AndroidProcessHandler - Adding device ${emulator1.serialNumber} to monitor for launched app: com\\.example\\.instrumentedtestapp").size > 0)
            assert(system.installation.ideaLog.findMatchingLines(
              ".*AndroidProcessHandler - Adding device ${emulator2.serialNumber} to monitor for launched app: com\\.example\\.instrumentedtestapp").size > 0)
          }
        }
      }
    }
  }

  private fun setDeviceSelection(project: AndroidProject, emulator1: Emulator, emulator2: Emulator) {
    val avdHome = "${emulator1.home.toAbsolutePath()}/.android/avd"
    project.inject(Path.of(".idea/deploymentTargetSelector.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project version="4">
                  <component name="deploymentTargetSelector">
                    <selectionStates>
                      <SelectionState runConfigName="ExampleInstrumentedTest">
                        <option name="selectionMode" value="DIALOG" />
                        <DialogSelection>
                          <targets>
                            <Target type="DEFAULT_BOOT">
                              <handle>
                                <DeviceId pluginId="LocalEmulator" identifier="path=$avdHome/${emulator2.name}.avd" />
                              </handle>
                            </Target>
                            <Target type="DEFAULT_BOOT">
                              <handle>
                                <DeviceId pluginId="LocalEmulator" identifier="path=$avdHome/${emulator1.name}.avd" />
                              </handle>
                            </Target>
                          </targets>
                        </DialogSelection>
                      </SelectionState>
                    </selectionStates>
                  </component>
                </project>
        """.trimIndent())
  }
}