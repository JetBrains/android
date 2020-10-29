/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.deviceManager.avdmanager

import com.android.sdklib.SdkVersionInfo
import java.util.Comparator

/** Compared API level strings numerically when possible  */
class ApiLevelComparator : Comparator<String> {
  override fun compare(s1: String, s2: String): Int {
    var api1 = -1 // not a valid API level
    var api2 = -1
    try {
      api1 = if (s1.isNotEmpty() && Character.isDigit(s1[0])) {
        s1.toInt()
      }
      else {
        SdkVersionInfo.getApiByPreviewName(s1, false)
      }
    }
    catch (e: NumberFormatException) {
      // ignore; still negative value
    }
    try {
      api2 = if (s2.isNotEmpty() && Character.isDigit(s2[0])) {
        s2.toInt()
      }
      else {
        SdkVersionInfo.getApiByPreviewName(s2, false)
      }
    }
    catch (e: NumberFormatException) {
      // ignore; still negative value
    }

    return when {
      api1 != -1 && api2 != -1 -> api1 - api2 // Descending order
      api1 != -1 -> -1 // Only the first value is a number: Sort preview platforms to the end
      api2 != -1 -> 1 // Only the second value is a number: Sort preview platforms to the end
      else -> s1.compareTo(s2) // Alphabetic sort when both API versions are codenames
    }
  }
}