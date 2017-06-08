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
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.configurations.FlatAction
import com.android.tools.idea.uibuilder.model.NlModel
import com.android.tools.idea.uibuilder.surface.DesignSurface
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import icons.AndroidIcons

import com.android.tools.idea.configurations.ConfigurationListener.CFG_LOCALE

/**
 * Action that sets the layout direction in the layout editor
 */
class RtlAction(private val mySurface: DesignSurface) : FlatAction() {

  init {
    val presentation = templatePresentation
    presentation.description = "RTL setting in the editor"
    presentation.icon = AndroidIcons.Configs.LayoutDirection
    updatePresentation(presentation)
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    updatePresentation(e.presentation)
  }

  override fun displayTextInToolbar(): Boolean = true

  private fun updatePresentation(presentation: Presentation) {
    val configuration = mySurface.configuration
    val visible = configuration != null
    if (visible) {
      val brief = getRtlLabel(configuration!!)
      presentation.setText(brief, false)
    }
    if (visible != presentation.isVisible) {
      presentation.isVisible = visible
    }
  }

  /**
   * Returns the current layout direction qualifier as a label ("RTL" or "LTR")
   */
  private fun getRtlLabel(configuration: Configuration): String {
    val qualifier = configuration.fullConfig.layoutDirectionQualifier

    return if (qualifier != null) qualifier.value.shortDisplayValue else LayoutDirection.RTL.shortDisplayValue
  }

  override fun actionPerformed(e: AnActionEvent) {
    val configuration = mySurface.configuration
    val model = mySurface.model
    if (configuration == null || model == null) {
      return
    }

    val current = if (configuration.fullConfig.layoutDirectionQualifier != null)
      configuration.fullConfig.layoutDirectionQualifier!!.value
    else
      LayoutDirection.LTR
    configuration.editedConfig.layoutDirectionQualifier = LayoutDirectionQualifier(if (current == LayoutDirection.RTL) LayoutDirection.LTR else LayoutDirection.RTL)

    // Notify the change and update so the surface is updated
    configuration.updated(CFG_LOCALE)
    model.notifyModified(NlModel.ChangeType.CONFIGURATION_CHANGE)
  }
}
