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
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.model.ComposeViewNode
import com.android.tools.idea.layoutinspector.model.IconProvider
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.SelectionOrigin
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
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
import org.jetbrains.annotations.VisibleForTesting
import javax.swing.JComponent
import javax.swing.JPopupMenu
import javax.swing.event.PopupMenuEvent

fun showViewContextMenu(views: List<ViewNode>, inspectorModel: InspectorModel, source: JComponent, x: Int, y: Int) {
  if (inspectorModel.isEmpty) {
    return
  }
  val actionManager = ActionManager.getInstance()
  val group = object : ActionGroup("", true) {
    override fun getChildren(event: AnActionEvent?): Array<AnAction> {
      val showAllAction = object : AnAction("Show All") {
        override fun actionPerformed(event: AnActionEvent) {
          inspectorModel.showAll()
        }

        override fun update(actionEvent: AnActionEvent) {
          actionEvent.presentation.isEnabled = inspectorModel.hasHiddenNodes()
        }
      }

      val result = mutableListOf<AnAction>()
      if (views.size > 1) {
        val viewMenu = DropDownAction("Select View", null, null)
        viewMenu.addAll(views.map { SelectViewAction(it, inspectorModel) })
        result.add(viewMenu)
      }

      val client = event?.let { LayoutInspector.get(it)?.currentClient }
      if (client?.capabilities?.contains(InspectorClient.Capability.SUPPORTS_SKP) == true) {
        if (views.isNotEmpty()) {
          val topView = views.first()
          result.add(HideSubtreeAction(inspectorModel, client, topView))
          result.add(ShowOnlySubtreeAction(inspectorModel, client, topView))
          result.add(ShowOnlyParentsAction(inspectorModel, client, topView))
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

private class HideSubtreeAction(
  val inspectorModel: InspectorModel,
  val client: InspectorClient,
  val topView: ViewNode
  ) : AnAction("Hide Subtree") {
  override fun actionPerformed(event: AnActionEvent) {
    client.updateScreenshotType(AndroidWindow.ImageType.SKP, -1f)
    inspectorModel.hideSubtree(topView)
  }
}

private class ShowOnlySubtreeAction(
  val inspectorModel: InspectorModel,
  val client: InspectorClient,
  val topView: ViewNode
  ) : AnAction("Show Only Subtree") {
  override fun actionPerformed(event: AnActionEvent) {
    client.updateScreenshotType(AndroidWindow.ImageType.SKP, -1f)
    inspectorModel.showOnlySubtree(topView)
  }
}

private class ShowOnlyParentsAction(
  val inspectorModel: InspectorModel,
  val client: InspectorClient,
  val topView: ViewNode
  ) : AnAction("Show Only Parents") {
  override fun actionPerformed(event: AnActionEvent) {
    client.updateScreenshotType(AndroidWindow.ImageType.SKP, -1f)
    inspectorModel.showOnlyParents(topView)
  }
}

private fun generateText(viewNode: ViewNode) =
  viewNode.viewId?.name.nullize() ?: viewNode.textValue.nullize() ?: viewNode.qualifiedName

@VisibleForTesting
class SelectViewAction(
  val view: ViewNode, val inspectorModel: InspectorModel
) : AnAction(generateText(view), null, IconProvider.getIconForView(view.qualifiedName, view is ComposeViewNode)) {

  override fun actionPerformed(event: AnActionEvent) {
    inspectorModel.setSelection(view, SelectionOrigin.INTERNAL)

    // This action is only performed from mouse clicks on the image
    LayoutInspector.get(event)?.currentClient?.stats?.selectionMadeFromImage(view)
  }
}
