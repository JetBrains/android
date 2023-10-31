/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.analytics

/**
 * Class with metadata related to filter operations, used for analytics purposes.
 */
class FilterMetadata {
  enum class View {
    UNKNOWN_FILTER_VIEW,
    CPU_TOP_DOWN,
    CPU_BOTTOM_UP,
    CPU_FLAME_CHART,
    CPU_CALL_CHART,
    MEMORY_PACKAGE,
    MEMORY_CLASS,
    MEMORY_CALLSTACK,
    NETWORK_THREADS,
    NETWORK_CONNECTIONS
  }

  var view = View.UNKNOWN_FILTER_VIEW
  var filterTextLength = 0
  var totalElementCount = 0
  var matchedElementCount = 0
  var featuresUsed = 0
    private set

  fun setFeaturesUsed(isMatchCase: Boolean, isRegex: Boolean) {
    featuresUsed = 0

    if (isMatchCase) {
      featuresUsed = featuresUsed or MATCH_CASE // bit-wise or
    }

    if (isRegex) {
      featuresUsed = featuresUsed or IS_REGEX // bit-wise or
    }
  }

  companion object {
    var MATCH_CASE = 1
    var IS_REGEX = 2
  }
}
