/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.tools.asdriver.tests.AndroidSystem
import com.android.tools.asdriver.tests.Display
import com.google.common.truth.Truth
import com.intellij.openapi.util.SystemInfo
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import kotlin.io.path.name

@Ignore("b/389922725")
class FirstLaunchTest {
  @JvmField
  @Rule
  val system: AndroidSystem = AndroidSystem.basicRemoteSDK(Display.createDefault(), Files.createTempDirectory("root"))

  @Test
  fun firstLaunchTest() {
    system.installation.addVmOption("-Dwizard.migration.first.run.migrated.wizard.enabled=false")
    configureWizardFlags(system)

    system.runStudioWithoutProject().use { studio ->
      studio.waitForComponentWithExactText("Welcome")
      Thread.sleep(1000)
      studio.invokeComponent("Next")
      studio.waitForComponentWithExactText("Install Type")
      Thread.sleep(1000)
      studio.invokeComponent("Next")
      studio.waitForComponentWithExactText("Verify Settings")
      Thread.sleep(1000)
      studio.invokeComponent("Next")
      studio.waitForComponentWithExactText("License Agreement")
      Thread.sleep(1000)
      if (SystemInfo.isLinux) { // The emulator settings step will only show on the Linux platform
        studio.invokeComponent("Next")
        studio.waitForComponentWithExactText("Emulator Settings")
        Thread.sleep(1000)
      }
      studio.invokeComponent("Finish")
      studio.waitForComponentWithExactText("Downloading Components")
      Thread.sleep(1000)
      studio.invokeComponent("Finish")

      val fileNames = getFileNamesInSdkDirectory(system)
      val expectedFiles: List<String> =
        ArrayList(
          mutableListOf(
            "build-tools",
            "emulator",
            "licenses",
            "platforms",
            "platform-tools",
            "sources",
          )
        )
      Truth.assertThat<String>(fileNames).asList().containsAllIn(expectedFiles)
    }
  }

  companion object {
    fun configureWizardFlags(system: AndroidSystem) {
      system.installation.addVmOption("-Dnpw.first.run.wizard.show=true")
      system.installation.addVmOption("-Dnpw.first.run.offline=true")
      system.installation.addVmOption("-Dnpw.first.run.accept.sdk.license=true")
      system.installation.addVmOption(
        String.format("-Dnpw.first.run.local.app.data=%s", system.installation.fileSystem.root)
      )
    }

    fun getFileNamesInSdkDirectory(system: AndroidSystem): Array<String?> {
      var directory = system.installation.fileSystem.root
      if (SystemInfo.isLinux) {
        directory = directory.resolve("home")
      }
      else if (SystemInfo.isMac) {
        directory = directory.resolve("Library")
      }
      val files = Files.list(directory.resolve("Android").resolve("Sdk")).toList()
      val fileNames = arrayOfNulls<String>(files?.size ?: 0)
      files?.mapIndexed { index, item -> fileNames[index] = item?.name }
      return fileNames
    }
  }
}