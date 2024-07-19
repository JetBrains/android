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
import com.android.tools.idea.preview.animation.AnimationTracker
import com.google.wireless.android.sdk.stats.ComposeAnimationToolingEvent.ComposeAnimationToolingEventType

class ComposeAnimationTracker(private val eventLogger: AnimationToolingUsageTracker) :
  AnimationTracker {

  private fun logEvent(type: ComposeAnimationToolingEventType) {
    eventLogger.logEvent(AnimationToolingEvent(type))
  }

  override fun openAnimationInspector() =
    logEvent(ComposeAnimationToolingEventType.OPEN_ANIMATION_INSPECTOR)

  override fun closeAnimationInspector() =
    logEvent(ComposeAnimationToolingEventType.CLOSE_ANIMATION_INSPECTOR)

  override fun animationInspectorAvailable() =
    logEvent(ComposeAnimationToolingEventType.ANIMATION_INSPECTOR_AVAILABLE)

  override fun triggerPlayAction() = logEvent(ComposeAnimationToolingEventType.TRIGGER_PLAY_ACTION)

  override fun triggerPauseAction() =
    logEvent(ComposeAnimationToolingEventType.TRIGGER_PAUSE_ACTION)

  override fun enableLoopAction() = logEvent(ComposeAnimationToolingEventType.ENABLE_LOOP_ACTION)

  override fun disableLoopAction() = logEvent(ComposeAnimationToolingEventType.DISABLE_LOOP_ACTION)

  override fun changeAnimationSpeed(speedMultiplier: Float) {
    val event =
      AnimationToolingEvent(ComposeAnimationToolingEventType.CHANGE_ANIMATION_SPEED)
        .withAnimationMultiplier(speedMultiplier)
    eventLogger.logEvent(event)
  }

  override fun triggerJumpToStartAction() =
    logEvent(ComposeAnimationToolingEventType.TRIGGER_JUMP_TO_START_ACTION)

  override fun triggerJumpToEndAction() =
    logEvent(ComposeAnimationToolingEventType.TRIGGER_JUMP_TO_END_ACTION)

  override fun changeStartState() = logEvent(ComposeAnimationToolingEventType.CHANGE_START_STATE)

  override fun changeEndState() = logEvent(ComposeAnimationToolingEventType.CHANGE_END_STATE)

  override fun triggerSwapStatesAction() =
    logEvent(ComposeAnimationToolingEventType.TRIGGER_SWAP_STATES_ACTION)

  override fun clickAnimationInspectorTimeline() =
    logEvent(ComposeAnimationToolingEventType.CLICK_ANIMATION_INSPECTOR_TIMELINE)

  override fun dragAnimationInspectorTimeline() =
    logEvent(ComposeAnimationToolingEventType.DRAG_ANIMATION_INSPECTOR_TIMELINE)

  override fun expandAnimationCard() =
    logEvent(ComposeAnimationToolingEventType.EXPAND_ANIMATION_CARD)

  override fun collapseAnimationCard() =
    logEvent(ComposeAnimationToolingEventType.COLLAPSE_ANIMATION_CARD)

  override fun openAnimationInTab() =
    logEvent(ComposeAnimationToolingEventType.OPEN_ANIMATION_IN_TAB)

  override fun closeAnimationTab() = logEvent(ComposeAnimationToolingEventType.CLOSE_ANIMATION_TAB)

  override fun lockAnimation() = logEvent(ComposeAnimationToolingEventType.LOCK_ANIMATION)

  override fun unlockAnimation() = logEvent(ComposeAnimationToolingEventType.UNLOCK_ANIMATION)

  override fun resetTimeline() = logEvent(ComposeAnimationToolingEventType.RESET_TIMELINE)

  override fun dragTimelineLine() = logEvent(ComposeAnimationToolingEventType.DRAG_TIMELINE_LINE)

  override fun openPicker() = logEvent(ComposeAnimationToolingEventType.OPEN_PICKER)
}
