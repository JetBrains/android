/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.animation.timeline

/** State of the [TimelineElement] in timeline. */
class ElementState(val title: String? = null) {
  private val valueOffsetListeners: MutableList<() -> Unit> = mutableListOf()

  /** The offset in ms for which the animation is shifted. */
  var valueOffset = 0
    set(value) {
      // Ignore repeated values.
      if (field != value) {
        field = value
        valueOffsetListeners.forEach { it() }
      }
    }

  fun addValueOffsetListener(listener: () -> Unit) {
    valueOffsetListeners.add(listener)
  }

  private val freezeListeners: MutableList<() -> Unit> = mutableListOf()

  /** If element is frozen in specified [frozenValue]. */
  var frozen = false
    set(value) {
      // Ignore repeated values.
      if (field != value) {
        field = value
        freezeListeners.forEach { it() }
      }
    }

  /** The value in ms in which the animation is frozen. */
  var frozenValue: Int = 0

  fun addFreezeListener(listener: () -> Unit) {
    freezeListeners.add(listener)
  }

  private val expandedListeners: MutableList<() -> Unit> = mutableListOf()
  var expanded = false
    set(value) {
      // Ignore repeated values.
      if (field != value) {
        field = value
        expandedListeners.forEach { it() }
      }
    }

  fun addExpandedListener(listener: () -> Unit) {
    expandedListeners.add(listener)
  }
}
