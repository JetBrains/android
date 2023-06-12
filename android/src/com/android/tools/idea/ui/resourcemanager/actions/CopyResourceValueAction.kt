/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.idea.ui.resourcemanager.model.RESOURCE_DESIGN_ASSETS_KEY
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ide.CopyPasteManager
import org.jetbrains.android.util.AndroidBundle.message
import java.awt.datatransfer.StringSelection

class CopyResourceValueAction : AnAction(
  message("resource.explorer.copy.value.title"),
  message("resource.explorer.copy.value.description"),
  null) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible =
      e.getData(RESOURCE_DESIGN_ASSETS_KEY)?.singleOrNull()?.resourceItem?.resourceValue?.value?.isNotBlank() ?: false
  }

  override fun actionPerformed(e: AnActionEvent) {
    val value = e.getData(RESOURCE_DESIGN_ASSETS_KEY)?.singleOrNull()?.resourceItem?.resourceValue?.value
                ?: return
    CopyPasteManager.getInstance().setContents(StringSelection(value))
  }
}