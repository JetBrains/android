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
package com.android.tools.idea.common.scene

object ScenePicker {
  @JvmRecord data class HitResult(val `object`: Any, val distance: Double)

  /** Interface that must be used to find hits within the scene. */
  interface Reader {
    fun find(x: Int, y: Int): List<HitResult>
  }

  /** Interface used by creators of scenes to populate with the objects that could be hit. */
  interface Writer {
    fun addLine(e: Any, range: Int, x1: Int, y1: Int, x2: Int, y2: Int, width: Int)

    fun addRect(e: Any, range: Int, x1: Int, y1: Int, x2: Int, y2: Int)

    fun addCircle(e: Any, range: Int, x1: Int, y1: Int, r: Int)

    fun addCurveTo(
      e: Any,
      range: Int,
      x1: Int,
      y1: Int,
      x2: Int,
      y2: Int,
      x3: Int,
      y3: Int,
      x4: Int,
      y4: Int,
      width: Int,
    )
  }
}
