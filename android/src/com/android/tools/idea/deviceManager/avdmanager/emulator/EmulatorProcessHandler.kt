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

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.BaseOSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Key

private val LOG = logger<EmulatorProcessHandler>()

/**
 * The [EmulatorProcessHandler] is a custom process handler specific to handling the emulator process.
 */
class EmulatorProcessHandler(
  process: Process, commandLine: GeneralCommandLine
) : BaseOSProcessHandler(process, commandLine.commandLineString, null) {
  private inner class ConsoleListener : ProcessAdapter() {
    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
      val text = event.text?.trim()
      if (text != null) {
        LOG.info("Emulator: $text")
      }
      if (ProcessOutputTypes.SYSTEM == outputType && isProcessTerminated) {
        if (exitCode != null && exitCode != 0) {
          LOG.warn("Emulator terminated with exit code $exitCode")
        }
      }
    }
  }

  init {
    addProcessListener(ConsoleListener())
    ProcessTerminatedListener.attach(this)
  }
}