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
package com.android.tools.idea.preview.animation.actions

import com.android.tools.idea.preview.PreviewBundle.message
import com.android.tools.idea.preview.animation.AnimationTracker
import com.android.tools.idea.preview.animation.TimelinePanel
import com.android.tools.idea.preview.animation.timeline.ElementState
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import icons.StudioIcons
import java.util.function.Supplier
import kotlinx.coroutines.flow.MutableStateFlow

/** A toggle action to freeze / unfreeze animation. */
class FreezeAction(
  private val timelinePanel: TimelinePanel,
  val state: MutableStateFlow<ElementState>,
  val tracker: AnimationTracker,
) :
  ToggleAction(
    Supplier { message("animation.inspector.action.freeze") },
    StudioIcons.Compose.Toolbar.FREEZE_ANIMATION,
  ) {

  override fun setSelected(e: AnActionEvent, frozen: Boolean) {
    state.value = state.value.copy(frozen = frozen, frozenValue = timelinePanel.value)
    if (frozen) {
      tracker.lockAnimation()
      e.presentation.text = message("animation.inspector.action.unfreeze")
    } else {
      tracker.unlockAnimation()
      e.presentation.text = message("animation.inspector.action.freeze")
    }
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    return state.value.frozen
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabled = true
    e.presentation.text = message("animation.inspector.action.freeze")
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}
