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

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Handles pub/sub notifications and bulk editing counts for [Configuration]. Optimized for high-concurrency environments using lock-free
 * data structures.
 */
class ConfigurationListeners {
  // CopyOnWriteArrayList allows safe, concurrent iteration without synchronized blocks.
  // It is highly optimized for observer patterns where reads vastly outnumber writes.
  private val listeners = CopyOnWriteArrayList<ConfigurationListener>()

  // AtomicInteger prevents thread contention when starting/finishing bulk edits
  private val bulkEditingCount = AtomicInteger(0)

  val isBulkEditing: Boolean
    get() = bulkEditingCount.get() > 0

  fun startBulkEditing() {
    bulkEditingCount.incrementAndGet()
  }

  fun finishBulkEditing(): Boolean {
    // Atomically decrements and checks if we just exited the LAST nested bulk edit
    return bulkEditingCount.decrementAndGet() == 0
  }

  fun addListener(listener: ConfigurationListener) {
    // TODO(b/475475082): Switch to addIfAbsent if being able to add duplicated doesn't
    // serve any purpose
    listeners.add(listener)
  }

  fun removeListener(listener: ConfigurationListener) {
    listeners.remove(listener)
  }

  fun notifyListeners(changedFlags: Int) {
    // Safe to iterate directly! No synchronized(listeners) block needed.
    for (listener in listeners) {
      listener.changed(changedFlags)
    }
  }
}
