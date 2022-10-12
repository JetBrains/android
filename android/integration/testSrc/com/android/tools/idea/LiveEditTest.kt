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
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.TimeUnit


class LiveEditTest {
  @JvmField
  @Rule
  val system: AndroidSystem = AndroidSystem.standard()

  /**
   * Enables Live Edit by modifying the settings on-disk.
   */
  @Throws(IOException::class)
  fun enableLiveEdit() {
    val filetypePaths = system.installation.configDir.resolve("options/other.xml")
    check(!filetypePaths.toFile().exists()) {
      String.format("%s already exists, which means this method should be changed to merge with it rather than overwriting it.",
                    filetypePaths)
    }
    Files.createDirectories(filetypePaths.parent)
    val filetypeContents = String.format(
      "<application>%n" +
      "  <component name=\"LiveEditConfiguration\">%n" +
      "    <option name=\"leTriggerMode\" value=\"LE_TRIGGER_AUTOMATIC\" />%n" +
      "    <option name=\"mode\" value=\"LIVE_EDIT\" />%n" +
      "  </component>%n" +
      "</application>")
    Files.writeString(filetypePaths, filetypeContents, StandardCharsets.UTF_8)
  }

  @Test
  fun liveEditTest() {
    val project = AndroidProject("tools/adt/idea/android/integration/testData/liveedit")
    project.setDistribution("tools/external/gradle/gradle-7.3.3-bin.zip")
    system.installRepo(MavenRepo("tools/adt/idea/android/integration/live_edit_project_deps.manifest"))

    enableLiveEdit()
    system.runAdb { adb ->
      // Live Edit requires an API level of at least 30.
      system.runEmulator(Emulator.SystemImage.API_30) { emulator ->
        system.runStudio(project) { studio ->
          studio.waitForSync()
          studio.waitForIndex()

          // Open the file ahead of time so that Live Edit is ready when we want to make a change
          val path = project.targetProject.resolve("app/src/main/java/com/example/liveedittest/MainActivity.kt")
          studio.openFile("LiveEditTest", path.toString())

          studio.executeAction("MakeGradleProject")
          studio.waitForBuild()

          studio.executeAction("Run")
          system.installation.ideaLog.waitForMatchingLine(
            ".*AndroidProcessHandler - Adding device emulator-${emulator.portString} to monitor for launched app: com\\.example\\.liveedittest",
            60, TimeUnit.SECONDS)
          adb.runCommand("logcat") {
            waitForLog(".*Before editing.*", 30, TimeUnit.SECONDS);
          }

          val newContents = "Log.i(\"MainActivity\", \"After editing\")\n" +
                            "Text(text = \"Hello, Live Edit and \$name!\")";
          studio.editFile(path.toString(), "(?s)// EASILY SEARCHABLE LINE.*?// END SEARCH", newContents)

          adb.runCommand("logcat") {
            waitForLog(".*After editing.*", 480, TimeUnit.SECONDS);
          }
        }
      }
    }
  }
}