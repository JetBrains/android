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

import com.android.tools.idea.layoutinspector.LayoutInspectorBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAwareAction
import java.awt.Component
import java.awt.event.MouseEvent

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
  RIGHT_VERTICAL_SWAP
}

/** Split the UI vertically. */
class VerticalSplitAction(private val updateUi: (UiConfig) -> Unit) :
  AnAction(LayoutInspectorBundle.message("split.vertical"), "", AllIcons.Actions.SplitVertically) {
  override fun actionPerformed(e: AnActionEvent) {
    updateUi(UiConfig.VERTICAL)
  }
}

/** Split the UI vertically and swap tree and attributes panel. */
class SwapVerticalSplitAction(private val updateUi: (UiConfig) -> Unit) :
  AnAction(
    @Suppress("DialogTitleCapitalization") LayoutInspectorBundle.message("split.vertical.swap"),
    "",
    AllIcons.Actions.SplitVertically
  ) {
  override fun actionPerformed(e: AnActionEvent) {
    updateUi(UiConfig.VERTICAL_SWAP)
  }
}

/** Split the UI horizontally. */
class HorizontalSplitAction(private val updateUi: (UiConfig) -> Unit) :
  AnAction(
    LayoutInspectorBundle.message("split.horizontal"),
    "",
    AllIcons.Actions.SplitHorizontally
  ) {
  override fun actionPerformed(e: AnActionEvent) {
    updateUi(UiConfig.HORIZONTAL)
  }
}

/** Split the UI horizontally and swap tree and attributes panel. */
class SwapHorizontalSplitAction(private val updateUi: (UiConfig) -> Unit) :
  AnAction(
    @Suppress("DialogTitleCapitalization") LayoutInspectorBundle.message("split.horizontal.swap"),
    "",
    AllIcons.Actions.SplitHorizontally
  ) {
  override fun actionPerformed(e: AnActionEvent) {
    updateUi(UiConfig.HORIZONTAL_SWAP)
  }
}

/**
 * Split the UI to have both panels vertically stacked on the left of the device, tree panel at the
 * top
 */
class LeftVerticalSplitAction(private val updateUi: (UiConfig) -> Unit) :
  AnAction(LayoutInspectorBundle.message("left.vertical"), "", AllIcons.Actions.MoveToLeftTop) {
  override fun actionPerformed(e: AnActionEvent) {
    updateUi(UiConfig.LEFT_VERTICAL)
  }
}

/**
 * Split the UI to have both panels vertically stacked on the left of the device, tree panel at the
 * bottom.
 */
class SwapLeftVerticalSplitAction(private val updateUi: (UiConfig) -> Unit) :
  AnAction(
    @Suppress("DialogTitleCapitalization") LayoutInspectorBundle.message("left.vertical.swap"),
    "",
    AllIcons.Actions.MoveToLeftBottom
  ) {
  override fun actionPerformed(e: AnActionEvent) {
    updateUi(UiConfig.LEFT_VERTICAL_SWAP)
  }
}

/**
 * Split the UI to have both panels vertically stacked on the right of the device, tree panel at the
 * top
 */
class RightVerticalSplitAction(private val updateUi: (UiConfig) -> Unit) :
  AnAction(LayoutInspectorBundle.message("right.vertical"), "", AllIcons.Actions.MoveToRightTop) {
  override fun actionPerformed(e: AnActionEvent) {
    updateUi(UiConfig.RIGHT_VERTICAL)
  }
}

/**
 * Split the UI to have both panels vertically stacked on the right of the device, tree panel at the
 * bottom.
 */
class SwapRightVerticalSplitAction(private val updateUi: (UiConfig) -> Unit) :
  AnAction(
    @Suppress("DialogTitleCapitalization") LayoutInspectorBundle.message("right.vertical.swap"),
    "",
    AllIcons.Actions.MoveToRightBottom
  ) {
  override fun actionPerformed(e: AnActionEvent) {
    updateUi(UiConfig.RIGHT_VERTICAL_SWAP)
  }
}
