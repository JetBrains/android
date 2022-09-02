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

import com.android.tools.idea.common.analytics.DesignerUsageTrackerManager
import com.android.tools.idea.common.analytics.setApplicationId
import com.android.tools.idea.common.surface.DesignSurface
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.ComposeAnimationToolingEvent
import java.util.concurrent.Executor
import java.util.function.Consumer

/** Usage tracker implementation for the Compose Animation tooling. */
interface AnimationToolingUsageTracker {
  fun logEvent(event: AnimationToolingEvent): AndroidStudioEvent.Builder

  companion object {
    private val NOP_TRACKER = AnimationToolingNopTracker()
    private val MANAGER =
      DesignerUsageTrackerManager<AnimationToolingUsageTracker, DesignSurface<*>>(
        ::InternalAnimationToolingUsageTracker,
        NOP_TRACKER
      )

    fun getInstance(surface: DesignSurface<*>?) = MANAGER.getInstance(surface)
  }
}

/**
 * Empty [AnimationToolingUsageTracker] implementation, used when the user is not opt-in or in
 * tests.
 */
private class AnimationToolingNopTracker : AnimationToolingUsageTracker {
  override fun logEvent(event: AnimationToolingEvent) = event.createAndroidStudioEvent()
}

/**
 * Default [AnimationToolingUsageTracker] implementation that sends the event to the analytics
 * backend.
 */
private class InternalAnimationToolingUsageTracker(
  private val executor: Executor,
  private val surface: DesignSurface<*>?,
  private val studioEventTracker: Consumer<AndroidStudioEvent.Builder>
) : AnimationToolingUsageTracker {
  override fun logEvent(event: AnimationToolingEvent): AndroidStudioEvent.Builder {
    event.createAndroidStudioEvent().setApplicationId(surface).let {
      executor.execute { studioEventTracker.accept(it) }
      return it
    }
  }
}

/**
 * Represents a [ComposeAnimationToolingEvent] to be tracked, and uses the builder pattern to create
 * it.
 */
class AnimationToolingEvent(type: ComposeAnimationToolingEvent.ComposeAnimationToolingEventType) {

  private val eventBuilder = ComposeAnimationToolingEvent.newBuilder().setType(type)

  fun withAnimationMultiplier(animationMultiplier: Float): AnimationToolingEvent {
    eventBuilder.animationSpeedMultiplier = animationMultiplier
    return this
  }

  fun build(): ComposeAnimationToolingEvent {
    return eventBuilder.build()
  }
}

/** Creates and returns an [AndroidStudioEvent.Builder] from an [AnimationToolingEvent]. */
private fun AnimationToolingEvent.createAndroidStudioEvent(): AndroidStudioEvent.Builder {
  return AndroidStudioEvent.newBuilder()
    .setKind(AndroidStudioEvent.EventKind.COMPOSE_ANIMATION_TOOLING)
    .setComposeAnimationToolingEvent(build())
}
