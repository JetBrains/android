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

import com.android.ddmlib.CollectingOutputReceiver
import com.android.ddmlib.IDevice
import com.android.tools.idea.explorer.adbimpl.AdbDeviceCapabilities
import com.android.tools.idea.explorer.adbimpl.AdbFileOperations
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import org.fest.swing.timing.Wait

internal fun prepareAdbInstall(adb: File, vararg apkFiles: File) =
  ProcessBuilder(listOf(adb.absolutePath, "install-multiple", "-t", "-r", "--ephemeral") + apkFiles.map { it.absolutePath })

internal fun waitForAppInstalled(device: IDevice, appId: String) {
  val exec = Executors.newSingleThreadExecutor()
  val adbOps = AdbFileOperations(device, AdbDeviceCapabilities(device), exec)
  try {
    Wait.seconds(10)
      .expecting("instant app to be listed from `pm packages list`")
      .until {
        try {
          adbOps.listPackages().get(10, TimeUnit.SECONDS).orEmpty().any { appId == it }
        }
        catch (interrupt: InterruptedException) {
          Thread.currentThread().interrupt()
          false
        }
        catch (otherExceptions: Exception) {
          false
        }
      }
  }
  finally {
    exec.shutdown()
  }
}

// Intent.FLAG_ACTIVITY_MATCH_EXTERNAL is required to launch in P+; ignored in pre-O.
internal fun prepareAdbInstantAppLaunchIntent(adb: File) = ProcessBuilder(
  listOf(adb.absolutePath, "shell", "am", "start", "-f", "0x00000800", "-n", "com.google.samples.apps.topeka/.activity.SignInActivity"))

internal fun isActivityWindowOnTop(dev: IDevice, activityComponentName: String): Boolean {
  val expectedComp = Component.fromString(activityComponentName)

  val receiver = CollectingOutputReceiver()
  try {
    dev.executeShellCommand("dumpsys activity activities", receiver, 30, TimeUnit.SECONDS)
  }
  catch (cmdFailed: Exception) {
    return false
  }

  val lines = receiver.output.split('\n').dropLastWhile { it.isEmpty() }.toTypedArray()

  // The line containing "mResumedActivity" has information on the top activity
  val resumedActivityMatcher = Pattern.compile("^mResumedActivity")

  return lines.map(String::trim).any {  line ->
    val m = resumedActivityMatcher.matcher(line)
    if (m.find() && m.end() < line.length) {
      // Slice the string apart to extract the application ID and activity's full name
      val componentNameStr = parseComponentNameFromResumedActivityLine(line)
      val parsedComp = Component.fromString(componentNameStr)

      expectedComp == parsedComp
    } else {
      false
    }
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
