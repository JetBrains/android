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
package com.android.tools.inspectors.common.api.ide

import com.android.tools.adtui.stdui.ContextMenuItem
import com.android.tools.adtui.stdui.ContextMenuItem.COPY
import com.android.tools.idea.codenavigation.CodeLocation
import com.android.tools.idea.codenavigation.CodeNavigator
import com.android.tools.inspectors.common.api.actions.NavigateToCodeAction
import com.android.tools.inspectors.common.ui.ContextMenuInstaller
import com.intellij.ide.actions.CopyAction
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces.UNKNOWN
import com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.ui.PopupHandler
import java.awt.Component
import java.util.function.IntConsumer
import java.util.function.IntPredicate
import java.util.function.Supplier
import javax.swing.JComponent

private const val COMPONENT_CONTEXT_MENU = "ComponentContextMenu"

class IntellijContextMenuInstaller : ContextMenuInstaller {
  /** Cache of the X mouse coordinate where the [javax.swing.JPopupMenu] is opened. */
  private var cachedX = -1

  override fun installGenericContextMenu(
    component: JComponent,
    contextMenuItem: ContextMenuItem,
    itemEnabled: IntPredicate,
    callback: IntConsumer,
  ) {
    val popupGroup = createOrGetActionGroup(component)
    if (contextMenuItem == ContextMenuItem.SEPARATOR) {
      popupGroup.addSeparator()
      return
    }

    // Reuses the IDE CopyAction, it makes the action component provides the data without exposing
    // the internal implementation.
    if (contextMenuItem == COPY) {
      popupGroup.add(
        object : CopyAction() {

          override fun update(event: AnActionEvent) {
            super.update(event)
            event.presentation.text = contextMenuItem.text
            event.presentation.icon = contextMenuItem.icon
            registerCustomShortcutSet(CommonShortcuts.getCopy(), component)
          }
        }
      )
      return
    }

    val action: AnAction =
      object : AnAction() {
        override fun getActionUpdateThread() = BGT

        override fun update(e: AnActionEvent) {
          val presentation = e.presentation
          presentation.text = contextMenuItem.text
          presentation.icon = contextMenuItem.icon
          presentation.isEnabled = itemEnabled.test(cachedX)
        }

        override fun actionPerformed(e: AnActionEvent) {
          callback.accept(cachedX)
        }
      }

    action.registerCustomShortcutSet(
      { contextMenuItem.keyStrokes.map { KeyboardShortcut(it, null) }.toTypedArray() },
      component
    )
    popupGroup.add(action)
  }

  override fun installNavigationContextMenu(
    component: JComponent,
    navigator: CodeNavigator,
    codeLocationSupplier: Supplier<CodeLocation>,
  ) {
    val popupGroup = createOrGetActionGroup(component)
    popupGroup.add(NavigateToCodeAction(codeLocationSupplier, navigator))
  }

  private fun createOrGetActionGroup(component: JComponent): DefaultActionGroup {
    var actionGroup = component.getClientProperty(COMPONENT_CONTEXT_MENU) as? DefaultActionGroup
    if (actionGroup == null) {
      val newActionGroup = DefaultActionGroup()
      component.putClientProperty(COMPONENT_CONTEXT_MENU, newActionGroup)
      component.addMouseListener(
        object : PopupHandler() {
          override fun invokePopup(comp: Component, x: Int, y: Int) {
            cachedX = x
            @Suppress("UnspecifiedActionsPlace")
            val menu = ActionManager.getInstance().createActionPopupMenu(UNKNOWN, newActionGroup).component
            menu.show(comp, x, y)
          }
        }
      )
      actionGroup = newActionGroup
    }

    return actionGroup
  }
}
