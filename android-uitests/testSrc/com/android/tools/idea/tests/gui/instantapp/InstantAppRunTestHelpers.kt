/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.instantapp

import com.android.adblib.AdbSession
import com.android.adblib.ConnectedDevice
import com.android.adblib.DeviceState
import com.android.adblib.ShellCommandOutputElement
import com.android.adblib.connectedDevicesTracker
import com.android.adblib.deviceProperties
import com.android.adblib.shellCommand
import com.android.adblib.withLineCollector
import com.android.tools.idea.file.explorer.toolwindow.adbimpl.AdbDeviceCapabilities
import com.android.tools.idea.file.explorer.toolwindow.adbimpl.AdbFileOperations
import com.android.tools.idea.file.explorer.toolwindow.adbimpl.selector
import java.io.File
import java.time.Duration
import java.util.concurrent.Executors
import java.util.regex.Pattern
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.time.withTimeout
import org.fest.swing.timing.Wait

internal fun prepareAdbInstall(adbPath: String, vararg apkFiles: File) =
  ProcessBuilder(
    listOf(adbPath, "install-multiple", "-t", "-r", "--ephemeral") +
      apkFiles.map { it.absolutePath }
  )

internal fun waitForAppInstalled(device: ConnectedDevice, appId: String) {
  val exec = Executors.newSingleThreadExecutor()
  val dispatcher = exec.asCoroutineDispatcher()
  runBlocking {
    val adbOps =
      AdbFileOperations(
        device,
        AdbDeviceCapabilities(this + dispatcher, "test device", device),
        dispatcher
      )
    try {
      Wait.seconds(10).expecting("instant app to be listed from `pm packages list`").until {
        try {
          runBlocking {
            withTimeout(Duration.ofSeconds(10)) { adbOps.listPackages().any { appId == it } }
          }
        } catch (interrupt: InterruptedException) {
          Thread.currentThread().interrupt()
          false
        } catch (otherExceptions: Exception) {
          false
        }
      }
    } finally {
      exec.shutdown()
    }
  }
}

// Intent.FLAG_ACTIVITY_MATCH_EXTERNAL is required to launch in P+; ignored in pre-O.
internal fun prepareAdbInstantAppLaunchIntent(adbPath: String) =
  ProcessBuilder(
    listOf(
      adbPath,
      "shell",
      "am",
      "start",
      "-f",
      "0x00000800",
      "-n",
      "com.google.samples.apps.topeka/.activity.SignInActivity"
    )
  )

internal fun firstDevice(session: AdbSession): ConnectedDevice? =
  session.connectedDevicesTracker.connectedDevices.value.firstOrNull()

internal fun isOnline(device: ConnectedDevice) = runBlocking {
  device.deviceInfoFlow.value.deviceState == DeviceState.ONLINE &&
    device.deviceProperties().all().any { it.name == "dev.bootcomplete" }
}

internal fun isActivityWindowOnTop(dev: ConnectedDevice, activityComponentName: String): Boolean {
  val expectedComp = Component.fromString(activityComponentName)

  // The line containing "mResumedActivity" has information on the top activity
  val resumedActivityMatcher = Pattern.compile("^mResumedActivity")

  fun isMatchingActivity(line: String): Boolean {
    val m = resumedActivityMatcher.matcher(line)
    return when {
      m.find() && m.end() < line.length -> {
        // Slice the string apart to extract the application ID and activity's full name
        val componentNameStr = parseComponentNameFromResumedActivityLine(line)
        val parsedComp = Component.fromString(componentNameStr)

        expectedComp == parsedComp
      }
      else -> false
    }
  }

  return runBlocking {
    dev.session.deviceServices
      .shellCommand(dev.selector, "dumpsys activity activities")
      .withLineCollector()
      .withCommandTimeout(Duration.ofSeconds(30))
      .execute()
      .firstOrNull {
        when (it) {
          is ShellCommandOutputElement.StdoutLine -> isMatchingActivity(it.contents)
          else -> false
        }
      } != null
  }
}

/**
 * The output from dumpsys looks like
 *
 * mResumedActivity: ActivityRecord{285ebc u0 com.android.settings/.CryptKeeper t1}
 *
 * Given the above example, we want to parse the line and return "com.android.settings/.CryptKeeper"
 */
internal fun parseComponentNameFromResumedActivityLine(line: String): String {
  val lineTokens = line.trim().split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }
  require(lineTokens.size == 5) { "line does not split into 5 tokens. line = $line" }

  return lineTokens[3]
}

internal data class Component(private val applicationId: String, private val componentClassName: String) {
  companion object {
    fun fromString(componentName: String): Component {
      val componentNameTokens = componentName.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

      // Need exactly 2 tokens. Activity component names are of the form
      // {application ID}/{Activity class name}
      require(componentNameTokens.size == 2) { "Component names must be composed of two tokens separated by 1 '/'" }

      val appId = componentNameTokens[0]
      var activityClassName = componentNameTokens[1]

      // Expand the class name if the name is in the shortened version:
      if (activityClassName.startsWith(".")) {
        activityClassName = appId + activityClassName
      }

      return Component(appId, activityClassName)
    }
  }
}
