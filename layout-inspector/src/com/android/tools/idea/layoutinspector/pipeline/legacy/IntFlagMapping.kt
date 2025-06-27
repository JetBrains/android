/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.pipeline.legacy

/** Loose adoption of android.view.inspector.IntFlagMapping */
class IntFlagMapping {
  private val flags = ArrayList<Flag>()

  /**
   * Get a set of the names of enabled flags for a given property value.
   *
   * @param value The value of the property
   * @return The names of the enabled flags, empty if no flags enabled
   */
  fun of(value: Int): MutableSet<String> {
    val enabledFlagNames = mutableSetOf<String>()
    var alreadyIncluded = 0

    for (flag in flags) {
      if (flag.isEnabledFor(value) && (alreadyIncluded and flag.target != flag.target)) {
        enabledFlagNames.add(flag.name)
        alreadyIncluded = alreadyIncluded or flag.target
      }
    }

    return enabledFlagNames
  }

  /**
   * Add a flag to the map.
   *
   * @param mask The bit mask to compare to and with a value
   * @param target The target value to compare the masked value with
   * @param name The name of the flag to include if enabled
   */
  fun add(mask: Int, target: Int, name: String) {
    flags.add(Flag(mask, target, name))
  }

  /** Inner class that holds the name, mask, and target value of a flag */
  private class Flag(private val mask: Int, val target: Int, val name: String) {

    /**
     * Compare the supplied property value against the mask and target.
     *
     * @param value The value to check
     * @return True if this flag is enabled
     */
    fun isEnabledFor(value: Int): Boolean {
      return value and mask == target
    }
  }
}
