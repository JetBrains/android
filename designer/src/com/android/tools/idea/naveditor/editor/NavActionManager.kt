/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.naveditor.editor

import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.idea.common.actions.ActivateComponentAction
import com.android.tools.idea.common.actions.GotoComponentAction
import com.android.tools.idea.common.actions.ZoomInAction
import com.android.tools.idea.common.actions.ZoomOutAction
import com.android.tools.idea.common.actions.ZoomToFitAction
import com.android.tools.idea.common.editor.ActionManager
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.surface.ZoomShortcut
import com.android.tools.idea.naveditor.actions.AddGlobalAction
import com.android.tools.idea.naveditor.actions.AddToExistingGraphAction
import com.android.tools.idea.naveditor.actions.AddToNewGraphAction
import com.android.tools.idea.naveditor.actions.AutoArrangeAction
import com.android.tools.idea.naveditor.actions.EditExistingAction
import com.android.tools.idea.naveditor.actions.ReturnToSourceAction
import com.android.tools.idea.naveditor.actions.SelectAllAction
import com.android.tools.idea.naveditor.actions.SelectNextAction
import com.android.tools.idea.naveditor.actions.SelectPreviousAction
import com.android.tools.idea.naveditor.actions.StartDestinationAction
import com.android.tools.idea.naveditor.actions.ToDestinationAction
import com.android.tools.idea.naveditor.actions.ToSelfAction
import com.android.tools.idea.naveditor.model.isAction
import com.android.tools.idea.naveditor.model.isDestination
import com.android.tools.idea.naveditor.model.isNavigation
import com.android.tools.idea.naveditor.model.supportsActions
import com.android.tools.idea.naveditor.model.uiName
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JComponent

/**
 * Provides and handles actions in the navigation editor
 */
// Open for testing only
open class NavActionManager(surface: NavDesignSurface) : ActionManager<NavDesignSurface>(surface) {
  private val gotoComponentAction: AnAction = GotoComponentAction(surface)
  private val autoArrangeAction: AnAction = AutoArrangeAction(surface)
  private val zoomInAction: AnAction = ZoomShortcut.ZOOM_IN.registerForAction(ZoomInAction(surface), surface, surface)
  private val zoomOutAction: AnAction = ZoomShortcut.ZOOM_OUT.registerForAction(ZoomOutAction(surface), surface, surface)
  private val zoomToFitAction: AnAction = ZoomShortcut.ZOOM_FIT.registerForAction(ZoomToFitAction(surface), surface, surface)
  private val selectNextAction: AnAction = SelectNextAction(surface)
  private val selectPreviousAction: AnAction = SelectPreviousAction(surface)
  private val selectAllAction: AnAction = SelectAllAction(surface)
  private val addToNewGraphAction: AnAction = AddToNewGraphAction(surface)

  // Open for testing only
  open val addDestinationMenu by lazy { AddDestinationMenu(mySurface) }

  override fun registerActionsShortcuts(component: JComponent) {
    ActionManager.registerAction(gotoComponentAction, IdeActions.ACTION_GOTO_DECLARATION, component)
    selectNextAction.registerCustomShortcutSet(KeyEvent.VK_TAB, 0, mySurface)
    selectPreviousAction.registerCustomShortcutSet(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK, mySurface)
    ActionManager.registerAction(selectAllAction, IdeActions.ACTION_SELECT_ALL, component)

    addToNewGraphAction.registerCustomShortcutSet(KeyEvent.VK_G, AdtUiUtils.getActionMask(), mySurface)
  }

  override fun createPopupMenu(
    actionManager: com.intellij.openapi.actionSystem.ActionManager,
    leafComponent: NlComponent?
  ): DefaultActionGroup {
    val group = DefaultActionGroup()

    if (leafComponent == null) {
      return group
    }

    when {
      mySurface.selectionModel.selection.count() > 1 -> addMultiSelectionGroup(group, actionManager)
      leafComponent == mySurface.currentNavigation -> addSurfaceGroup(group)
      leafComponent.isDestination -> addDestinationGroup(group, leafComponent, actionManager)
      leafComponent.isAction -> addActionGroup(group, leafComponent, actionManager)
    }

    return group
  }

  private fun addMultiSelectionGroup(group: DefaultActionGroup, actionManager: com.intellij.openapi.actionSystem.ActionManager) {
    group.add(createNestedGraphGroup(mySurface.selectionModel.selection))

    group.addSeparator()
    addCutCopyPasteDeleteGroup(group, actionManager)
  }

  private fun addCutCopyPasteDeleteGroup(group: DefaultActionGroup,
                                         actionManager: com.intellij.openapi.actionSystem.ActionManager) {
    group.add(actionManager.getAction(IdeActions.ACTION_CUT))
    group.add(actionManager.getAction(IdeActions.ACTION_COPY))
    group.add(actionManager.getAction(IdeActions.ACTION_PASTE))
    group.add(actionManager.getAction(IdeActions.ACTION_DELETE))
  }

  private fun addSurfaceGroup(group: DefaultActionGroup) {
    group.add(selectAllAction)

    group.addSeparator()
    group.add(autoArrangeAction)

    group.addSeparator()
    group.add(zoomInAction)
    group.add(zoomOutAction)
    group.add(zoomToFitAction)

    group.addSeparator()
    group.add(gotoComponentAction)
  }

  private fun addDestinationGroup(
    group: DefaultActionGroup,
    component: NlComponent,
    actionManager: com.intellij.openapi.actionSystem.ActionManager
  ) {
    val activateComponentAction = ActivateComponentAction(if (component.isNavigation) "Open" else "Edit", mySurface, component)
    group.add(activateComponentAction)

    group.addSeparator()
    group.add(createAddActionGroup(component))
    group.add(createNestedGraphGroup(listOf(component)))
    group.add(StartDestinationAction(component))

    group.addSeparator()
    addCutCopyPasteDeleteGroup(group, actionManager)

    group.addSeparator()
    group.add(gotoComponentAction)
  }

  private fun addActionGroup(
    group: DefaultActionGroup,
    component: NlComponent,
    actionManager: com.intellij.openapi.actionSystem.ActionManager
  ) {
    val parent = component.parent ?: throw IllegalStateException()
    group.add(EditExistingAction(mySurface, parent, component))

    group.addSeparator()
    addCutCopyPasteDeleteGroup(group, actionManager)
  }

  private fun createAddActionGroup(component: NlComponent): DefaultActionGroup {
    val group = DefaultActionGroup("Add Action", true)

    if (component.supportsActions) {
      group.add(ToDestinationAction(mySurface, component))
      group.add(ToSelfAction(mySurface, component))
      group.add(ReturnToSourceAction(mySurface, component))
      group.add(AddGlobalAction(mySurface, component))
    }

    return group
  }

  private fun createNestedGraphGroup(components: List<NlComponent>): DefaultActionGroup {
    // TODO: Add shortcut
    val group = DefaultActionGroup("Move to Nested Graph", true)
    val currentNavigation = mySurface.currentNavigation
    group.add(addToNewGraphAction)

    val subnavs = currentNavigation.children.filter { it.isNavigation && !components.contains(it) }
    if (!subnavs.isEmpty()) {
      group.addSeparator()
      for (graph in subnavs) {
        group.add(AddToExistingGraphAction(mySurface, graph.uiName, graph))
      }
    }

    return group
  }

  override fun addActions(
    group: DefaultActionGroup,
    component: NlComponent?,
    newSelection: List<NlComponent>,
    toolbar: Boolean
  ) {
    // This is called whenever the selection changes, but since our contents are static they can be cached.
    group.add(addDestinationMenu)
  }
}