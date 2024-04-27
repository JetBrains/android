/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.animation

import com.android.tools.idea.compose.preview.analytics.AnimationToolingEvent
import com.android.tools.idea.compose.preview.analytics.AnimationToolingUsageTracker
import com.google.wireless.android.sdk.stats.ComposeAnimationToolingEvent.ComposeAnimationToolingEventType

class AnimationTracker(private val eventLogger: AnimationToolingUsageTracker) {

  private fun logEvent(type: ComposeAnimationToolingEventType) {
    eventLogger.logEvent(AnimationToolingEvent(type))
  }

  fun openAnimationInspector() = logEvent(ComposeAnimationToolingEventType.OPEN_ANIMATION_INSPECTOR)

  fun closeAnimationInspector() =
    logEvent(ComposeAnimationToolingEventType.CLOSE_ANIMATION_INSPECTOR)

  fun animationInspectorAvailable() =
    logEvent(ComposeAnimationToolingEventType.ANIMATION_INSPECTOR_AVAILABLE)

  fun triggerPlayAction() = logEvent(ComposeAnimationToolingEventType.TRIGGER_PLAY_ACTION)

  fun triggerPauseAction() = logEvent(ComposeAnimationToolingEventType.TRIGGER_PAUSE_ACTION)

  fun enableLoopAction() = logEvent(ComposeAnimationToolingEventType.ENABLE_LOOP_ACTION)

  fun disableLoopAction() = logEvent(ComposeAnimationToolingEventType.DISABLE_LOOP_ACTION)

  fun changeAnimationSpeed(speedMultiplier: Float) {
    val event =
      AnimationToolingEvent(ComposeAnimationToolingEventType.CHANGE_ANIMATION_SPEED)
        .withAnimationMultiplier(speedMultiplier)
    eventLogger.logEvent(event)
  }

  fun triggerJumpToStartAction() =
    logEvent(ComposeAnimationToolingEventType.TRIGGER_JUMP_TO_START_ACTION)

  fun triggerJumpToEndAction() =
    logEvent(ComposeAnimationToolingEventType.TRIGGER_JUMP_TO_END_ACTION)

  fun changeStartState() = logEvent(ComposeAnimationToolingEventType.CHANGE_START_STATE)

  fun changeEndState() = logEvent(ComposeAnimationToolingEventType.CHANGE_END_STATE)

  fun triggerSwapStatesAction() =
    logEvent(ComposeAnimationToolingEventType.TRIGGER_SWAP_STATES_ACTION)

  fun clickAnimationInspectorTimeline() =
    logEvent(ComposeAnimationToolingEventType.CLICK_ANIMATION_INSPECTOR_TIMELINE)

  fun dragAnimationInspectorTimeline() =
    logEvent(ComposeAnimationToolingEventType.DRAG_ANIMATION_INSPECTOR_TIMELINE)

  fun expandAnimationCard() = logEvent(ComposeAnimationToolingEventType.EXPAND_ANIMATION_CARD)

  fun collapseAnimationCard() = logEvent(ComposeAnimationToolingEventType.COLLAPSE_ANIMATION_CARD)

  fun openAnimationInTab() = logEvent(ComposeAnimationToolingEventType.OPEN_ANIMATION_IN_TAB)

  fun closeAnimationTab() = logEvent(ComposeAnimationToolingEventType.CLOSE_ANIMATION_TAB)

  fun lockAnimation() = logEvent(ComposeAnimationToolingEventType.LOCK_ANIMATION)

  fun unlockAnimation() = logEvent(ComposeAnimationToolingEventType.UNLOCK_ANIMATION)

  fun resetTimeline() = logEvent(ComposeAnimationToolingEventType.RESET_TIMELINE)

  fun dragTimelineLine() = logEvent(ComposeAnimationToolingEventType.DRAG_TIMELINE_LINE)

  fun openPicker() = logEvent(ComposeAnimationToolingEventType.OPEN_PICKER)
}
