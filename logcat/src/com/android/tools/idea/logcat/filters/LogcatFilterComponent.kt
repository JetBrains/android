/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.logcat.filters

import com.android.annotations.concurrency.UiThread
import com.intellij.ui.FilterComponent

/**
 * A text field to be used in filter definition.
 *
 * TODO(aalbert):
 *  Currently, implemented using a [FilterComponent] but will probably be changed to a from-scratch implementation to be able to support an
 *  inline regex icon button similar to SearchReplaceComponent.
 */
internal class LogcatFilterComponent(historyKey: String, historySize: Int = 10)
  : FilterComponent(historyKey, historySize, /* onTheFlyUpdate=*/ true) {
  private val listeners = mutableListOf<FilterChangeListener>()

  @UiThread
  override fun filter() {
    for (listener in listeners) {
      listener.onFilterChange(this)
    }
  }

  fun addFilterChangeListener(listener: FilterChangeListener) {
    listeners.add(listener)
  }

  internal interface FilterChangeListener {
    fun onFilterChange(logcatFilterComponent: LogcatFilterComponent)
  }
}
