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

import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_MANAGER
import com.android.tools.idea.compose.preview.isPreviewRefreshing
import com.android.tools.idea.preview.actions.disabledIf
import com.android.tools.idea.preview.actions.hasSceneViewErrors
import com.android.tools.idea.preview.modes.PreviewMode
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup

// TODO(b/292057010) Enable group filtering for Gallery mode.
private class ComposePreviewDefaultWrapper(actions: List<AnAction>) : DefaultActionGroup(actions) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    super.update(e)

    e.getData(COMPOSE_PREVIEW_MANAGER)?.let {
      e.presentation.isVisible = it.mode.value is PreviewMode.Default
    }
  }
}

private class ComposePreviewUiCheckWrapper(actions: List<AnAction>) : DefaultActionGroup(actions) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    super.update(e)

    e.getData(COMPOSE_PREVIEW_MANAGER)?.let {
      e.presentation.isVisible = it.mode.value is PreviewMode.UiCheck
    }
  }
}

/**
 * Makes the given action only visible when the Compose preview is not in interactive or animation
 * modes. Returns an [ActionGroup] that handles the visibility.
 */
internal fun AnAction.visibleOnlyInComposeDefaultPreview(): ActionGroup =
  ComposePreviewDefaultWrapper(listOf(this))

/**
 * Makes the given action only visible when the Compose preview is in UI Check mode. Returns an
 * [ActionGroup] that handles the visibility.
 */
internal fun AnAction.visibleOnlyInUiCheck(): ActionGroup =
  ComposePreviewUiCheckWrapper(listOf(this))

/**
 * The given disables the actions if any surface is refreshing or if the [sceneView] contains
 * errors.
 */
fun List<AnAction>.disabledIfRefreshingOrRenderErrors(): List<AnAction> = disabledIf { context ->
  isPreviewRefreshing(context) || hasSceneViewErrors(context)
}
