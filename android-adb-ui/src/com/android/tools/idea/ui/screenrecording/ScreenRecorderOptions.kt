/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.ui.screenrecording

/**
 * Contains options for recording device screen.
 */
internal class ScreenRecorderOptions(
  // Video size is given by width x height, defaults to device's main display resolution or 1280x720.
  val width: Int,
  val height: Int,

  // Bit rate in Mbps. Defaults to 4Mbps.
  val bitrateMbps: Int,

  // Display touches.
  val showTouches: Boolean,

  // Max recording duration, or zero to use the default.
  val timeLimitSec: Int,
)
