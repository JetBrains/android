/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.actions

import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_ELEMENT
import com.android.tools.idea.compose.preview.PIN_EMOJI
import com.android.tools.idea.compose.preview.PinnedPreviewElementManager
import com.android.tools.idea.compose.preview.util.PreviewElementInstance
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ToggleAction
import org.jetbrains.kotlin.idea.refactoring.project

fun DataContext.getPreviewElementInstance(): PreviewElementInstance? = getData(COMPOSE_PREVIEW_ELEMENT) as? PreviewElementInstance


internal class PinPreviewElementAction(private val dataContextProvider: () -> DataContext)
  : ToggleAction(PIN_EMOJI, null, null) {

  override fun displayTextInToolbar(): Boolean = true

  override fun update(e: AnActionEvent) {
    super.update(e)

    // Only instances can be pinned (except pinned ones)
    e.presentation.isEnabledAndVisible = dataContextProvider().getData(COMPOSE_PREVIEW_ELEMENT) is PreviewElementInstance
  }

  override fun isSelected(e: AnActionEvent): Boolean =
    dataContextProvider().getPreviewElementInstance()?.let {
      PinnedPreviewElementManager.getInstance(e.dataContext.project).isPinned(it)
    } ?: false

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val project = e.dataContext.project
    dataContextProvider().getPreviewElementInstance()?.let {
      if (state)
        PinnedPreviewElementManager.getInstance(project).pin(it)
      else
        PinnedPreviewElementManager.getInstance(project).unpin(it)
    }
  }
}