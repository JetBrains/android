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
package com.android.tools.idea.uibuilder.surface

import com.android.tools.idea.actions.LAYOUT_VALIDATOR_KEY
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.project.DumbAwareAction
import icons.StudioIcons

/**
 * Action to toggle layout validation in [NlDesignSurface].
 * For now, all icons are temporary.
 */
class LayoutValidatorAction: DumbAwareAction(
  "Layout Validation On/Off", "Run the layout validation",
  StudioIcons.Shell.Toolbar.STOP) {

  private var enableLayoutValidation = false

  override fun update(e: AnActionEvent) {
    e.presentation.isVisible = StudioFlags.NELE_LAYOUT_VALIDATOR_IN_EDITOR.get()
    updateIcon(e)
  }

  private fun updateIcon(e: AnActionEvent) {
    e.presentation.icon =
      if (enableLayoutValidation) StudioIcons.Shell.Toolbar.RUN
      else StudioIcons.Shell.Toolbar.STOP
  }

  override fun actionPerformed(e: AnActionEvent) {
    val editor = e.getData(CommonDataKeys.EDITOR)
    enableLayoutValidation = !enableLayoutValidation
    updateIcon(e)

    e.getData(LAYOUT_VALIDATOR_KEY)?.setLayoutValidationEnabled(enableLayoutValidation)
  }
}

/**
 * Controller for Layout Validator. It can enable or disable layout validation.
 */
interface LayoutValidatorControl {
  fun setLayoutValidationEnabled(enableLayoutValidation: Boolean)
}