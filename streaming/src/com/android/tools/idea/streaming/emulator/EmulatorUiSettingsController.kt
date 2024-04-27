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
package com.android.tools.idea.streaming.emulator

import com.android.adblib.DeviceSelector
import com.android.adblib.ShellCommandOutputElement
import com.android.adblib.shellAsLines
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.streaming.uisettings.ui.UiSettingsController
import com.android.tools.idea.streaming.uisettings.ui.UiSettingsModel
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch

private const val DARK_MODE_ON = "Night mode: yes"
private const val DARK_MODE_OFF = "Night mode: no"

/**
 * A controller for the UI settings for an Emulator,
 * that populates the model and reacts to changes to the model initiated by the UI.
 */
internal class EmulatorUiSettingsController(
  private val project: Project,
  private val deviceSerialNumber: String,
  model: UiSettingsModel,
  parentDisposable: Disposable
) : UiSettingsController(model) {
  private val scope = AndroidCoroutineScope(parentDisposable)

  override suspend fun populateModel() {
    executeShellCommand("cmd uimode night") { processAdbOutput(it) }
  }

  private fun processAdbOutput(output: List<String>) {
    output.forEach {
      when (it) {
        DARK_MODE_ON -> model.inDarkMode.setFromController(true)
        DARK_MODE_OFF -> model.inDarkMode.setFromController(false)
      }
    }
  }

  override fun setDarkMode(on: Boolean) {
    val darkMode = if (on) "yes" else "no"
    scope.launch { executeShellCommand("cmd uimode night $darkMode") }
  }

  private suspend fun executeShellCommand(command: String, commandProcessor: (lines: List<String>) -> Unit = {}) {
    val adb = AdbLibService.getSession(project).deviceServices
    val output = adb.shellAsLines(DeviceSelector.fromSerialNumber(deviceSerialNumber), command)
    val lines = output.filterIsInstance<ShellCommandOutputElement.StdoutLine>().map { it.contents }.toList()
    commandProcessor.invoke(lines)
  }
}
