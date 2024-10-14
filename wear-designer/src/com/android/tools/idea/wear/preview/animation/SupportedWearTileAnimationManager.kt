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
package com.android.tools.idea.wear.preview.animation

import com.android.tools.idea.preview.animation.AnimatedProperty
import com.android.tools.idea.preview.animation.AnimationTabs
import com.android.tools.idea.preview.animation.AnimationTracker
import com.android.tools.idea.preview.animation.AnimationUnit
import com.android.tools.idea.preview.animation.PlaybackControls
import com.android.tools.idea.preview.animation.SupportedAnimationManager
import com.android.tools.idea.preview.animation.Transition
import com.android.tools.idea.wear.preview.animation.ProtoAnimation.TYPE
import com.android.tools.idea.wear.preview.animation.state.NoopAnimationState
import com.android.tools.idea.wear.preview.animation.state.WearTileAnimationState
import com.android.tools.idea.wear.preview.animation.state.WearTileColorPickerState
import com.android.tools.idea.wear.preview.animation.state.WearTileFloatState
import com.android.tools.idea.wear.preview.animation.state.WearTileIntState
import java.awt.Color
import javax.swing.JComponent
import kotlinx.coroutines.CoroutineScope

/**
 * Implementation of [SupportedAnimationManager] for Wear Tiles.
 *
 * Key responsibilities include:
 *
 * Loading animation data. Setting up and managing the appropriate state manager for the animation
 * type. Syncing state between the UI (combo boxes) and the underlying animation data. Updating and
 * resetting the animation state.
 *
 * @param animation The animation object.
 * @param tracker The animation tracker.
 * @param getCurrentTime Function to get the current time in milliseconds in timeline.
 * @param executeInRenderSession A function that executes a runnable in the render session.
 * @param tabbedPane The animation tabs.
 * @param rootComponent The root component.
 * @param tabTitle The title of the tab.
 */
class SupportedWearTileAnimationManager(
  val animation: ProtoAnimation,
  private val tracker: AnimationTracker,
  private val getCurrentTime: () -> Int,
  private val executeInRenderSession: suspend (Boolean, () -> Unit) -> Unit,
  private val tabbedPane: AnimationTabs,
  private val rootComponent: JComponent,
  override val tabTitle: String,
  playbackControls: PlaybackControls,
  updateTimelineElementsCallback: suspend () -> Unit,
  parentScope: CoroutineScope,
  private val setClockTime: suspend (Int, Boolean) -> Unit,
) :
  SupportedAnimationManager(
    getCurrentTime,
    playbackControls,
    tabbedPane,
    rootComponent,
    tracker,
    executeInRenderSession,
    parentScope,
    updateTimelineElementsCallback,
  ) {

  public override fun loadTransitionFromLibrary(): Transition {
    val duration = animation.durationMs.toInt()
    val delay = animation.startDelayMs.toInt()

    val builder = AnimatedProperty.Builder().setStartTimeMs(delay).setEndTimeMs(duration + delay)

    for (ms in delay until duration + delay) {
      animation.setTime(ms.toLong())
      animation.getAnimationUnit().let { if (it is AnimationUnit.NumberUnit) builder.add(ms, it) }
    }

    animation.setTime(getCurrentTime().toLong())
    return Transition(mapOf(1 to builder.build()))
  }

  override suspend fun loadAnimatedPropertiesAtCurrentTime(longTimeout: Boolean) {
    executeInRenderSession(false) {
      animatedPropertiesAtCurrentTime =
        listOf(AnimationUnit.TimelineUnit("value", animation.getAnimationUnit()))
    }
  }

  override lateinit var animationState: WearTileAnimationState<*>

  override suspend fun setupInitialAnimationState() {
    executeInRenderSession(true) { animationState = animation.createStateManager(tracker) }
  }

  override suspend fun syncAnimationWithState() {
    executeInRenderSession(true) { animationState.updateAnimation(animation) }
    setClockTime(getCurrentTime(), false)
  }
}

private fun ProtoAnimation.createStateManager(
  tracker: AnimationTracker
): WearTileAnimationState<*> {
  return when (type) {
    TYPE.INT -> {
      WearTileIntState(tracker, startValueInt, endValueInt)
    }
    TYPE.FLOAT -> {
      WearTileFloatState(tracker, startValueFloat, endValueFloat)
    }
    TYPE.COLOR -> {
      WearTileColorPickerState(
        tracker,
        ColorUnit.parseColorUnit(startValueInt ?: Color.BLACK.rgb),
        ColorUnit.parseColorUnit(endValueInt ?: Color.WHITE.rgb),
      )
    }
    else -> NoopAnimationState
  }
}

private fun ProtoAnimation.getAnimationUnit(): AnimationUnit.Unit<*> {
  if (value == null) {
    throw IllegalStateException("Animation value is null")
  }
  return when (type) {
    TYPE.INT -> AnimationUnit.parseNumberUnit(value)
    TYPE.FLOAT -> AnimationUnit.parseNumberUnit(value)
    TYPE.COLOR -> ColorUnit.parseColorUnit(value)
    else -> AnimationUnit.UnitUnknown(value)
  } ?: AnimationUnit.UnitUnknown(value)
}
