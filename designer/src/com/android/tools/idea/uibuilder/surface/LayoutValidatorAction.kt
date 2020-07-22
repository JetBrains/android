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

import com.android.tools.idea.actions.DesignerActions
import com.android.tools.idea.actions.LAYOUT_VALIDATOR_KEY
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.ui.alwaysEnableLayoutValidation
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import icons.StudioIcons.LayoutEditor.Toolbar.ACCESSIBILITY

/**
 * Action to toggle layout validation in [NlDesignSurface].
 * For now, all icons are temporary.
 */
class LayoutValidatorAction: DumbAwareAction(
  "Run accessibility scanner", "Run accessibility testing framework scanner on current layout",
  ACCESSIBILITY) {

  companion object {
    @JvmStatic
    fun getInstance(): LayoutValidatorAction {
      return ActionManager.getInstance().getAction(
        DesignerActions.ACTION_RUN_LAYOUT_VALIDATOR) as LayoutValidatorAction
    }
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isVisible = StudioFlags.NELE_LAYOUT_VALIDATOR_IN_EDITOR.get()
  }

  override fun actionPerformed(e: AnActionEvent) {
    val controller = e.getData(LAYOUT_VALIDATOR_KEY) ?: return
    controller.runLayoutValidation()
  }
}

/**
 * Controller for validator that checks the layout and produces lint checks.
 * It runs Accessibility Testing Framework.
 */
interface LayoutValidatorControl {
  /** Return the validator capable of checking the layout. */
  val validator: NlLayoutValidator

  /** Trigger the validation, and show lint results */
  fun runLayoutValidation()
}

/** Configuration for layout validator */
interface LayoutValidationConfiguration {

  /** Returns true if it layout validation should be enabled. False otherwise. */
  var isLayoutValidationEnabled: Boolean

  /** Returns true if metric is for render result, which contains atf result, must be logged. */
  var forceLoggingRenderResult: Boolean

  companion object {

    /** Configuration for when layout validation is not applicable. */
    @JvmStatic
    val DISABLED = object: LayoutValidationConfiguration {
      override var isLayoutValidationEnabled: Boolean
        get() = false
        set(value) { }

      override var forceLoggingRenderResult: Boolean
        get() = false
        set(value) { }
    }
  }
}

/** Configuration for when layout validation is available. */
class LayoutValidationEnabled : LayoutValidationConfiguration {

  private var isEnabled = false

  override var isLayoutValidationEnabled: Boolean
    get() = alwaysEnableLayoutValidation || isEnabled
    set(value) {
      isEnabled = value
    }
  override var forceLoggingRenderResult: Boolean = false
}