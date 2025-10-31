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
package com.android.tools.profilers;

import com.intellij.openapi.diagnostic.Logger

/** A simplified version of the Selection class from the `insights` module, used to avoid a module dependency. */
data class Selection<T>(val selected: T?, val items: List<T>) {
  fun deselect(): Selection<T> {
    return Selection(null, items)
  }

  fun select(item: T?): Selection<T> {
    if (item == null) return deselect()

    if (item !in items) {
      Logger.getInstance(Selection::class.java)
        .warn("Requested item $item is not among allowed selections $items.")
      return this
    }
    return Selection(item, items)
  }

  companion object {
    fun <T> emptySelection() = Selection<T>(null, emptyList())
  }
}

/** Creates a selection object of all enum values. */
inline fun <reified T : Enum<T>> selectionOf(initialValue: T? = null): Selection<T> {
  return Selection(initialValue, enumValues<T>().toList())
}
