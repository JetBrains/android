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
package com.android.tools.idea.compose.preview.analytics

import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.ComposeAnimationToolingEvent
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AnimationToolingUsageTrackerTest {
  private lateinit var animationToolingUsageTracker: AnimationToolingUsageTracker

  @Before
  fun setUp() {
    animationToolingUsageTracker = AnimationToolingUsageTracker.getInstance(null)
  }

  @Test
  fun testLogEvent() {
    val animationToolingEvent =
      AnimationToolingEvent(
        ComposeAnimationToolingEvent.ComposeAnimationToolingEventType.CHANGE_START_STATE
      )
    val androidStudioEvent = animationToolingUsageTracker.logEvent(animationToolingEvent)

    assertEquals(AndroidStudioEvent.EventKind.COMPOSE_ANIMATION_TOOLING, androidStudioEvent.kind)
    assertEquals(
      ComposeAnimationToolingEvent.ComposeAnimationToolingEventType.CHANGE_START_STATE,
      androidStudioEvent.composeAnimationToolingEvent.type
    )
  }

  @Test
  fun testSpeedMultiplier() {
    val animationToolingEvent =
      AnimationToolingEvent(
          ComposeAnimationToolingEvent.ComposeAnimationToolingEventType.CHANGE_ANIMATION_SPEED
        )
        .withAnimationMultiplier(1.5f)

    val composeAnimationToolingEvent =
      animationToolingUsageTracker.logEvent(animationToolingEvent).composeAnimationToolingEvent
    assertEquals(
      ComposeAnimationToolingEvent.ComposeAnimationToolingEventType.CHANGE_ANIMATION_SPEED,
      composeAnimationToolingEvent.type
    )
    assertEquals(1.5f, composeAnimationToolingEvent.animationSpeedMultiplier)
  }
}
