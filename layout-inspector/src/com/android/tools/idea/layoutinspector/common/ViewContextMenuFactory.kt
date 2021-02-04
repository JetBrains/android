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

import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.SelectionOrigin
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.tree.GotoDeclarationAction
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.ActionMenuItem
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.PopupMenuListenerAdapter
import com.intellij.util.containers.toArray
import com.intellij.util.text.nullize
import icons.StudioIcons
import org.jetbrains.android.dom.AndroidDomElementDescriptorProvider
import javax.swing.JComponent
import javax.swing.JPopupMenu
import javax.swing.event.PopupMenuEvent

fun showViewContextMenu(views: List<ViewNode>, inspectorModel: InspectorModel, source: JComponent, x: Int, y: Int) {
  if (inspectorModel.isEmpty) {
    return
  }
  val root = inspectorModel.root
  val actionManager = ActionManager.getInstance()
  val group = object : ActionGroup("", true) {
    override fun getChildren(unused: AnActionEvent?): Array<AnAction> {
      val showAllAction = object : AnAction("Show All") {
        override fun actionPerformed(unused: AnActionEvent) {
          root.flatten().forEach { it.visible = true }
          inspectorModel.notifyModified()
        }

        override fun update(actionEvent: AnActionEvent) {
          actionEvent.presentation.isEnabled = root.flatten().any { !it.visible }
        }
      }

      val result = mutableListOf<AnAction>()
      if (views.size > 1) {
        val viewMenu = DropDownAction("Select View", null, null)
        viewMenu.addAll(views.map { SelectViewAction(it, inspectorModel) })
        result.add(viewMenu)
      }
      if (inspectorModel.hasSubImages) {
        if (views.isNotEmpty()) {
          val topView = views.first()
          result.add(object : AnAction("Hide Subtree") {
            override fun actionPerformed(unused: AnActionEvent) {
              topView.flatten().forEach { it.visible = false }
              inspectorModel.notifyModified()
            }
          })
          result.add(object : AnAction("Show Only Subtree") {
            override fun actionPerformed(unused: AnActionEvent) {
              root.flatten().forEach { it.visible = false }
              topView.flatten().forEach { it.visible = true }
              inspectorModel.notifyModified()
            }
          })
          result.add(object : AnAction("Show Only Parents") {
            override fun actionPerformed(unused: AnActionEvent) {
              root.flatten().forEach { it.visible = false }
              generateSequence(topView) { it.parent }.forEach { it.visible = true }
              inspectorModel.notifyModified()
            }
          })
        }
        result.add(showAllAction)
        result.add(GotoDeclarationAction)
      }
      return result.toArray(arrayOf())
    }
  }
  val popupMenu = actionManager.createActionPopupMenu("LayoutInspector", group)
  val popupComponent = popupMenu.component

  if (views.size > 1) {
    // Add listeners to highlight the hovered item. Unfortunately the necessary components to which to add listeners aren't available right
    // away, so we have to have this chain of listeners and invokeLaters.
    popupComponent.addPopupMenuListener(object : PopupMenuListenerAdapter() {
      override fun popupMenuWillBecomeVisible(unuxed: PopupMenuEvent?) {
        ApplicationManager.getApplication().invokeLater {
          val subMenu = popupComponent.subElements[0].subElements[0] as? JPopupMenu ?: return@invokeLater
          subMenu.addPopupMenuListener(object : PopupMenuListenerAdapter() {
            override fun popupMenuWillBecomeVisible(unused: PopupMenuEvent) {
              subMenu.subElements
                .filterIsInstance<ActionMenuItem>()
                .forEach { menuItem ->
                  menuItem.addChangeListener {
                    if (menuItem.isArmed) {
                      inspectorModel.hoveredNode = (menuItem.anAction as SelectViewAction).view
                    }
                  }
                }
            }
          })
        }
      }
    })
  }
  popupComponent.show(source, x, y)
}

private fun generateText(viewNode: ViewNode) =
  viewNode.viewId?.name.nullize() ?: viewNode.textValue.nullize() ?: viewNode.qualifiedName

private class SelectViewAction(
  val view: ViewNode, val inspectorModel: InspectorModel
) : AnAction(generateText(view), null,
             AndroidDomElementDescriptorProvider.getIconForViewTag(view.unqualifiedName) ?: StudioIcons.LayoutEditor.Palette.UNKNOWN_VIEW) {

  override fun actionPerformed(unused: AnActionEvent) {
    inspectorModel.setSelection(view, SelectionOrigin.INTERNAL)

    // This action is only performed from mouse clicks on the image
    inspectorModel.stats.selectionMadeFromImage(view)
  }
}
