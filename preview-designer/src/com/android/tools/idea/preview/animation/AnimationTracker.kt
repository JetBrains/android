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
package com.android.tools.idea.preview.animation

interface AnimationTracker {
  fun openAnimationInspector()

  fun closeAnimationInspector()

  fun animationInspectorAvailable()

  fun triggerPlayAction()

  fun triggerPauseAction()

  fun enableLoopAction()

  fun disableLoopAction()

  fun changeAnimationSpeed(speedMultiplier: Float)

  fun triggerJumpToStartAction()

  fun triggerJumpToEndAction()

  fun clickAnimationInspectorTimeline()

  fun dragAnimationInspectorTimeline()

  fun expandAnimationCard()

  fun collapseAnimationCard()

  fun openAnimationInTab()

  fun closeAnimationTab()

  fun resetTimeline()

  fun dragTimelineLine()

  fun lockAnimation()

  fun unlockAnimation()
}
