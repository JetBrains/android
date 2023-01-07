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
package testSrc.com.android.tools.idea.device.explorer

import com.android.tools.asdriver.tests.AndroidProject
import com.android.tools.asdriver.tests.AndroidSystem
import com.android.tools.asdriver.tests.ComponentMatchersBuilder
import com.android.tools.asdriver.tests.MavenRepo
import com.android.tools.asdriver.tests.MemoryDashboardNameProviderWatcher
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class DeviceExplorerIntegrationTest {
  @JvmField @Rule
  val system: AndroidSystem = AndroidSystem.standard()

  @JvmField
  @Rule
  var watcher = MemoryDashboardNameProviderWatcher()

  @Test
  fun viewDeviceExplorerToolWindow() {
    val project = AndroidProject("tools/adt/idea/android/integration/testData/minapp")
    project.setDistribution("tools/external/gradle/gradle-7.5-bin.zip")
    system.installRepo(MavenRepo("tools/adt/idea/android/integration/buildproject_deps.manifest"))
    system.installation.addVmOption("-Didea.log.debug.categories=#com.android.tools.idea.device.explorer.files.DeviceFileExplorerControllerImpl,com.android.tools.idea.device.explorer.monitor.DeviceMonitorModel")

    system.runAdb { adb ->
      system.runEmulator { emulator ->
        emulator.waitForBoot()
        adb.waitForDevice(emulator)

        system.runStudio(project, watcher.dashboardName) { studio ->
          studio.waitForSync()
          studio.waitForIndex()

          assertThat(studio.showToolWindow("Device Explorer")).isTrue()

          // Verify device process nodes are populated.
          system.installation.ideaLog.waitForMatchingLine(".*Process list updated to ([1-9]+[0-9]*) processes(.*)", 60, TimeUnit.SECONDS)

          // Verify device file nodes are populated.
          system.installation.ideaLog.waitForMatchingLine(".*Number of nodes added: ([1-9]+[0-9]*)(.*)", 60, TimeUnit.SECONDS)
        }
      }
    }
  }
}