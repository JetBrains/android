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
import com.android.tools.idea.FirstLaunchTest.Companion.getFileNamesInSdkDirectory
import com.google.common.truth.Truth
import com.intellij.openapi.util.SystemInfo
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files

class FirstLaunchNewWizardTest {

  @JvmField
  @Rule
  val system: AndroidSystem =
    AndroidSystem.basicRemoteSDK(Display.createDefault(), Files.createTempDirectory("root"))

  @Test
  fun firstLaunchTest() {
    system.installation.addVmOption("-Dnpw.first.run.wizard=true")
    FirstLaunchTest.configureWizardFlags(system)

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
      studio.invokeComponent("Next")
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
}
