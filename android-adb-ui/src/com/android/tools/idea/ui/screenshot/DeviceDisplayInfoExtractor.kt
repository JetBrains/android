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
package com.android.tools.idea.ui.screenshot

/** Extracts device display information for a given [displayId]. */
class DeviceDisplayInfoExtractor(displayId: Int) {
  private val regex =
    Regex(
      "\\s(DisplayDeviceInfo\\W[^\\n]* state ON,[^\\n]*)}\\n" +
        "([^\\n]+\\n)*" + // match anything that is not two consecutive carriage returns
        "\\s+mCurrentLayerStack=$displayId\\W",
      RegexOption.MULTILINE,
    )

  /**
   * Returns the line starting with "DisplayDeviceInfo" that corresponds to the given [displayId].
   */
  fun extractFromDumpSys(dumpsysOutput: String): String {
    return regex.find(dumpsysOutput)?.groupValues?.get(1) ?: ""
  }
}
