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

import com.android.tools.idea.common.actions.*
import com.android.tools.idea.common.editor.ActionManager
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.surface.ZoomType
import com.android.tools.idea.naveditor.actions.*
import com.android.tools.idea.naveditor.model.getUiName
import com.android.tools.idea.naveditor.model.isDestination
import com.android.tools.idea.naveditor.model.isNavigation
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import javax.swing.JComponent

/**
 * Provides and handles actions in the navigation editor
 */
class NavActionManager(surface: NavDesignSurface) : ActionManager<NavDesignSurface>(surface) {
  private val gotoComponentAction: GotoComponentAction = GotoComponentAction(surface)

  private val createDestinationMenu by lazy { CreateDestinationMenu(mySurface) }

  private val addExistingDestinationMenu by lazy { AddExistingDestinationMenu(mySurface) }

  override fun registerActionsShortcuts(component: JComponent) {
    ActionManager.registerAction(gotoComponentAction, IdeActions.ACTION_GOTO_DECLARATION, component)
  }

  override fun createPopupMenu(
    actionManager: com.intellij.openapi.actionSystem.ActionManager,
    leafComponent: NlComponent?
  ): DefaultActionGroup {
    val group = DefaultActionGroup()

    if (leafComponent == null) {
      return group
    }

    // TODO: Add group for action context menu items
    when {
      mySurface.selectionModel.selection.count() > 1 -> addMultiSelectionGroup(group, actionManager)
      leafComponent == mySurface.currentNavigation -> addSurfaceGroup(group)
      leafComponent.isDestination -> addDestinationGroup(group, leafComponent, actionManager)
    }

    return group
  }

  private fun addMultiSelectionGroup(group: DefaultActionGroup, actionManager: com.intellij.openapi.actionSystem.ActionManager) {
    group.add(createNestedGraphGroup(mySurface.selectionModel.selection))

    group.addSeparator()
    group.add(actionManager.getAction(IdeActions.ACTION_DELETE))
  }

  private fun addSurfaceGroup(group: DefaultActionGroup) {
    group.add(ZoomInAction(mySurface))
    group.add(ZoomOutAction(mySurface))
    group.add(SetZoomAction(mySurface, ZoomType.FIT))

    group.addSeparator()
    group.add(gotoComponentAction)
  }

  private fun addDestinationGroup(
    group: DefaultActionGroup,
    component: NlComponent,
    actionManager: com.intellij.openapi.actionSystem.ActionManager
  ) {
    group.add(ActivateComponentAction(if (component.isNavigation) "Open" else "Edit", mySurface, component))

    group.addSeparator()
    group.add(createAddActionGroup(component))
    group.add(createNestedGraphGroup(listOf(component)))
    group.add(StartDestinationAction(component))

    group.addSeparator()
    group.add(actionManager.getAction(IdeActions.ACTION_DELETE))

    group.addSeparator()
    group.add(gotoComponentAction)
  }

  private fun createAddActionGroup(component: NlComponent): DefaultActionGroup {
    val group = DefaultActionGroup("Add Action", true)
    mySurface?.configuration?.resourceResolver?.let { group.add(ToDestinationAction(component, it)) }
    group.add(ToSelfAction(mySurface, component))
    group.add(ReturnToSourceAction(mySurface, component))
    group.add(AddGlobalAction(mySurface, component))
    return group
  }

  private fun createNestedGraphGroup(components: List<NlComponent>): DefaultActionGroup {
    // TODO: Add shortcut
    val group = DefaultActionGroup("Move to Nested Graph", true)
    val currentNavigation = mySurface.currentNavigation
    group.add(AddToNewGraphAction(mySurface, components))

    val resolver = mySurface?.configuration?.resourceResolver

    if (resolver != null && currentNavigation.childCount > 0) {
      group.addSeparator()
      for (graph in currentNavigation.children.filter { it.isNavigation && !components.contains(it) }) {
        group.add(AddToExistingGraphAction(mySurface, components, graph.getUiName(resolver), graph))
      }
    }

    return group
  }

  override fun addActions(
    group: DefaultActionGroup,
    component: NlComponent?,
    parent: NlComponent?,
    newSelection: List<NlComponent>,
    toolbar: Boolean
  ) {
    // This is called whenever the selection changes, but since our contents are static they can be cached.
    group.add(createDestinationMenu)
    group.add(addExistingDestinationMenu)
  }
}