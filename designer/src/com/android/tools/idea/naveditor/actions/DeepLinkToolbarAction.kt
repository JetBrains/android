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

import com.android.tools.idea.naveditor.property.inspector.AddDeeplinkDialog
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.WriteCommandAction
import icons.StudioIcons
import org.jetbrains.android.dom.navigation.DeeplinkElement

class DeepLinkToolbarAction(surface: NavDesignSurface) :
  ToolbarAction(surface, "Add deep link", StudioIcons.NavEditor.Surface.DEEPLINK) {

  override fun isEnabled(): Boolean = surface.selectionModel.selection.let {
    if (it.size != 1) {
      return false
    }

    return supportsSubtag(it[0], DeeplinkElement::class.java)
  }

  override fun actionPerformed(e: AnActionEvent?) {
    surface.selectionModel.selection.firstOrNull()?.let {
      WriteCommandAction.runWriteCommandAction(it.model.project) {
        val dialog = AddDeeplinkDialog(null, it)
        if (dialog.showAndGet()) {
          dialog.save()
        }
      }
    }
  }
}
