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
package com.android.tools.idea.layoutinspector.common

import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import javax.swing.JComponent

fun showViewContextMenu(view: ViewNode?, inspectorModel: InspectorModel, source: JComponent, x: Int, y: Int) {
  if (!inspectorModel.isEmpty) {
    val root = inspectorModel.root
    val actionManager = ActionManager.getInstance()
    val group = object : ActionGroup("", true) {
      override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val showAllAction = object : AnAction("Show All") {
          override fun actionPerformed(e: AnActionEvent) {
            root.flatten().forEach { it.visible = true }
            inspectorModel.notifyModified()
          }

          override fun update(e: AnActionEvent) {
            super.update(e)
            e.presentation.isEnabled = root.flatten().any { !it.visible }
          }
        }

        return if (view == null) arrayOf(showAllAction) else
          arrayOf(object : AnAction("Hide Subtree") {
            override fun actionPerformed(e: AnActionEvent) {
              view.flatten().forEach { it.visible = false }
              inspectorModel.notifyModified()
            }
          }, object : AnAction("Show Only Subtree") {
            override fun actionPerformed(e: AnActionEvent) {
              root.flatten().forEach { it.visible = false }
              view.flatten().forEach { it.visible = true }
              inspectorModel.notifyModified()
            }
          }, object : AnAction("Show Only Parents") {
            override fun actionPerformed(e: AnActionEvent) {
              root.flatten().forEach { it.visible = false }
              generateSequence(view) { it.parent }.forEach { it.visible = true }
              inspectorModel.notifyModified()
            }
          }, showAllAction)
      }
    }
    val popupMenu = actionManager.createActionPopupMenu("LayoutInspector", group)
    popupMenu.component.show(source, x, y)
  }
}
