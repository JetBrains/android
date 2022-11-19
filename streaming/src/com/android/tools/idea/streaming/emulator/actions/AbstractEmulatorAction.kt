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
package com.android.tools.idea.streaming.emulator.actions

import com.android.tools.idea.streaming.NUMBER_OF_DISPLAYS
import com.android.tools.idea.streaming.emulator.EMULATOR_CONTROLLER_KEY
import com.android.tools.idea.streaming.emulator.EMULATOR_VIEW_KEY
import com.android.tools.idea.streaming.emulator.EmulatorConfiguration
import com.android.tools.idea.streaming.emulator.EmulatorController
import com.android.tools.idea.streaming.emulator.EmulatorView
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import java.util.function.Predicate

/**
 * Common superclass for toolbar actions for embedded emulators.
 *
 * @param configFilter determines the types of devices the action is applicable to
 */
abstract class AbstractEmulatorAction(private val configFilter: Predicate<EmulatorConfiguration>? = null) : AnAction(), DumbAware {

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabled = isEnabled(event)
    if (configFilter != null) {
      event.presentation.isVisible = getEmulatorConfig(event)?.let(configFilter::test) ?: false
    }
  }

  protected open fun isEnabled(event: AnActionEvent): Boolean =
    isEmulatorConnected(event)
}

internal fun getProject(event: AnActionEvent): Project =
  event.getRequiredData(CommonDataKeys.PROJECT)

internal fun getEmulatorController(event: AnActionEvent): EmulatorController? =
  event.getData(EMULATOR_CONTROLLER_KEY)

internal fun getEmulatorConfig(event: AnActionEvent): EmulatorConfiguration? {
  val controller = getEmulatorController(event)
  return if (controller?.connectionState == EmulatorController.ConnectionState.CONNECTED) controller.emulatorConfig else null
}

internal fun getEmulatorView(event: AnActionEvent): EmulatorView? =
  event.getData(EMULATOR_VIEW_KEY)

internal fun getNumberOfDisplays(event: AnActionEvent): Int =
  event.getData(NUMBER_OF_DISPLAYS) ?: 0

internal fun isEmulatorConnected(event: AnActionEvent) =
  getEmulatorController(event)?.connectionState == EmulatorController.ConnectionState.CONNECTED
