/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.adtui.model

/**
 * Multiselection model of selections type [S] indexed by some keys.
 * The selection order is maintained.
 */
class MultiSelectionModel<S> : AspectModel<MultiSelectionModel.Aspect>() {
  private val currentSelections = LinkedHashMap<Any, Entry<S>>()
  var activeSelectionKey: Any? = null
    private set
  val selections: List<Entry<S>> get() = currentSelections.values.toList()

  fun<T: S> getFirstActiveSelectionItem(c: Class<T>): T? = currentSelections[activeSelectionKey]?.value?.firstOrNull(c::isInstance) as T?

  fun setSelection(key: Any, selections: Set<S>) = when {
    selections.isEmpty() -> removeSelection(key)
    selections != currentSelections[key] -> {
      activeSelectionKey = key
      currentSelections[key] = Entry(key, selections)
      changed(Aspect.CHANGE_SELECTION)
    }
    else -> {}
  }

  fun removeSelection(key: Any) {
    if (key in currentSelections) {
      currentSelections.remove(key)
      if (key == activeSelectionKey) {
        activeSelectionKey = null
      }
      changed(Aspect.CHANGE_SELECTION)
    }
  }

  fun clearSelection() {
    if (currentSelections.isNotEmpty()) {
      currentSelections.clear()
      activeSelectionKey = null
      changed(Aspect.CHANGE_SELECTION)
    }
  }

  /**
   * Make sure no selection is active, but retain all of them.
   */
  fun deselect() {
    if (activeSelectionKey != null) {
      activeSelectionKey = null
      changed(Aspect.CHANGE_SELECTION)
    }
  }

  enum class Aspect {
    CHANGE_SELECTION
  }

  data class Entry<S>(val key: Any, val value: Set<S>)
}