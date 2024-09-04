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
package com.android.tools.idea.preview

import com.android.tools.idea.preview.animation.AnimationTracker

val NoopAnimationTracker =
  object : AnimationTracker {
    override fun openAnimationInspector() {}

    override fun closeAnimationInspector() {}

    override fun animationInspectorAvailable() {}

    override fun triggerPlayAction() {}

    override fun triggerPauseAction() {}

    override fun enableLoopAction() {}

    override fun disableLoopAction() {}

    override fun changeAnimationSpeed(speedMultiplier: Float) {}

    override fun triggerJumpToStartAction() {}

    override fun triggerJumpToEndAction() {}

    override fun clickAnimationInspectorTimeline() {}

    override fun dragAnimationInspectorTimeline() {}

    override fun expandAnimationCard() {}

    override fun collapseAnimationCard() {}

    override fun openAnimationInTab() {}

    override fun closeAnimationTab() {}

    override fun resetTimeline() {}

    override fun dragTimelineLine() {}

    override fun lockAnimation() {}

    override fun unlockAnimation() {}

    override fun openPicker() {}

    override fun triggerSwapStatesAction() {}

    override fun changeEndState() {}

    override fun changeStartState() {}
  }

/**
 * Send the [request] to the refresh manager and wait for it to be actually enqueued.
 *
 * Note that it doesn't wait for the request to be actually processed.
 */
internal suspend fun PreviewRefreshManager.requestRefreshSync(request: PreviewRefreshRequest) {
  this.requestRefreshForTest(request).join()
}
