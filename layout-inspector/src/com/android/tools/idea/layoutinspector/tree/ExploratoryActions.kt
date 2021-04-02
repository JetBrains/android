/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.tree

import com.android.tools.componenttree.ui.COMPACT_LINES
import com.android.tools.componenttree.ui.LINES
import com.android.tools.idea.layoutinspector.LAYOUT_INSPECTOR_DATA_KEY
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.ui.tree.ui.Control
import com.intellij.ui.treeStructure.Tree

/**
 * This file contains exploratory actions meant for a user study.
 */

object CallstackAction : ToggleAction("Show compose as Callstack", null, null) {

  override fun isSelected(event: AnActionEvent): Boolean =
    TreeSettings.composeAsCallstack

  override fun setSelected(event: AnActionEvent, state: Boolean) {
    TreeSettings.composeAsCallstack = state

    // Update the component tree:
    event.treePanel()?.refresh()
  }
}

object DrawablesInCallstackAction : ToggleAction("Show compose Drawables in Callstack", null, null) {

  override fun isSelected(event: AnActionEvent): Boolean =
    TreeSettings.composeDrawablesInCallstack

  override fun setSelected(event: AnActionEvent, state: Boolean) {
    TreeSettings.composeDrawablesInCallstack = state

    // Update the component tree:
    event.treePanel()?.refresh()
  }

  override fun update(event: AnActionEvent) {
    super.update(event)
    event.presentation.isEnabled = TreeSettings.composeAsCallstack
  }
}

object CompactTree : ToggleAction("Compact tree", null, null) {

  override fun isSelected(event: AnActionEvent): Boolean =
    event.tree()?.getClientProperty(Control.Painter.KEY) in listOf(Control.Painter.COMPACT, COMPACT_LINES)

  override fun setSelected(event: AnActionEvent, state: Boolean) {
    TreeSettings.compactTree = state
    val isLine = event.tree()?.getClientProperty(Control.Painter.KEY) in listOf(LINES, COMPACT_LINES)
    val newPainter = when {
      isLine && state -> COMPACT_LINES
      isLine && !state -> LINES
      state -> Control.Painter.COMPACT
      else -> null
    }
    val tree = event.tree() ?: return
    tree.putClientProperty(Control.Painter.KEY, newPainter)
    tree.repaint()
  }
}

object SupportLines : ToggleAction("Show support lines", null, null) {

  override fun isSelected(event: AnActionEvent): Boolean =
    event.tree()?.getClientProperty(Control.Painter.KEY) in listOf(LINES, COMPACT_LINES)

  override fun setSelected(event: AnActionEvent, state: Boolean) {
    TreeSettings.supportLines = state
    val isCompact = event.tree()?.getClientProperty(Control.Painter.KEY) in listOf(Control.Painter.COMPACT, COMPACT_LINES)
    val newPainter = when {
      isCompact && state -> COMPACT_LINES
      isCompact && !state -> Control.Painter.COMPACT
      state -> LINES
      else -> null
    }
    val tree = event.tree() ?: return
    tree.putClientProperty(Control.Painter.KEY, newPainter)
    tree.repaint()
  }
}

private fun inspector(event: AnActionEvent): LayoutInspector? =
  event.getData(LAYOUT_INSPECTOR_DATA_KEY)

fun Tree.setDefaultPainter() {
  val painter = with(TreeSettings) {
    when {
      compactTree && supportLines -> COMPACT_LINES
      compactTree && !supportLines -> Control.Painter.COMPACT
      !compactTree && supportLines -> LINES
      else -> null
    }
  }
  putClientProperty(Control.Painter.KEY, painter)
}
