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
package com.android.tools.idea.layoutinspector.runningdevices.actions

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.layoutinspector.LayoutInspectorBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAwareAction
import icons.StudioIcons
import java.awt.Component
import java.awt.event.MouseEvent
import javax.swing.Icon

/** A gear action that when clicked shows a popup containing [actions]. */
class GearAction(vararg val actions: AnAction) :
  DumbAwareAction("More Options", null, AllIcons.General.GearPlain) {
  override fun actionPerformed(event: AnActionEvent) {
    var x = 0
    var y = 0
    val inputEvent = event.inputEvent
    if (inputEvent is MouseEvent) {
      x = inputEvent.x
      y = inputEvent.y
    }
    showGearPopup(inputEvent!!.component, x, y, actions.toList())
  }
}

private fun showGearPopup(component: Component, x: Int, y: Int, actions: List<AnAction>) {
  val group = DefaultActionGroup()
  actions.forEach { group.add(it) }
  val popupMenu =
    ActionManager.getInstance().createActionPopupMenu(ActionPlaces.TOOLWINDOW_POPUP, group)
  popupMenu.component.show(component, x, y)
}

enum class UiConfig {
  HORIZONTAL,
  HORIZONTAL_SWAP,
  VERTICAL,
  VERTICAL_SWAP,
  LEFT_VERTICAL,
  LEFT_VERTICAL_SWAP,
  RIGHT_VERTICAL,
  RIGHT_VERTICAL_SWAP,
}

abstract class UiConfigAction(
  title: String,
  private val icon: Icon,
  private val uiConfig: UiConfig,
  @UiThread private val currentConfig: () -> UiConfig,
  @UiThread private val updateUi: (UiConfig) -> Unit,
) : AnAction(title, "", icon) {
  override fun update(e: AnActionEvent) {
    super.update(e)
    if (currentConfig() == uiConfig) {
      e.presentation.icon = AllIcons.Actions.Checked
    } else {
      e.presentation.icon = icon
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    updateUi(uiConfig)
  }

  override fun getActionUpdateThread() = ActionUpdateThread.EDT
}

/** Split the UI vertically. */
class VerticalSplitAction(currentConfig: () -> UiConfig, updateUi: (UiConfig) -> Unit) :
  UiConfigAction(
    LayoutInspectorBundle.message("split.vertical"),
    StudioIcons.LayoutInspector.Panel.VERTICAL_SPLIT,
    UiConfig.VERTICAL,
    currentConfig,
    updateUi,
  )

/** Split the UI vertically and swap tree and attributes panel. */
class SwapVerticalSplitAction(currentConfig: () -> UiConfig, updateUi: (UiConfig) -> Unit) :
  UiConfigAction(
    @Suppress("DialogTitleCapitalization") LayoutInspectorBundle.message("split.vertical.swap"),
    StudioIcons.LayoutInspector.Panel.VERTICAL_SPLIT_SWAP,
    UiConfig.VERTICAL_SWAP,
    currentConfig,
    updateUi,
  )

/** Split the UI horizontally. */
class HorizontalSplitAction(currentConfig: () -> UiConfig, updateUi: (UiConfig) -> Unit) :
  UiConfigAction(
    LayoutInspectorBundle.message("split.horizontal"),
    StudioIcons.LayoutInspector.Panel.BOTTOM,
    UiConfig.HORIZONTAL,
    currentConfig,
    updateUi,
  )

/** Split the UI horizontally and swap tree and attributes panel. */
class SwapHorizontalSplitAction(currentConfig: () -> UiConfig, updateUi: (UiConfig) -> Unit) :
  UiConfigAction(
    @Suppress("DialogTitleCapitalization") LayoutInspectorBundle.message("split.horizontal.swap"),
    StudioIcons.LayoutInspector.Panel.BOTTOM_SWAP,
    UiConfig.HORIZONTAL_SWAP,
    currentConfig,
    updateUi,
  )

/**
 * Split the UI to have both panels vertically stacked on the left of the device, tree panel at the
 * top
 */
class LeftVerticalSplitAction(currentConfig: () -> UiConfig, updateUi: (UiConfig) -> Unit) :
  UiConfigAction(
    LayoutInspectorBundle.message("left.vertical"),
    StudioIcons.LayoutInspector.Panel.LEFT_VERTICAL,
    UiConfig.LEFT_VERTICAL,
    currentConfig,
    updateUi,
  )

/**
 * Split the UI to have both panels vertically stacked on the left of the device, tree panel at the
 * bottom.
 */
class SwapLeftVerticalSplitAction(currentConfig: () -> UiConfig, updateUi: (UiConfig) -> Unit) :
  UiConfigAction(
    @Suppress("DialogTitleCapitalization") LayoutInspectorBundle.message("left.vertical.swap"),
    StudioIcons.LayoutInspector.Panel.LEFT_VERTICAL_SWAP,
    UiConfig.LEFT_VERTICAL_SWAP,
    currentConfig,
    updateUi,
  )

/**
 * Split the UI to have both panels vertically stacked on the right of the device, tree panel at the
 * top
 */
class RightVerticalSplitAction(currentConfig: () -> UiConfig, updateUi: (UiConfig) -> Unit) :
  UiConfigAction(
    LayoutInspectorBundle.message("right.vertical"),
    StudioIcons.LayoutInspector.Panel.RIGHT_VERTICAL,
    UiConfig.RIGHT_VERTICAL,
    currentConfig,
    updateUi,
  )

/**
 * Split the UI to have both panels vertically stacked on the right of the device, tree panel at the
 * bottom.
 */
class SwapRightVerticalSplitAction(currentConfig: () -> UiConfig, updateUi: (UiConfig) -> Unit) :
  UiConfigAction(
    @Suppress("DialogTitleCapitalization") LayoutInspectorBundle.message("right.vertical.swap"),
    StudioIcons.LayoutInspector.Panel.RIGHT_VERTICAL_SWAP,
    UiConfig.RIGHT_VERTICAL_SWAP,
    currentConfig,
    updateUi,
  )
