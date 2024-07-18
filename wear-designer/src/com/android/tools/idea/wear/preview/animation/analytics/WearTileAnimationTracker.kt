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
package com.android.tools.idea.wear.preview.animation.analytics

import com.android.tools.idea.preview.animation.AnimationTracker
import com.google.wireless.android.sdk.stats.WearTileAnimationToolingEvent.WearTileAnimationToolingEventType

class WearTileAnimationTracker
internal constructor(private val eventLogger: AnimationToolingUsageTracker) : AnimationTracker {

  private fun logEvent(type: WearTileAnimationToolingEventType) {
    eventLogger.logEvent(AnimationToolingEvent(type))
  }

  override fun openAnimationInspector() =
    logEvent(WearTileAnimationToolingEventType.OPEN_ANIMATION_INSPECTOR)

  override fun closeAnimationInspector() =
    logEvent(WearTileAnimationToolingEventType.CLOSE_ANIMATION_INSPECTOR)

  override fun animationInspectorAvailable() =
    logEvent(WearTileAnimationToolingEventType.ANIMATION_INSPECTOR_AVAILABLE)

  override fun triggerPlayAction() = logEvent(WearTileAnimationToolingEventType.TRIGGER_PLAY_ACTION)

  override fun triggerPauseAction() =
    logEvent(WearTileAnimationToolingEventType.TRIGGER_PAUSE_ACTION)

  override fun enableLoopAction() = logEvent(WearTileAnimationToolingEventType.ENABLE_LOOP_ACTION)

  override fun disableLoopAction() = logEvent(WearTileAnimationToolingEventType.DISABLE_LOOP_ACTION)

  override fun changeAnimationSpeed(speedMultiplier: Float) {
    val event =
      AnimationToolingEvent(WearTileAnimationToolingEventType.CHANGE_ANIMATION_SPEED)
        .withAnimationMultiplier(speedMultiplier)
    eventLogger.logEvent(event)
  }

  override fun triggerJumpToStartAction() =
    logEvent(WearTileAnimationToolingEventType.TRIGGER_JUMP_TO_START_ACTION)

  override fun triggerJumpToEndAction() =
    logEvent(WearTileAnimationToolingEventType.TRIGGER_JUMP_TO_END_ACTION)

  override fun changeStartState() = logEvent(WearTileAnimationToolingEventType.CHANGE_START_STATE)

  override fun changeEndState() = logEvent(WearTileAnimationToolingEventType.CHANGE_END_STATE)

  override fun triggerSwapStatesAction() =
    logEvent(WearTileAnimationToolingEventType.TRIGGER_SWAP_STATES_ACTION)

  override fun clickAnimationInspectorTimeline() =
    logEvent(WearTileAnimationToolingEventType.CLICK_ANIMATION_INSPECTOR_TIMELINE)

  override fun dragAnimationInspectorTimeline() =
    logEvent(WearTileAnimationToolingEventType.DRAG_ANIMATION_INSPECTOR_TIMELINE)

  override fun expandAnimationCard() =
    logEvent(WearTileAnimationToolingEventType.EXPAND_ANIMATION_CARD)

  override fun collapseAnimationCard() =
    logEvent(WearTileAnimationToolingEventType.COLLAPSE_ANIMATION_CARD)

  override fun openAnimationInTab() =
    logEvent(WearTileAnimationToolingEventType.OPEN_ANIMATION_IN_TAB)

  override fun closeAnimationTab() = logEvent(WearTileAnimationToolingEventType.CLOSE_ANIMATION_TAB)

  override fun lockAnimation() = logEvent(WearTileAnimationToolingEventType.LOCK_ANIMATION)

  override fun unlockAnimation() = logEvent(WearTileAnimationToolingEventType.UNLOCK_ANIMATION)

  override fun resetTimeline() = logEvent(WearTileAnimationToolingEventType.RESET_TIMELINE)

  override fun dragTimelineLine() = logEvent(WearTileAnimationToolingEventType.DRAG_TIMELINE_LINE)

  override fun openPicker() = logEvent(WearTileAnimationToolingEventType.OPEN_PICKER)
}
