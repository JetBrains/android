/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.editor

/** Interface to control how to preview the animation. */
interface AnimationController {
  /** Indicates that the elapsed frame of animation needs to be setup again. */
  var forceElapsedReset: Boolean

  fun play()

  fun pause()

  fun stop()

  /** Get the current [PlayStatus] of the controlled animation. */
  fun getPlayStatus(): PlayStatus

  /**
   * Sets a new frame position. If newPositionMs is outside of the min and max values, the value
   * will be truncated to be within the range.
   */
  fun setFrameMs(frameMs: Long)

  fun getFrameMs(): Long

  /** Note: Set max time as -1 means it is an unlimited animation. */
  fun setMaxTimeMs(maxTimeMs: Long)

  fun getMaxTimeMs(): Long

  fun setLooping(enabled: Boolean)

  fun isLooping(): Boolean

  fun registerAnimationControllerListener(listener: AnimationControllerListener)
}

enum class PlayStatus {
  /** The animation is playing. */
  PLAY,
  /** The animation is paused. The animation frame is same as the last shown frame. */
  PAUSE,
  /** The animation is reset. The animation frame is same as its initial value. */
  STOP,
  /** The played animation reaches the end itself. The animation frame is same as its max time. */
  COMPLETE
}

interface AnimationControllerListener {
  fun onPlayStatusChanged(newStatus: PlayStatus) = Unit

  fun onCurrentFrameMsChanged(newFrameMs: Long) = Unit

  fun onMaxTimeMsChanged(newMaxTimeMs: Long) = Unit

  fun onLoopingChanged(enabled: Boolean) = Unit
}
