/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.actions

import com.android.ide.common.resources.configuration.LayoutDirectionQualifier
import com.android.resources.LayoutDirection
import com.android.tools.configurations.ConfigurationListener.CFG_LOCALE
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction

private const val TEXT = "Preview Right to Left"
private const val DESCRIPTION = "Text direction setting in the editor"

/** Action that sets the layout direction in the layout editor */
class RtlAction : ToggleAction(TEXT, DESCRIPTION, null) {

  override fun displayTextInToolbar(): Boolean = true

  override fun isSelected(e: AnActionEvent) =
    e.getData(CONFIGURATIONS)?.firstOrNull()?.fullConfig?.layoutDirectionQualifier?.value ==
      LayoutDirection.RTL

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val configuration = e.getData(CONFIGURATIONS)?.firstOrNull() ?: return
    configuration.editedConfig.layoutDirectionQualifier =
      LayoutDirectionQualifier(if (state) LayoutDirection.RTL else LayoutDirection.LTR)
    // Notify the change and update so the surface is updated
    configuration.updated(CFG_LOCALE)
  }
}
