/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.configurations

import com.google.common.collect.ImmutableList

/** Handles pub/sub notifications and bulk editing counts for [Configuration]. */
class ConfigurationListeners {
  private val listeners: MutableList<ConfigurationListener> = ArrayList()
  private var bulkEditingCount = 0

  fun startBulkEditing() {
    bulkEditingCount++
  }

  fun finishBulkEditing(): Boolean {
    bulkEditingCount--
    return bulkEditingCount == 0
  }

  val isBulkEditing: Boolean
    get() = bulkEditingCount > 0

  fun addListener(listener: ConfigurationListener) {
    synchronized(listeners) { listeners.add(listener) }
  }

  fun removeListener(listener: ConfigurationListener) {
    synchronized(listeners) { listeners.remove(listener) }
  }

  fun notifyListeners(changedFlags: Int) {
    val currentListeners: ImmutableList<ConfigurationListener>
    synchronized(listeners) { currentListeners = ImmutableList.copyOf(listeners) }
    for (listener in currentListeners) {
      listener.changed(changedFlags)
    }
  }
}
