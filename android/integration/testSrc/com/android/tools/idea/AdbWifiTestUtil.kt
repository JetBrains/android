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
package com.android.tools.idea

import com.android.tools.testlib.Adb
import com.android.tools.testlib.Emulator
import java.util.concurrent.TimeUnit

internal data class PairingData(val pairingPort: String, val pairingCode: String, val tlsConnectionPort: String)

internal fun setupAdbWifi(adb: Adb, emulator: Emulator) {
  emulator.waitForBoot()
  adb.waitForDevice(emulator)
  checkAndDismissSystemUiNotRespondingDialog(adb, emulator)
  connectToWifi(adb, emulator)
  enableAdbWifi(adb, emulator)
}

internal fun verifyTlsServiceAdvertised(adb: Adb, emulator: Emulator) {
  val tlsPortRegex = ".*text=\".*:(\\d+)\".*resource-id=\"android:id/summary\".*"
  val tlsPort = adb.extractUiElement(tlsPortRegex, emulator, 60)
  println("TLS Port: $tlsPort")

  adb.runCommand("mdns", "track-services", "--proto-text").use { devices ->
    val tlsConnectRegexes = listOf(".*service: \"_adb-tls-connect._tcp\".*", ".*port: ${tlsPort}.*")
    devices.waitForLogs(tlsConnectRegexes, 60, TimeUnit.SECONDS)
  }
}

internal fun setupPortForwarding(adb: Adb, emulator: Emulator, pairingData: PairingData) {
  adb.runCommand("forward", "tcp:${pairingData.pairingPort}", "tcp:${pairingData.pairingPort}", emulator = emulator)
  adb.runCommand("forward", "tcp:${pairingData.tlsConnectionPort}", "tcp:${pairingData.tlsConnectionPort}", emulator = emulator)
}

internal fun fetchPairingData(adb: Adb, emulator: Emulator): PairingData {
  var tlsConnectionPort = ""
  var pairingPort = ""

  adb.runCommand("mdns", "track-services", "--proto-text").use { devices ->
    // Capture tls port
    val tlsConnectRegexes = listOf(".*service: \"_adb-tls-connect._tcp\".*", ".*port: (\\d+).*")
    val tlsConnectMatchers = devices.waitForLogs(tlsConnectRegexes, 60, TimeUnit.SECONDS)
    tlsConnectionPort = tlsConnectMatchers[1].group(1)
    println("TLS Connect port: $tlsConnectionPort")

    // Capture Pairing Port
    val pairingRegexes = listOf(".*service: \"_adb-tls-pairing._tcp\".*", ".*port: (\\d+).*")
    val pairingMatchers = devices.waitForLogs(pairingRegexes, 60, TimeUnit.SECONDS)
    pairingPort = pairingMatchers[1].group(1)
    println("Pairing port: $pairingPort")
  }
  sleep()

  // Capture Pairing Code
  val pairingCodeRegex = ".*text=\"(\\d+)\".*resource-id=\"com\\.android\\.settings:id/pairing_code\".*"
  val pairingCode = adb.extractUiElement(pairingCodeRegex, emulator)
  println("Pairing Code: $pairingCode")

  return PairingData(pairingPort = pairingPort, pairingCode = pairingCode, tlsConnectionPort = tlsConnectionPort)
}

internal fun startPairingCodePairing(adb: Adb, emulator: Emulator) {
  val regex = ".*text=\"Pair device with pairing code\"[^>]*?bounds=\"([^\"]+)\".*"
  adb.clickUiElement(regex, emulator)
}

// See b/496156110 for more information.
private fun checkAndDismissSystemUiNotRespondingDialog(adb: Adb, emulator: Emulator) {
  for (i in 1..10) {
    try {
      adb.runCommand("exec-out", "uiautomator", "dump", "/dev/tty", emulator = emulator).use { output ->
        val regex = ".*System UI isn't responding.*text=\"Wait\"[^>]*?bounds=\"([^\"]+)\".*"
        try {
          val matcher = output.waitForLog(regex, 5, TimeUnit.SECONDS)
          println("System UI isn't responding detected, attempting to dismiss (attempt $i of 10)...")
          val bounds = matcher.group(1)
          val boundsRegex = "\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]".toRegex()
          val matchResult = boundsRegex.find(bounds) ?: return@use
          val (x1, y1, x2, y2) = matchResult.destructured
          val x = (x1.toInt() + x2.toInt()) / 2
          val y = (y1.toInt() + y2.toInt()) / 2
          adb.runCommandAndSleep("shell", "input", "tap", "$x", "$y", emulator = emulator)
          return
        } catch (_: Exception) {
          // Dialog not found or "Wait" button not found.
          println("System UI isn't responding not detected (attempt $i of 10).")
        }
      }
    } catch (_: Exception) {
      println("uiautomator dump failed (attempt $i of 10).")
    }
    sleep()
  }
}

