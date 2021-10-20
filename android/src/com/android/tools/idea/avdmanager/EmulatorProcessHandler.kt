/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.avdmanager

import com.intellij.execution.configurations.GeneralCommandLine
import com.android.tools.idea.avdmanager.EmulatorProcessHandler.ConsoleListener
import com.intellij.execution.process.ProcessTerminatedListener
import com.android.tools.idea.avdmanager.EmulatorProcessHandler
import com.intellij.execution.process.BaseOSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Key
import com.intellij.util.io.BaseOutputReader

private val LOG = Logger.getInstance(EmulatorProcessHandler::class.java)

/**
 * A process handler for the emulator process.
 */
class EmulatorProcessHandler(
  process: Process,
  commandLine: GeneralCommandLine
) : BaseOSProcessHandler(process, commandLine.commandLineString, null) {

  init {
    addProcessListener(ConsoleListener())
    ProcessTerminatedListener.attach(this)
  }

  override fun readerOptions(): BaseOutputReader.Options =
      BaseOutputReader.Options.forMostlySilentProcess()

  private inner class ConsoleListener : ProcessAdapter() {

    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
      val text = event.text
      if (text != null) {
        LOG.info("Emulator: " + text.trim { it <= ' ' })
      }
      if (ProcessOutputType.SYSTEM == outputType && isProcessTerminated) {
        val exitCode = exitCode
        if (exitCode != null && exitCode != 0) {
          LOG.warn("Emulator terminated with exit code $exitCode")
        }
      }
    }
  }
}