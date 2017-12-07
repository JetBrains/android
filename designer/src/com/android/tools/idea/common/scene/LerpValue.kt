// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.common.scene

/**
 * [LerpValue] Represents a linear interpolation between two values.
 */
class LerpValue(val start: Int, val end: Int, val duration: Int) {
  private var startTime = -1L

  /**
   * Gets the interpolated value at the specified time. The start
   * time is measured from the first time getValue is called.
   */
  fun getValue(time: Long): Int {
    if (startTime == -1L) {
      startTime = time
      return start
    }

    val elapsed = time - startTime

    return if (elapsed >= duration) {
      end
    }
    else {
      start + ((end - start) * elapsed / duration).toInt()
    }
  }
}
