/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.preview.representation

import com.android.tools.idea.concurrency.asCollection
import com.android.tools.idea.preview.PsiPreviewElementInstance
import com.android.tools.idea.preview.flow.PreviewElementFilter
import com.android.tools.idea.preview.flow.PreviewFlowManager
import com.android.tools.idea.preview.modes.FOCUS_MODE_LAYOUT_OPTION
import com.android.tools.idea.preview.modes.PREVIEW_LAYOUT_OPTIONS
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.preview.modes.PreviewModeManager
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentation
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentationState
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import kotlin.text.isNullOrEmpty

/** Key for the persistent group state for the Preview. */
private const val SELECTED_GROUP_KEY = "selectedGroup"

/** Key for persisting the selected layout manager. */
private const val LAYOUT_KEY = "previewLayout"

/**
 * Common class that handles persisting and restoring Preview Representations. The calls to
 * [PreviewRepresentation.getState] and [PreviewRepresentation.setState] should delegate to this
 * class's [getState] and [setState] methods respectively. [restoreState] should be called after the
 * preview has loaded the initial state through [setState] and is ready to restore the state.
 */
class CommonPreviewStateManager<T : PsiPreviewElementInstance>(
  private val surfaceProvider: () -> NlDesignSurface,
  private val currentGroupFilterProvider: () -> PreviewElementFilter.Group<T>?,
  private val previewFlowManager: PreviewFlowManager<T>,
  private val previewModeManager: PreviewModeManager,
) {

  /**
   * Callback first time after the preview has loaded the initial state, and it's ready to restore
   * any saved state.
   */
  private var onRestoreState: (() -> Unit)? = null

  /**
   * Returns the state to be saved.
   *
   * @see [PreviewRepresentation.getState]
   */
  fun getState(): PreviewRepresentationState {
    val selectedGroupName = currentGroupFilterProvider()?.filterGroup?.name ?: ""
    val selectedLayoutName =
      surfaceProvider().layoutManagerSwitcher?.currentLayoutOption?.value?.displayName ?: ""
    return mapOf(SELECTED_GROUP_KEY to selectedGroupName, LAYOUT_KEY to selectedLayoutName)
  }

  /**
   * Restore any saved state within the manager after the preview representation has been
   * instantiated. [restoreState] needs to be called once the preview is ready to be restored.
   *
   * @see PreviewRepresentation.setState
   * @see restoreState
   */
  fun setState(state: PreviewRepresentationState) {
    val selectedGroupName = state[SELECTED_GROUP_KEY]
    val previewLayoutName = state[LAYOUT_KEY]
    onRestoreState = {
      if (!selectedGroupName.isNullOrEmpty()) {
        previewFlowManager.availableGroupsFlow.value
          .find { it.name == selectedGroupName }
          ?.let { previewFlowManager.groupFilter = it }
      }

      PREVIEW_LAYOUT_OPTIONS.find { it.displayName == previewLayoutName }
        ?.let {
          // If focus mode was selected before - need to restore this type of layout.
          if (it == FOCUS_MODE_LAYOUT_OPTION) {
            previewFlowManager.allPreviewElementsFlow.value.asCollection().firstOrNull().let {
              previewElement ->
              previewModeManager.setMode(PreviewMode.Focus(previewElement))
            }
          } else {
            previewModeManager.setMode(PreviewMode.Default(it))
          }
        }
    }
  }

  /**
   * This method should be called after the preview has loaded the initial state through [setState]
   * and is ready to restore the state.
   *
   * @see setState
   */
  fun restoreState() {
    onRestoreState?.invoke()
    onRestoreState = null
  }
}
