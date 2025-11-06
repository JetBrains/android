/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.insights.model.stacktrace

/** The frames of the thread's stack, along with name & analysis results. */
data class Stacktrace(
  // The title & subtitle of this thread
  val caption: Caption = Caption(),

  // Used to indicate that the analysis blames this Stacktrace
  val blames: Blames = Blames.UNKNOWN_BLAMED,

  // The frames of the Stacktrace
  val frames: List<Frame> = listOf(),
)
