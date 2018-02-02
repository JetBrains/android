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

import com.android.tools.idea.common.editor.ActionManager
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.intellij.openapi.actionSystem.DefaultActionGroup
import javax.swing.JComponent

/**
 * Provides and handles actions in the navigation editor
 */
class NavActionManager(surface: NavDesignSurface) : ActionManager<NavDesignSurface>(surface) {

  private val createDestinationMenu by lazy { CreateDestinationMenu(mySurface) }

  private val addExistingDestinationMenu by lazy { AddExistingDestinationMenu(mySurface) }

  override fun registerActionsShortcuts(component: JComponent) {
    // TODO
  }

  override fun createPopupMenu(
    actionManager: com.intellij.openapi.actionSystem.ActionManager,
    leafComponent: NlComponent?
  ): DefaultActionGroup {
    // TODO
    return DefaultActionGroup()
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