private fun Adb.clickUiElement(regex: String, emulator: Emulator, timeoutSeconds: Long = 30) {
  runCommand("exec-out", "uiautomator", "dump", "/dev/tty", emulator = emulator).use { output ->
    val matcher = output.waitForLog(regex, timeoutSeconds, TimeUnit.SECONDS)
    val bounds = matcher.group(1)
    val boundsRegex = "\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]".toRegex()
    val matchResult = boundsRegex.find(bounds) ?: throw RuntimeException("Failed to parse bounds: $bounds")
    val (x1, y1, x2, y2) = matchResult.destructured
    val x = (x1.toInt() + x2.toInt()) / 2
    val y = (y1.toInt() + y2.toInt()) / 2
    runCommandAndSleep("shell", "input", "tap", "$x", "$y", emulator = emulator)
  }
}

private fun enableAdbWifi(adb: Adb, emulator: Emulator) {
  // enable developer settings first
  adb.runCommandAndSleep("shell", "settings", "put", "global", "development_settings_enabled", "1", emulator = emulator)
  // root needed to launch wireless debugging activity.
  adb.runCommandAndSleep("root", emulator = emulator)
  adb.runCommandAndSleep("shell", "am", "start", "-a", "android.settings.DEVICE_INFO_SETTINGS", emulator = emulator)
  adb.runCommandAndSleep(
    "shell",
    "am",
    "start",
    "-n",
    "com.android.settings/.SubSettings",
    "-e",
    ":settings:show_fragment",
    "com.android.settings.development.AdbWirelessDebuggingFragment",
    emulator = emulator,
  )
  adb.runCommandAndSleep("shell", "settings", "put", "global", "adb_wifi_enabled", "1", emulator = emulator)
}

internal fun allowAdbWifi(adb: Adb, emulator: Emulator, alwaysAllow: Boolean = false) {
  if (alwaysAllow) {
    adb.clickUiElement(".*text=\"Always allow on this network\"[^>]*?bounds=\"([^\"]+)\".*", emulator)
  }
  adb.clickUiElement(".*text=\"Allow\"[^>]*?bounds=\"([^\"]+)\".*", emulator)
}

internal fun disableWifi(adb: Adb, emulator: Emulator) {
  adb.runCommandAndSleep("shell", "svc", "wifi", "disable", emulator = emulator)
}

internal fun connectToWifi(adb: Adb, emulator: Emulator) {
  for (i in 1..5) {
    try {
      adb.runCommandAndSleep("shell", "svc", "wifi", "enable", emulator = emulator)
      adb.runCommandAndSleep("shell", "cmd", "wifi", "connect-network", "AndroidWifi", "open", emulator = emulator)
      adb.runCommand("shell", "cmd", "wifi", "status", emulator = emulator).use { output ->
        output.waitForLog(".*Wifi is connected to.*", 5, TimeUnit.SECONDS)
        return
      }
    } catch (e: Exception) {
      if (i == 5) {
        throw AssertionError("Wifi did not connect", e)
      }
      sleep()
    }
  }
}

private fun Adb.extractUiElement(regex: String, emulator: Emulator, timeoutSeconds: Long = 30): String {
  runCommand("exec-out", "uiautomator", "dump", "/dev/tty", emulator = emulator).use { output ->
    val matcher = output.waitForLog(regex, timeoutSeconds, TimeUnit.SECONDS)
    return matcher.group(1)
  }
}

private fun sleep(millis: Long = 20000) {
  Thread.sleep(millis)
}

private fun Adb.runCommandAndSleep(vararg command: String, emulator: Emulator) {
  runCommand(*command, emulator = emulator)
  sleep()
}
