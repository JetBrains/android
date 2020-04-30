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
package com.android.tools.idea.uibuilder.structure

import com.android.SdkConstants
import com.android.tools.adtui.common.secondaryPanelBackground
import com.android.tools.idea.res.RESOURCE_ICON_SIZE
import com.android.tools.idea.uibuilder.structure.NlVisibilityModel.Visibility
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.ui.colorpicker.LightCalloutPopup
import com.intellij.ui.components.JBLabel
import org.jetbrains.kotlin.idea.debugger.readAction
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Point
import java.util.function.BiFunction
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.JPopupMenu

/**
 * Menu that displays all available visibility options to choose from.
 * [SdkConstants.ANDROID_URI] and [SdkConstants.TOOLS_URI].
 */
class NlVisibilityPopupMenu {

  /** Invoker needs to be set for determining position of the menu. */
  var invoker: UpdatableActionButton? = null

  /** Shows menu that displays all available visibility options to choose from. */
  fun showMenu(model: NlVisibilityModel, e: AnActionEvent) {
    val application = ApplicationManager.getApplication()
    if (!application.isReadAccessAllowed) {
      application.readAction {
        showMenu(model, e)
      }
      return
    }

    val content = VisibilityPopupContent(model)
    val popupMenu = LightCalloutPopup(
        content,
        closedCallback = {
          // Ideally this should update the button. But doesn't seem to always work.
          invoker?.toggleAction?.let {
            it.isSelected = false
          }
          invoker?.presentation?.let {
            Toggleable.setSelected(it, false)
          }
        })
    val x: Int = if (invoker != null) invoker!!.width else 0
    val y: Int = if (invoker != null) invoker!!.height / 2 else 0
    popupMenu.show(invoker, Point(x, y), Balloon.Position.atRight)
  }
}

/**
 * UI contents inside the popup menu.
 */
class VisibilityPopupContent(
  model: NlVisibilityModel) : JPanel() {
  companion object {
    private const val MENU_OUTER_PADDING = 10
    private const val MENU_INNER_PADDING = 5
    private const val ICON_PADDING_X = 6
    private const val ICON_PADDING_Y = 6
  }

  init {
    layout = GridBagLayout()
    background = secondaryPanelBackground
    border = BorderFactory.createEmptyBorder(MENU_OUTER_PADDING, MENU_OUTER_PADDING, MENU_OUTER_PADDING, MENU_OUTER_PADDING)

    val c = GridBagConstraints()
    c.fill = GridBagConstraints.NONE
    c.anchor = GridBagConstraints.WEST
    c.gridx = 0
    c.gridy = 0
    c.ipady = MENU_INNER_PADDING

    c.gridwidth = 4
    val androidText = JBLabel("Android:visibility")
    add(androidText, c)

    c.gridwidth = 1
    c.gridy++
    c.ipady = ICON_PADDING_Y
    c.ipadx = ICON_PADDING_X
    addButtons(this, c, SdkConstants.ANDROID_URI, model)

    // Having trouble getting this separator to show.
    c.gridy++
    c.ipadx = 0
    c.gridx = 0
    c.gridwidth = 4
    val separator = JPopupMenu.Separator()
    separator.preferredSize = Dimension(1, 1)
    add(separator, c)

    c.fill = GridBagConstraints.NONE
    c.gridy++
    c.gridwidth = 4
    val toolsText = JBLabel("Tools:visibility")
    add(toolsText, c)

    c.gridwidth = 1
    c.gridy++
    c.ipady = ICON_PADDING_Y
    c.ipadx = ICON_PADDING_X
    addButtons(this, c, SdkConstants.TOOLS_URI, model)
  }

  private fun addButtons(panel: JPanel,
                         c: GridBagConstraints,
                         uri: String,
                         model: NlVisibilityModel) {
    val buttons = VisibilityPopupButtons(uri, model)
    buttons.buttons.forEach {
      panel.add(it, c)
      c.gridx++
    }
  }
}

/**
 * Class that controls 4 buttons each representing the visibility settings for
 * the component.
 */
@VisibleForTesting
class VisibilityPopupButtons(
  private val uri: String,
  private val model: NlVisibilityModel) {

  /** Actions that updates component visibility. */
  private val actions = ArrayList<VisibilityToggleAction>()

  /** List of buttons, each represent one of the visibility settings for the component. */
  val buttons = ArrayList<ActionButton>()

  init {
    buttons.add(createActionButton(Visibility.NONE))
    buttons.add(createActionButton(Visibility.VISIBLE))
    buttons.add(createActionButton(Visibility.INVISIBLE))
    buttons.add(createActionButton(Visibility.GONE))

    buttons.forEach {
      actions.add(it.action as VisibilityToggleAction)
    }
  }

  private fun createActionButton(visibility: Visibility): ActionButton {
    val action = VisibilityToggleAction(
      model,
      uri,
      visibility,
      BiFunction { model, button ->
        updateOtherButtons(button)
      })

    val presentation = action.templatePresentation
    updatePresentation(visibility, SdkConstants.TOOLS_URI == uri, presentation)

    val dim = Dimension(RESOURCE_ICON_SIZE, RESOURCE_ICON_SIZE)
    val button = ActionButton(action, presentation, "Visibility change", dim)
    button.isEnabled = true
    return button
  }

  /** Make sure that all other buttons are set false. */
  private fun updateOtherButtons(currentAction: VisibilityToggleAction) {
    actions.forEach {
      if (it != currentAction) {
        it.isSelected = false
      }
    }
  }
}

/**
 * Create an action, that can perform:
 * - Clicking update component visibility
 * - Able to update view without updating component.
 *
 * @param model - model that represents the current view
 * @param visibility - visibility setting that this action represent.
 * @param uri - either tools or android.
 */
@VisibleForTesting
class VisibilityToggleAction(
  val model: NlVisibilityModel,
  val uri: String,
  val visibility: Visibility,
  private val callback: BiFunction<NlVisibilityModel, VisibilityToggleAction, Unit>) : ControllableToggleAction() {

  init {
    isSelected = model.contains(visibility, uri)
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    if (!state) {
      /*
       * isSelected not updated intentionally. Visibility must be one of 4:
       * none, visible, invisible, gone. Only way to toggle this action must
       * be by clicking other 3 button.
       */
      return
    }
    isSelected = state
    model.writeToComponent(visibility, uri)
    callback.apply(model, this)
  }
}

/**
 * Action that can toggle the button on/off.
 * Updating [isSelected] will update the presentation.
 */
open class ControllableToggleAction(): ToggleAction() {

  /**  Update the button state. */
  var isSelected: Boolean = false
  set(value) {
    field = value
    Toggleable.setSelected(templatePresentation, value)
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    return isSelected
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    isSelected = state
  }
}
