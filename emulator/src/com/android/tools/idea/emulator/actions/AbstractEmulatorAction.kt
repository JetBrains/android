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
package com.android.tools.idea.emulator.actions

import com.android.tools.idea.emulator.EMULATOR_CONTROLLER_KEY
import com.android.tools.idea.emulator.EMULATOR_VIEW_KEY
import com.android.tools.idea.emulator.EmulatorController
import com.android.tools.idea.emulator.EmulatorView
import com.android.tools.idea.emulator.NUMBER_OF_DISPLAYS
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware

/**
 * Common superclass for toolbar actions of the Emulator window.
 */
abstract class AbstractEmulatorAction : AnAction(), DumbAware {

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabled = isEnabled(event)
  }

  protected open fun isEnabled(event: AnActionEvent): Boolean =
    isEmulatorConnected(event)
}

internal fun getEmulatorController(event: AnActionEvent): EmulatorController? =
  event.dataContext.getData(EMULATOR_CONTROLLER_KEY)

internal fun getEmulatorView(event: AnActionEvent): EmulatorView? =
  event.dataContext.getData(EMULATOR_VIEW_KEY)

internal fun getNumberOfDisplays(event: AnActionEvent): Int =
  event.dataContext.getData(NUMBER_OF_DISPLAYS) ?: 0

internal fun isEmulatorConnected(event: AnActionEvent) =
  getEmulatorController(event)?.connectionState == EmulatorController.ConnectionState.CONNECTED
