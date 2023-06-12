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

import com.android.tools.adtui.actions.ZoomInAction
import com.android.tools.adtui.actions.ZoomOutAction
import com.android.tools.adtui.actions.ZoomToFitAction
import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.idea.common.actions.GotoComponentAction
import com.android.tools.idea.common.editor.ActionManager
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.configurations.ConfigurationHolder
import com.android.tools.idea.configurations.OrientationMenuAction
import com.android.tools.idea.naveditor.actions.ActivateComponentAction
import com.android.tools.idea.naveditor.actions.ActivateSelectionAction
import com.android.tools.idea.naveditor.actions.AddActionToolbarAction
import com.android.tools.idea.naveditor.actions.AddGlobalAction
import com.android.tools.idea.naveditor.actions.AddToExistingGraphAction
import com.android.tools.idea.naveditor.actions.AddToNewGraphAction
import com.android.tools.idea.naveditor.actions.AutoArrangeAction
import com.android.tools.idea.naveditor.actions.DeepLinkToolbarAction
import com.android.tools.idea.naveditor.actions.EditExistingAction
import com.android.tools.idea.naveditor.actions.NestedGraphToolbarAction
import com.android.tools.idea.naveditor.actions.ReturnToSourceAction
import com.android.tools.idea.naveditor.actions.ScrollToDestinationAction
import com.android.tools.idea.naveditor.actions.StartDestinationAction
import com.android.tools.idea.naveditor.actions.StartDestinationToolbarAction
import com.android.tools.idea.naveditor.actions.ToDestinationAction
import com.android.tools.idea.naveditor.actions.ToSelfAction
import com.android.tools.idea.naveditor.model.isAction
import com.android.tools.idea.naveditor.model.isDestination
import com.android.tools.idea.naveditor.model.isNavigation
import com.android.tools.idea.naveditor.model.supportsActions
import com.android.tools.idea.naveditor.model.uiName
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.android.tools.idea.uibuilder.actions.SelectAllAction
import com.android.tools.idea.uibuilder.actions.SelectNextAction
import com.android.tools.idea.uibuilder.actions.SelectPreviousAction
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.util.SystemInfo
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.KeyStroke

/**
 * Provides and handles actions in the navigation editor
 */
// Open for testing only
open class NavActionManager(surface: NavDesignSurface) : ActionManager<NavDesignSurface>(surface) {
  private val configurationHolder = ConfigurationHolder { mySurface.configuration }
  private val gotoComponentAction: AnAction = GotoComponentAction(surface)
  private val autoArrangeAction: AnAction = AutoArrangeAction.instance
  private val orientationAction = OrientationMenuAction(configurationHolder, false)
  private val zoomInAction: AnAction = ZoomInAction.getInstance()
  private val zoomOutAction: AnAction = ZoomOutAction.getInstance()
  private val zoomToFitAction: AnAction = ZoomToFitAction.getInstance()
  private val selectNextAction: AnAction = SelectNextAction(surface)
  private val selectPreviousAction: AnAction = SelectPreviousAction(surface)
  private val selectAllAction: AnAction = SelectAllAction(surface)
  private val addToNewGraphAction: AnAction = AddToNewGraphAction(surface)
  private val nestedGraphToolbarAction: AnAction = NestedGraphToolbarAction(surface)
  private val startDestinationToolbarAction: AnAction = StartDestinationToolbarAction.instance
  private val deepLinkToolbarAction: AnAction = DeepLinkToolbarAction.instance
  private val addActionToolbarAction: AnAction = AddActionToolbarAction.instance
  private val activateSelectionAction: AnAction = ActivateSelectionAction(surface)

  // Open for testing only
  open val addDestinationMenu by lazy { AddDestinationMenu(mySurface) }

  override fun registerActionsShortcuts(component: JComponent) {
    registerAction(gotoComponentAction, IdeActions.ACTION_GOTO_DECLARATION, component)
    registerAction(selectAllAction, IdeActions.ACTION_SELECT_ALL, component)
    registerAction(activateSelectionAction, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), component)
    val focusablePane = mySurface.layeredPane
    registerAction(selectPreviousAction, KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), focusablePane)
    registerAction(selectNextAction, KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), focusablePane)
    registerAction(selectPreviousAction, KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), focusablePane)
    registerAction(selectNextAction, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), focusablePane)

    val keyEvent = if (SystemInfo.isMac) KeyEvent.META_DOWN_MASK else KeyEvent.CTRL_DOWN_MASK
    registerAction(addToNewGraphAction, KeyStroke.getKeyStroke(KeyEvent.VK_G, keyEvent), focusablePane)
    addToNewGraphAction.registerCustomShortcutSet(KeyEvent.VK_G, AdtUiUtils.getActionMask(), focusablePane)
  }

  override fun getPopupMenuActions(leafComponent: NlComponent?): DefaultActionGroup {
    val group = DefaultActionGroup()

    if (leafComponent == null) {
      return group
    }

    when {
      mySurface.selectionModel.selection.count() > 1 -> addMultiSelectionGroup(group)
      leafComponent == mySurface.currentNavigation -> addSurfaceGroup(group)
      leafComponent.isDestination -> addDestinationGroup(group, leafComponent)
      leafComponent.isAction -> addActionGroup(group, leafComponent)
    }

    return group
  }

  private fun addMultiSelectionGroup(group: DefaultActionGroup) {
    group.add(createNestedGraphGroup(mySurface.selectionModel.selection))

    group.addSeparator()
    addCutCopyPasteDeleteGroup(group)
  }

  private fun addCutCopyPasteDeleteGroup(group: DefaultActionGroup) {
    group.add(getRegisteredActionByName(IdeActions.ACTION_CUT)!!)
    group.add(getRegisteredActionByName(IdeActions.ACTION_COPY)!!)
    group.add(getRegisteredActionByName(IdeActions.ACTION_PASTE)!!)
    group.add(getRegisteredActionByName(IdeActions.ACTION_DELETE)!!)
  }

  private fun addSurfaceGroup(group: DefaultActionGroup) {
    // Need to select the current orientation before showing the popup:
    orientationAction.updateActionsImmediately()

    group.add(selectAllAction)

    group.addSeparator()
    group.add(orientationAction)
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
    component: NlComponent
  ) {
    val activateComponentAction = ActivateComponentAction(if (component.isNavigation) "Open" else "Edit", mySurface, component)
    group.add(activateComponentAction)

    group.addSeparator()
    group.add(createAddActionGroup(component))
    group.add(createNestedGraphGroup(listOf(component)))
    group.add(StartDestinationAction(component))
    group.add(ScrollToDestinationAction(mySurface, component))

    group.addSeparator()
    addCutCopyPasteDeleteGroup(group)

    group.addSeparator()
    group.add(gotoComponentAction)
  }

  private fun addActionGroup(
    group: DefaultActionGroup,
    component: NlComponent
  ) {
    val parent = component.parent ?: throw IllegalStateException()
    group.add(EditExistingAction(mySurface, parent, component))

    group.addSeparator()
    addCutCopyPasteDeleteGroup(group)
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

  override fun getToolbarActions(newSelection: List<NlComponent>) =
    DefaultActionGroup().apply {
      // This is called whenever the selection changes, but since our contents are static they can be cached.
      add(addDestinationMenu)
      add(nestedGraphToolbarAction)

      addSeparator()
      add(startDestinationToolbarAction)
      add(deepLinkToolbarAction)
      add(addActionToolbarAction)

      addSeparator()
      add(orientationAction)
      add(autoArrangeAction)
    }
}