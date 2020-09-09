/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.deviceManager.avdmanager.emulator

import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.internal.avd.AvdManager
import com.android.tools.analytics.CommonMetricsData.applicationBinaryInterfaceFromString
import com.android.tools.analytics.UsageTracker.log
import com.android.tools.idea.deviceManager.avdmanager.AvdManagerConnection
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AvdLaunchEvent
import com.google.wireless.android.sdk.stats.AvdLaunchEvent.AvdClass
import com.google.wireless.android.sdk.stats.AvdLaunchEvent.LaunchType
import com.google.wireless.android.sdk.stats.DeviceInfo
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import java.util.ArrayList
import java.util.function.Consumer

class EmulatorRunner(private val commandLine: GeneralCommandLine, avdInfo: AvdInfo?) {
  private var processHandler: ProcessHandler? = null
  private val extraListeners: MutableList<ProcessListener> = ArrayList()

  @Throws(ExecutionException::class)
  fun start(): ProcessHandler {
    val process = commandLine.createProcess()
    EmulatorProcessHandler(process, commandLine).apply {
      extraListeners.forEach(Consumer { addProcessListener(it) })
      startNotify()
      return this
    }
  }

  /**
   * Adds a listener to our process (if it's started already), or saves the listener away to be added when the process is started.
   */
  fun addProcessListener(listener: ProcessListener) {
    processHandler?.addProcessListener(listener) ?: extraListeners.add(listener)
  }

  companion object {
    private fun getLaunchType(commandLine: GeneralCommandLine): LaunchType =
      if (commandLine.parametersList.parameters.contains("-no-window")) LaunchType.IN_TOOL_WINDOW
      else LaunchType.STANDALONE

    private fun getAvdClass(avdInfo: AvdInfo?): AvdClass {
      if (avdInfo == null) {
        return AvdClass.UNKNOWN_AVD_CLASS
      }

      val tag = avdInfo.getProperty(AvdManager.AVD_INI_TAG_ID)
      return when {
        "android-tv" == tag -> AvdClass.TV
        "android-automotive" == tag -> AvdClass.AUTOMOTIVE
        "android-wear" == tag -> AvdClass.WEARABLE
        AvdManagerConnection.isFoldable(avdInfo) -> AvdClass.FOLDABLE
        else -> AvdClass.GENERIC
      }
    }
  }

  init {
    val event = AndroidStudioEvent.newBuilder()
      .setCategory(AndroidStudioEvent.EventCategory.DEPLOYMENT)
      .setKind(AndroidStudioEvent.EventKind.DEPLOYMENT_TO_EMULATOR)
      .setAvdLaunchEvent(
        AvdLaunchEvent.newBuilder()
          .setLaunchType(getLaunchType(commandLine))
          .setAvdClass(getAvdClass(avdInfo))
      )
    if (avdInfo != null) {
      event.setDeviceInfo(
        DeviceInfo.newBuilder()
          .setCpuAbi(applicationBinaryInterfaceFromString(avdInfo.abiType))
          .setBuildApiLevelFull(avdInfo.androidVersion.toString())
      )
    }
    log(event)
  }
}