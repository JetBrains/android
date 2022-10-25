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
package com.android.tools.idea.compose.preview.animation.actions

import com.android.tools.idea.compose.preview.animation.AnimationPreviewState
import com.android.tools.idea.compose.preview.animation.ComposeAnimationEventTracker
import com.android.tools.idea.compose.preview.animation.timeline.ElementState
import com.android.tools.idea.compose.preview.message
import com.google.wireless.android.sdk.stats.ComposeAnimationToolingEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import icons.StudioIcons
import java.util.function.Supplier

/** A toggle action to freeze / unfreeze animation. */
class FreezeAction(
  private val previewState: AnimationPreviewState,
  val state: ElementState,
  val tracker: ComposeAnimationEventTracker
) :
  ToggleAction(
    Supplier { message("animation.inspector.action.freeze") },
    StudioIcons.Compose.Toolbar.FREEZE_ANIMATION
  ) {

  override fun setSelected(e: AnActionEvent, frozen: Boolean) {
    state.frozen = frozen
    if (frozen) {
      tracker(ComposeAnimationToolingEvent.ComposeAnimationToolingEventType.LOCK_ANIMATION)
      e.presentation.text = message("animation.inspector.action.unfreeze")
    } else {
      tracker(ComposeAnimationToolingEvent.ComposeAnimationToolingEventType.UNLOCK_ANIMATION)
      e.presentation.text = message("animation.inspector.action.freeze")
    }
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    return state.frozen
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    if (e.presentation.isEnabled != previewState.isCoordinationAvailable()) {
      e.presentation.isEnabled = previewState.isCoordinationAvailable()
      e.presentation.text =
        if (previewState.isCoordinationAvailable()) message("animation.inspector.action.freeze")
        else message("animation.inspector.coordination.unavailable.freeze.animation")
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}
