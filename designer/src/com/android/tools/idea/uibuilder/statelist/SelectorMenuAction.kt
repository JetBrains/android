/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.statelist

import android.widget.ImageView
import com.android.tools.adtui.common.secondaryPanelBackground
import com.android.tools.idea.actions.ANIMATION_TOOLBAR
import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.uibuilder.editor.AnimatedSelectorToolbar
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.google.common.primitives.Ints
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBRadioButton
import com.intellij.util.ArrayUtil
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Point
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

private const val PICKER_WIDTH_PX = 300
private const val STATE_ITEM_HEIGHT_PX = 35

class SelectorMenuAction: AnAction("State Selector", null, StudioIcons.LayoutEditor.Menu.SWITCH) {

  override fun displayTextInToolbar(): Boolean = true

  override fun update(e: AnActionEvent) {
    e.presentation.description = null

    val surface = e.getData(DESIGN_SURFACE)
    if (surface == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    when (val toolbar = e.getData(ANIMATION_TOOLBAR)) {
      null -> e.presentation.isEnabledAndVisible = true // This happens when previewing <selector> file
      is AnimatedSelectorToolbar -> {
        e.presentation.isVisible = true
        e.presentation.isEnabled = !toolbar.isTransitionSelected()
        e.presentation.description = if (toolbar.isTransitionSelected()) "Cannot select the state when previewing a transition" else null
      }
      else -> e.presentation.isEnabledAndVisible = false
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val surface = e.getRequiredData(DESIGN_SURFACE)
    val button = e.inputEvent.component

    // Setup callback to reset the animated selector toolbar when state is changed.
    val toolbar = DataManager.getDataProvider(surface)?.let { ANIMATION_TOOLBAR.getData(it) } as? AnimatedSelectorToolbar
    val callback: () -> Unit = { toolbar?.setNoTransition() }

    val menu = StateListMenu(surface, callback)
    val popup = JBPopupFactory.getInstance().createComponentPopupBuilder(menu, null)
      .setBorderColor(JBColor.border())
      .setShowBorder(false)
      .setCancelOnClickOutside(true)
      .setCancelOnWindowDeactivation(true)
      .createPopup()
    popup.content.background = secondaryPanelBackground
    popup.show(RelativePoint(button, Point(0, button.height)))
  }
}

class StateListMenu(designSurface: DesignSurface<*>, callback: () -> Unit): JComponent() {
  init {
    val height = State.values().size * STATE_ITEM_HEIGHT_PX

    layout = BoxLayout(this, BoxLayout.Y_AXIS).apply {
      background = secondaryPanelBackground
    }
    border = JBUI.Borders.empty(5, 14, 5, 14)
    preferredSize = JBUI.size(PICKER_WIDTH_PX, height)
    background = secondaryPanelBackground

    for (state in State.values()) {
      add(createStateItem(designSurface, state, callback))
    }
  }
}

private fun createStateItem(designSurface: DesignSurface<*>, state: State, callback: () -> Unit): JPanel {
  val stateItemPanel = JPanel(GridBagLayout()).apply {
    preferredSize = JBUI.size(PICKER_WIDTH_PX, STATE_ITEM_HEIGHT_PX)
    border = JBUI.Borders.empty(0, 2, 2, 2)
    background = secondaryPanelBackground
  }

  val trueButton = JBRadioButton("True").apply {
    background = secondaryPanelBackground
    border = JBUI.Borders.empty(0, 10, 0, 10)
  }
  val falseButton = JBRadioButton("False").apply {
    background = secondaryPanelBackground
    border = JBUI.Borders.empty(0, 10, 0, 10)
  }
  val buttonGroup = ButtonGroup()
  buttonGroup.add(trueButton)
  buttonGroup.add(falseButton)
  trueButton.addActionListener {
    setState(designSurface, state, true)
    callback()
  }
  falseButton.addActionListener {
    setState(designSurface, state, false)
    callback()
  }

  if (isSelected(designSurface, state)) {
    trueButton.isSelected = true
    falseButton.isSelected = false
  }
  else {
    trueButton.isSelected = false
    falseButton.isSelected = true
  }

  val label = JLabel(state.text)

  val constraints = GridBagConstraints().apply {
    fill = GridBagConstraints.HORIZONTAL
    gridx = 0
    gridy = 0
    weightx = 1.0 // Make the state text fill all extra space.
  }
  stateItemPanel.add(label, constraints)
  constraints.run {
    gridx = 1
    weightx = 0.0 // No extra space for true and false button.
  }
  stateItemPanel.add(trueButton, constraints)
  constraints.run {
    gridx = 2
  }
  stateItemPanel.add(falseButton, constraints)
  return stateItemPanel
}

private fun isSelected(surface: DesignSurface<*>, state: State): Boolean {
  val imageView = getImageView(surface) ?: return false
  return Ints.contains(imageView.drawableState, state.intValue)
}

private fun getImageView(surface: DesignSurface<*>): ImageView? {
  val layoutlibSceneManager = surface.sceneManager as? LayoutlibSceneManager ?: return null
  return layoutlibSceneManager.renderResult?.rootViews?.firstOrNull()?.viewObject as? ImageView
}

private fun setState(surface: DesignSurface<*>, state: State, enabled: Boolean) {
  val image = getImageView(surface) ?: return
  val states = image.drawableState
  val stateValue = state.intValue

  val sceneManager = surface.sceneManagers.first() as LayoutlibSceneManager

  // image.setImageState(states, true) didn't work as expected. So I'm doing it this way.
  if (enabled) {
    if (!Ints.contains(states, stateValue)) {
      sceneManager.executeInRenderSessionAsync { image.setImageState(ArrayUtil.append(states, stateValue), false) }
        .whenComplete { _, _ -> sceneManager.requestRenderAsync() }
    }
  }
  else if (Ints.contains(states, stateValue)) {
    val i = Ints.indexOf(states, stateValue)
    sceneManager.executeInRenderSessionAsync { image.setImageState(ArrayUtil.remove(states, i), false) }
      .whenComplete { _, _ -> sceneManager.requestRenderAsync() }
  }
}
