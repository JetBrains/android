/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.ui.toolbar.actions

import com.android.tools.idea.layoutinspector.ui.RenderModel
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.util.ui.JBUI
import java.awt.FlowLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSlider

private const val SLIDER_KEY = "SliderKey"
const val INITIAL_ALPHA_PERCENT = 60

class AlphaSliderAction(private val renderModelProvider: () -> RenderModel) :
  AnAction(), CustomComponentAction {
  override fun actionPerformed(event: AnActionEvent) {
    val component =
      event.presentation.getClientProperty(CustomComponentAction.COMPONENT_KEY) ?: return
    val slider = component.getClientProperty(SLIDER_KEY) as JSlider
    // The event for Custom components actions are constructed differently than normal actions.
    // If this action is shown in a popup toolbar (when there is not enough space to show the whole
    // toolbar in-place),
    // go through the action toolbar data context to find the model.
    renderModelProvider().overlayAlpha = slider.value / 100.0f
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    val panel = JPanel(FlowLayout(FlowLayout.CENTER, JBUI.scale(5), 0))
    panel.add(JLabel("Overlay Alpha:"))
    val slider = JSlider(JSlider.HORIZONTAL, 0, 100, INITIAL_ALPHA_PERCENT)
    slider.addChangeListener {
      val dataContext = DataManager.getInstance().getDataContext(slider)
      actionPerformed(
        AnActionEvent.createFromDataContext(ActionPlaces.TOOLBAR, presentation, dataContext)
      )
    }
    panel.add(slider)
    panel.putClientProperty(SLIDER_KEY, slider)
    return panel
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val hasOverlay = (renderModelProvider().overlay != null)
    e.presentation.isVisible = hasOverlay
  }
}
