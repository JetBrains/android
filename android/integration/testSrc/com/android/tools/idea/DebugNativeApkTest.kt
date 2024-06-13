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
package com.android.tools.idea

import com.android.tools.asdriver.tests.AndroidProjectWithoutGradle
import com.android.tools.asdriver.tests.AndroidSystem
import com.android.tools.asdriver.tests.Emulator
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path

class DebugNativeApkTest {
  @JvmField
  @Rule
  val system : AndroidSystem = AndroidSystem.standard()

  @Test
  fun debugNativeApkTest() {
    val project = AndroidProjectWithoutGradle("tools/adt/idea/android/integration/testData/minnativeapp-apk")

    system.installation.setGlobalSdk(system.sdk)

    // Running an APK directly means that Android Studio won't try setting up the JDK table, and
    // even if it did, it would do so with the latest API level. Both of those issues would cause
    // failures in this test, so we force auto-creation at the API level that we expect.
    system.installation.addVmOption("-Dtesting.android.platform.to.autocreate=31")

    system.runAdb { adb ->
      system.runEmulator(Emulator.SystemImage.API_33) { emulator ->
        println("Waiting for boot")
        emulator.waitForBoot()

        // If you try to run the app too early, you'll see this error in Android Studio: "Error
        // while waiting for device: emu0 is already running. If that is not the case, delete
        // <testtemppath>/home/.android/avd/emu0.avd/*.lock and try again."
        println("Waiting for device")
        adb.waitForDevice(emulator)

        system.runStudioFromApk(project) { studio ->
          studio.waitForIndex()
          println("Finished waiting for index");

          studio.waitForProjectInit();

          println("Opening a file")
          val srcPath: Path = project.targetProject.resolve("minnativeapp/src/main/cpp/minnativeapp.cpp")
          val projectName: String = project.targetProject.fileName.toString()
          studio.openFile(projectName, srcPath.toString(), 7, 0)

          println("Setting a breakpoint")
          studio.executeAction("ToggleLineBreakpoint")

          println("Running the app")
          studio.executeAction("Debug")

          studio.waitForNativeBreakpointHit()
        }
      }
    }
  }
}