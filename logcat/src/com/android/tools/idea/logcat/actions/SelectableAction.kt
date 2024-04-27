/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.logcat.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.util.NlsActions.ActionText

/**
 * An action that can display a checkmark to indicate it is selected.
 *
 * This class is based on [com.intellij.openapi.actionSystem.ToggleAction]. ToggleAction isn't quite
 * suited for this. It is very well suited for an action that you click to set, and click again to
 * unset. But these work differently. You click to set but clicking again does not unset. It's
 * possible to use it but results in awkward code.
 */
abstract class SelectableAction(text: @ActionText String) : AnAction(text), Toggleable {

  abstract fun isSelected(): Boolean

  override fun update(e: AnActionEvent) {
    Toggleable.setSelected(e.presentation, isSelected())
  }
}
