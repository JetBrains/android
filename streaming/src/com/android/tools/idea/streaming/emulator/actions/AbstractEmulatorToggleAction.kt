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
package com.android.tools.idea.streaming.emulator.actions

import com.android.tools.idea.streaming.emulator.EmulatorConfiguration
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import java.util.function.Predicate

/**
 * Common superclass for toggle toolbar actions for embedded emulators.
 *
 * @param configFilter determines the types of devices the action is applicable to
 */
internal abstract class AbstractEmulatorToggleAction(private val configFilter: Predicate<EmulatorConfiguration>? = null) : ToggleAction() {

  override fun update(event: AnActionEvent) {
    val presentation = event.presentation
    if (configFilter != null) {
      presentation.isVisible = getEmulatorConfig(event)?.let(configFilter::test) ?: false
    }
    presentation.isEnabled = presentation.isVisible && isEnabled(event)
  }

  protected open fun isEnabled(event: AnActionEvent): Boolean = isEmulatorConnected(event)
}
