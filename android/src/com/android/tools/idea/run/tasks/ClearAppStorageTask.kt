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
package com.android.tools.idea.run.tasks

import com.android.ddmlib.CollectingOutputReceiver
import com.android.ddmlib.IDevice
import com.android.tools.idea.execution.common.RunConfigurationNotifier
import com.android.tools.idea.run.tasks.LaunchTaskDurations.CLEAR_APP_DATA
import org.jetbrains.android.util.AndroidBundle

/**
 * A [LaunchTask] that clears app storage data.
 *
 * If the app is installed on the device, executes `pm clear <package>`.
 */
class ClearAppStorageTask(private val packageName: String) : LaunchTask {
  override fun getDescription(): String = AndroidBundle.message("android.launch.task.clear.app.data.description")

  override fun getDuration(): Int = CLEAR_APP_DATA

  override fun getId(): String = "CLEAR_APP_STORAGE_TASK"

  override fun run(launchContext: LaunchContext) {
    val device = launchContext.device

    val packageList = device.shellToString("pm list packages $packageName")
    if (packageList.contains("^package:${packageName.replace(".", "\\.")}$".toRegex())) {
      val result = device.shellToString("pm clear $packageName").trim()
      if (result != "Success") {
        val message = AndroidBundle.message("android.launch.task.clear.app.data.error", packageName, device)
        RunConfigurationNotifier.notifyWarning(launchContext.env.project, "", message)
      }
    }
  }
}

private fun IDevice.shellToString(command: String): String {
  val receiver = CollectingOutputReceiver()
  executeShellCommand(command, receiver)
  return receiver.output
}
