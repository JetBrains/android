/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.naveditor.actions

import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.roots.ui.configuration.actions.IconWithTextAction
import javax.swing.Icon
import javax.swing.JComponent

abstract class ToolbarAction(description: String, icon: Icon) :
  IconWithTextAction("", description, icon) {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  private var buttonPresentation: Presentation? = null

  override fun createCustomComponent(presentation: Presentation): JComponent {
    buttonPresentation = presentation
    return super.createCustomComponent(presentation, ActionPlaces.UNKNOWN)
  }

  protected abstract fun isEnabled(surface: NavDesignSurface): Boolean

  override fun update(e: AnActionEvent) {
    super.update(e)
    val surface = e.getData(DESIGN_SURFACE) as? NavDesignSurface
    buttonPresentation?.isEnabled = surface?.let { isEnabled(it) } ?: false
  }
}
