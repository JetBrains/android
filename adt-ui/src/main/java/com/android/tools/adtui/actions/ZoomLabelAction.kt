/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.adtui.actions

import com.android.tools.adtui.ZOOMABLE_KEY
import com.android.tools.adtui.Zoomable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.UIUtil
import java.beans.PropertyChangeListener
import java.util.Locale
import javax.swing.JComponent

/**
 * Action which shows a zoom percentage
 */
object ZoomLabelAction : AnAction(), CustomComponentAction {

  init {
    val presentation = templatePresentation
    presentation.description = "Current Zoom Level"
    updatePresentation(presentation, null)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    event.getData(ZOOMABLE_KEY)?.let { updatePresentation(event.presentation, it) }
  }

  override fun actionPerformed(event: AnActionEvent) {
    // No-op: only label matters
  }

  private fun updatePresentation(presentation: Presentation, zoomable: Zoomable?) {
    val scale = if (zoomable != null) zoomable.scale * zoomable.screenScalingFactor else 1.0

    val label = String.format(Locale.ROOT, "%d%% ", (100 * scale).toInt())
    presentation.text = label
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    val label = object : JBLabel() {
      private val presentationSyncer: PropertyChangeListener = PropertyChangeListener { evt ->
        val propertyName = evt.propertyName
        if (Presentation.PROP_TEXT == propertyName) {
          text = evt.newValue as String
          parent.parent.validate()
          repaint()
        }
      }

      override fun addNotify() {
        super.addNotify()
        presentation.addPropertyChangeListener(presentationSyncer)
        text = presentation.text
        parent.parent.validate()
      }

      override fun removeNotify() {
        presentation.removePropertyChangeListener(presentationSyncer)
        super.removeNotify()
      }
    }
    label.font = UIUtil.getToolTipFont()
    label.name = "Current Zoom Level"
    return label
  }
}
