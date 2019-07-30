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
package com.android.tools.idea.ui.resourcemanager.actions

import com.android.resources.ResourceType
import com.android.tools.idea.editors.theme.ResolutionUtils
import com.android.tools.idea.fonts.MoreFontsDialog
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.android.facet.AndroidFacet

/**
 * [AnAction] wrapper that calls the MoreFontsDialog to add new font resources.
 */
class AddFontAction(
  private val facet: AndroidFacet,
  private val createdResourceCallback: (String, ResourceType) -> Unit
): AnAction(MoreFontsDialog.ACTION_NAME, "Add font resources to project", null) {
  override fun actionPerformed(e: AnActionEvent) {
    val dialog = MoreFontsDialog(facet, null, false)
    if (dialog.showAndGet()) {
      dialog.resultingFont?.let {
        createdResourceCallback(
          // This returns a font name as a resource url '@font/name', but we just need the name.
          ResolutionUtils.getNameFromQualifiedName(ResolutionUtils.getQualifiedNameFromResourceUrl(it)),
          ResourceType.FONT)
      }
    }
  }
}
