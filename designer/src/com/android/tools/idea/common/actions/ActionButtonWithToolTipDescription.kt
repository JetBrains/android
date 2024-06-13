/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.common.actions

import com.intellij.ide.HelpTooltip
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.ActionButton

/**
 * An [ActionButton] with a ToolTip that includes extended description as opposed to the usual
 * [ActionButton] where ToolTip only includes title text.
 */
class ActionButtonWithToolTipDescription(
  action: AnAction,
  presentation: Presentation,
  place: String,
) : ActionButton(action, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {
  override fun updateToolTipText() {
    HelpTooltip.dispose(this)
    HelpTooltip()
      .setTitle(myPresentation.text)
      .setDescription(myPresentation.description)
      .installOn(this)
  }
}
