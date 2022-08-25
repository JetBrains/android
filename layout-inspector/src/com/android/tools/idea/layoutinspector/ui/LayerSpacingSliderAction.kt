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
package com.android.tools.idea.layoutinspector.ui

import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
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
const val INITIAL_LAYER_SPACING = 150
const val MAX_LAYER_SPACING = 500

object LayerSpacingSliderAction : AnAction(), CustomComponentAction {
  override fun actionPerformed(event: AnActionEvent) {
    val component = event.presentation.getClientProperty(CustomComponentAction.COMPONENT_KEY) ?: return
    val slider = component.getClientProperty(SLIDER_KEY) as JSlider
    // The event for Custom components actions are constructed differently than normal actions.
    // If this action is shown in a popup toolbar (when there is not enough space to show the whole toolbar in-place),
    // go through the action toolbar data context to find the model.
    val model = ActionToolbar.getDataContextFor(component).getData(DEVICE_VIEW_MODEL_KEY) ?: return
    model.layerSpacing = slider.value
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    val panel = JPanel(FlowLayout(FlowLayout.CENTER, JBUI.scale(5), 0))
    panel.add(JLabel("Layer Spacing:"))
    val slider = JSlider(JSlider.HORIZONTAL, 0, MAX_LAYER_SPACING, INITIAL_LAYER_SPACING)
    slider.addChangeListener {
      val dataContext = DataManager.getInstance().getDataContext(slider)
      actionPerformed(AnActionEvent.createFromDataContext(ActionPlaces.TOOLBAR, presentation, dataContext))
    }
    panel.add(slider)
    panel.putClientProperty(SLIDER_KEY, slider)
    return panel
  }

  override fun update(e: AnActionEvent) {
    val client = LayoutInspector.get(e)?.currentClient
    e.presentation.isVisible = client?.capabilities?.contains(InspectorClient.Capability.SUPPORTS_SKP) ?: false
    val model = e.getData(DEVICE_VIEW_MODEL_KEY)
    e.presentation.isEnabled = model?.isRotated == true
    val component = e.presentation.getClientProperty(CustomComponentAction.COMPONENT_KEY) as? JPanel ?: return
    component.components.forEach { it.isEnabled = e.presentation.isEnabled }
  }
}