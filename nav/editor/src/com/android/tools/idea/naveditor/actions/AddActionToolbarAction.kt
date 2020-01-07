/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.naveditor.actions

import com.android.tools.idea.naveditor.property.inspector.AddActionDialog
import com.android.tools.idea.naveditor.property.inspector.showAndUpdateFromDialog
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.google.wireless.android.sdk.stats.NavEditorEvent
import com.intellij.openapi.actionSystem.AnActionEvent
import icons.StudioIcons
import org.jetbrains.android.dom.navigation.NavActionElement

class AddActionToolbarAction(surface: NavDesignSurface) :
  ToolbarAction(surface, "Add action", StudioIcons.NavEditor.Toolbar.ACTION) {

  override fun isEnabled(): Boolean = surface.selectionModel.selection.let {
    if (it.size != 1) {
      return false
    }

    return supportsSubtag(it[0], NavActionElement::class.java)
  }

  override fun actionPerformed(e: AnActionEvent) {
    surface.selectionModel.selection.firstOrNull()?.let {
      val dialog = AddActionDialog(AddActionDialog.Defaults.NORMAL, null, it, NavEditorEvent.Source.TOOLBAR)
      showAndUpdateFromDialog(dialog, surface, false)
    }
  }
}


