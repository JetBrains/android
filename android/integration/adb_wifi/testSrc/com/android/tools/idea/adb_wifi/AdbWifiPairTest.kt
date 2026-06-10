/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.adb_wifi

import com.android.tools.asdriver.tests.AndroidSystem
import com.android.tools.testlib.Emulator
import java.util.concurrent.TimeUnit
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

class AdbWifiPairTest {
  @JvmField @Rule val system: AndroidSystem = AndroidSystem.standard()

  @Ignore("b/512173064")
  @Test
  fun pairAndConnectTest() {
    system.runAdb { adb ->
      adb.runCommand("server-status")
      adb.runCommand("version")
      system.runEmulator(Emulator.SystemImage.API_CANARY_11) { emulator ->
        setupAdbWifi(adb, emulator)
        allowAdbWifi(adb, emulator)
        startPairingCodePairing(adb, emulator)
        val pairingData = fetchPairingData(adb, emulator)

        // See b/479152698 for more information on why we need port forwarding.
        setupPortForwarding(adb, emulator, pairingData)

        adb.runCommand("pair", "localhost:${pairingData.pairingPort}", pairingData.pairingCode, emulator = emulator).use { result ->
          result.waitForLog(".*Successfully paired to localhost:${pairingData.pairingPort}.*", 60, TimeUnit.SECONDS)
        }

        // The adb host needs manual connect after pairing since the adb host doesn't know about the port forwarding needed to overcome
        // the emulator network limitations. See b/479152698 for more information.
        adb.runCommand("connect", "localhost:${pairingData.tlsConnectionPort}", emulator = emulator).use { result ->
          result.waitForLog(".*connected to localhost:${pairingData.tlsConnectionPort}.*", 60, TimeUnit.SECONDS)
        }

        // verify that devices is connected using adb over Wi-Fi
        adb.runCommand("track-devices") { waitForLog("localhost:${pairingData.tlsConnectionPort}\tdevice", 60, TimeUnit.SECONDS) }
      }
    }
  }
}
