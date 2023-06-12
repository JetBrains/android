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
package com.android.tools.idea.insights

import com.intellij.openapi.diagnostic.Logger

/** Represents a selection within a list. */
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

/** Represents a selection with potentially multiple selected items. */
data class MultiSelection<T>(val selected: Set<T>, val items: List<T>) {
  fun toggle(item: T): MultiSelection<T> {
    if (item !in items) {
      return this
    }

    if (item in selected) {
      return MultiSelection(selected - item, items)
    }

    return MultiSelection(selected + item, items)
  }

  fun select(item: T): MultiSelection<T> {
    if (item !in items || item in selected) {
      return this
    }

    return MultiSelection(selected + item, items)
  }

  fun selectMatching(predicate: (T) -> Boolean): MultiSelection<T> {
    val newItems = items.filter(predicate)
    return if (newItems == selected) this else MultiSelection(newItems.toSet(), items)
  }
  fun deselect(item: T): MultiSelection<T> = MultiSelection(selected - item, this.items)

  fun allSelected() = items.toSet() == selected

  companion object {
    fun <T> emptySelection() = MultiSelection<T>(emptySet(), emptyList())
  }
}

/** Creates a multi selection object of all enum values. */
inline fun <reified T : Enum<T>> multiSelectionOf(
  initialValue: Set<T> = emptySet()
): MultiSelection<T> {
  return MultiSelection(initialValue, enumValues<T>().toList())
}
