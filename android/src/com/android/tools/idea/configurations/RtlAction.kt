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
package com.android.tools.idea.configurations

import com.android.ide.common.resources.configuration.LayoutDirectionQualifier
import com.android.resources.LayoutDirection
import com.android.tools.idea.configurations.ConfigurationListener.CFG_LOCALE
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import icons.AndroidIcons

/**
 * Action that sets the layout direction in the layout editor
 */
class RtlAction(private val holder: ConfigurationHolder) : AnAction() {

  init {
    val presentation = templatePresentation
    with(presentation) {
      description = "Text direction setting in the editor"
      icon = AndroidIcons.Configs.LayoutDirection
    }
    updatePresentation(presentation)
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    updatePresentation(e.presentation)
  }

  override fun displayTextInToolbar(): Boolean = true

  private fun updatePresentation(presentation: Presentation) {
    val configuration = holder.configuration
    presentation.isEnabledAndVisible = configuration != null
    if (!presentation.isEnabledAndVisible) {
      return
    }

    val qualifier = configuration!!.fullConfig.layoutDirectionQualifier
    val brief = "Preview as ${getOppositeDirection(qualifier).longDisplayValue}"
    presentation.setText(brief, false)
  }

  /**
   * Returns the opposite direction to the one passed or LTR if none is passed
   */
  private fun getOppositeDirection(direction: LayoutDirectionQualifier?): LayoutDirection {
    return when (direction?.value) {
      LayoutDirection.LTR -> LayoutDirection.RTL
      else -> LayoutDirection.LTR
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val configuration = holder.configuration ?: return
    configuration.editedConfig.layoutDirectionQualifier = LayoutDirectionQualifier(getOppositeDirection(configuration.fullConfig.layoutDirectionQualifier))

    // Notify the change and update so the surface is updated
    configuration.updated(CFG_LOCALE)
  }
}
