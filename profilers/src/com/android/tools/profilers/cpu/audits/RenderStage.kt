/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.profilers.cpu.audits

/**
 * When an Android frame is being drawn, it goes through stages in the render pipeline,
 * each of which performs a portion of the work needed to prepare a frame.
 */
enum class RenderStage {
  // App executes any operations in between two consecutive frames
  MISC_TIME,
  // App processes code inside of input callbacks
  INPUT,
  // App evaluates all the animators for this frame
  ANIMATION,
  // App walks through the view hierarchy and calculates new sizes and positions for invalidated views
  MEASURE_LAYOUT,
  // App creates and updates display lists for views
  DRAW,
  // App uploads bitmap information to the GPU
  SYNC_UPLOAD,
  // Android's 2D renderer issues commands to OpenGL to draw and redraw display lists
  COMMAND_ISSUE,
  // CPU waits for the GPU to finish its work
  SWAP_BUFFERS;
}