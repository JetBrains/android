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
package com.android.tools.idea.editors.layoutInspector.actions

import com.android.tools.idea.editors.layoutInspector.ui.ViewNodeActiveDisplay
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.ui.components.JBLabel
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener

/**
 * Custom action that renders a slider for choosing alpha between 0 and 1 for the overlay image.
 * Only visible if there is an overlay selected.
 */
class SetOverlayAlphaAction(private val myPreview: ViewNodeActiveDisplay) :
    AnAction("Overlay Alpha", "Set Overlay Alpha", null), CustomComponentAction, ChangeListener {

  override fun update(e: AnActionEvent?) {
    super.update(e)
    if (e == null) return
    e.presentation.isEnabledAndVisible = myPreview.hasOverlay()
  }

  override fun createCustomComponent(presentation: Presentation): JComponent {
    val alphaSlider = JSlider(JSlider.HORIZONTAL, 0, 100, (myPreview.overlayAlpha * 100).toInt())
    alphaSlider.addChangeListener(this)

    val alphaLabel = JBLabel("Alpha:").apply {
      border = EmptyBorder(0, 5, 0, 0)
      labelFor = alphaSlider
    }

    return JPanel().apply {
      layout = BoxLayout(this, BoxLayout.LINE_AXIS)
      add(JSeparator(SwingConstants.VERTICAL))
      add(alphaLabel)
      add(alphaLabel)
    }
  }

  override fun actionPerformed(e: AnActionEvent?) {
    // noop, action is handled in alphaSlider's change listener
  }

  override fun stateChanged(e: ChangeEvent?) {
    if (e == null) return
    val source = e.source as JSlider
    myPreview.overlayAlpha = source.value.toFloat() / 100
  }
}

