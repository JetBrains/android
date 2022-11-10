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

import com.android.tools.asdriver.tests.AndroidSystem
import com.android.tools.asdriver.tests.Emulator
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class RunApkTest {
  @JvmField
  @Rule
  val system = AndroidSystem.standard()

  @Test
  fun runApkTest() {
    val project = "tools/adt/idea/android/integration/testData/helloworldapk";

    system.installation.setGlobalSdk(system.sdk)

    // Running an APK directly means that Android Studio won't try setting up the JDK table, and
    // even if it did, it would do so with the latest API level. Both of those issues would cause
    // failures in this test, so we force auto-creation at the API level that we expect.
    system.installation.addVmOption("-Dtesting.android.platform.to.autocreate=31")

    // TODO: figure out if this is even needed, and if not, remove "//tools/base/build-system:gradle-distrib-7.2"
    // from the BUILD file.
    //project.setDistribution("tools/external/gradle/gradle-7.2-bin.zip")

    system.runAdb { adb ->
      system.runEmulator(Emulator.SystemImage.API_31) { emulator ->
        system.runStudioFromApk(project) { studio ->
          studio.waitForIndex()

          println("Waiting for boot")
          emulator.waitForBoot()
          // If you try to run the app too early, you'll see this error in Android Studio: "Error
          // while waiting for device: emu0 is already running. If that is not the case, delete
          // <testtemppath>/home/.android/avd/emu0.avd/*.lock and try again."
          println("Waiting for device")

          // TODO: Tue 11/22/2022 - this sometimes just fails, perhaps because the device is never ready?
          adb.waitForDevice(emulator)

          // TODO: figure out how to definitively tell that the emulator is ready to run the app
          println("Waiting for 10000 seconds before running the app so that the emulator is ready")
          Thread.sleep(10000)

          println("Running the app")
          studio.executeAction("Run")

          adb.runCommand("logcat") {
            waitForLog(".*Hello Minimal World!.*", 600.seconds);
          }
        }
      }
    }
  }
}