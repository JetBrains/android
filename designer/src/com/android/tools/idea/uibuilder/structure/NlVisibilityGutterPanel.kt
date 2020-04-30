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
package com.android.tools.idea.uibuilder.structure

import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.adtui.common.secondaryPanelBackground
import com.android.tools.idea.common.model.ModelListener
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.res.RESOURCE_ICON_SIZE
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.ui.EmptyIcon
import icons.StudioIcons
import org.jetbrains.kotlin.idea.debugger.readAction
import java.awt.Component
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.util.stream.Stream
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.Icon
import javax.swing.JPanel

/**
 * Panel that shows the view's visibility in the gutter next to the component tree.
 * Clicking each icon would show popup menu that allows users to choose visibility.
 */
class NlVisibilityGutterPanel: JPanel(), ModelListener {

  companion object {
    private const val PADDING_Y = 2
    private const val PADDING_X = 10
  }
  private val LOG = Logger.getInstance(NlVisibilityGutterPanel::class.java)
  private var myModel: NlModel? = null

  init {
    layout = GridBagLayout()
    alignmentX = Component.CENTER_ALIGNMENT
    alignmentY = Component.TOP_ALIGNMENT
    background = secondaryPanelBackground
    border = BorderFactory.createMatteBorder(
        0, 1, 0, 0, AdtUiUtils.DEFAULT_BORDER_COLOR)
  }

  fun setDesignSurface(surface: DesignSurface?) {
    setModel(surface?.model)
  }

  fun setModel(model: NlModel?) {
    if (model === myModel) {
      return
    }
    if (myModel != null) {
      myModel!!.removeListener(this)
    }
    myModel = model
    if (myModel != null) {
      myModel!!.addListener(this)
    }
    updateList(myModel)
  }

  override fun modelDerivedDataChanged(model: NlModel) {
    updateList(model)
  }

  private fun updateList(nlModel: NlModel?) {
    if (nlModel == null) {
      return
    }

    val application = ApplicationManager.getApplication()
    if (!application.isReadAccessAllowed) {
      application.readAction { updateList(nlModel) }
      return
    }
    val buttons = nlModel.flattenComponents().map { component ->
      val model = NlVisibilityModel(component)
      return@map createActionButton(model)
    }
    updateList(buttons)
  }

  /** Update the gutter ui. */
  private fun updateList(buttons: Stream<UpdatableActionButton>) {
    val application = ApplicationManager.getApplication()
    if (!application.isDispatchThread) {
      application.invokeLater { updateList(buttons) }
      return
    }

    removeAll()
    val c = GridBagConstraints()
    c.fill = GridBagConstraints.NONE
    c.anchor = GridBagConstraints.NORTH
    c.gridx = 0
    c.gridy = 0
    c.ipadx = PADDING_X
    c.ipady = PADDING_Y

    buttons.forEach{
      add(it, c)
      c.gridy++
    }

    // Add invisible filter at the bottom
    c.anchor = GridBagConstraints.SOUTH
    c.weighty = 100.0
    add(Box.Filler(Dimension(0, 0), Dimension(0, Int.MAX_VALUE), Dimension(0, Int.MAX_VALUE)), c)
    revalidate()
  }

  /** Creates a clickable visibility button in the gutter. When clicked pops up a menu. */
  private fun createActionButton(model: NlVisibilityModel): UpdatableActionButton {

    val menu = NlVisibilityPopupMenu()
    val action = object: ControllableToggleAction() {
      override fun setSelected(e: AnActionEvent, state: Boolean) {
        isSelected = state
        menu.showMenu(model, e)
      }
    }
    val presentation = Presentation()
    val visibility = model.getCurrentVisibility()
    updatePresentation(visibility.first, visibility.second, presentation)
    if (visibility.first == NlVisibilityModel.Visibility.NONE) {
      // hover-show
      presentation.hoveredIcon = StudioIcons.LayoutEditor.Properties.VISIBLE
      presentation.icon = null
    }

    val dim = Dimension(RESOURCE_ICON_SIZE, RESOURCE_ICON_SIZE)
    val button = UpdatableActionButton(action, presentation, "Update Visibility", dim)
    button.size = dim
    button.isEnabled = true
    menu.invoker = button
    return button
  }
}

/**
 * [ActionButton] that exposes action, and fixes the fallback icon.
 * Default fallback icon for action button is 18x18, causes everything to misalign
 * in the layout editor where all default icons are 16x16.
 */
class UpdatableActionButton(
  val toggleAction: ControllableToggleAction,
  val presentation: Presentation,
  place: String,
  minimumSize: Dimension) : ActionButton(toggleAction, presentation, place, minimumSize) {

  override fun getFallbackIcon(enabled: Boolean): Icon {
    return EmptyIcon.ICON_16
  }
}