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
package com.android.tools.idea.logcat.util

/**
 * A [LinkedHashSet] that orders elements by the time they were added and evicts elements if it
 * exceeds a maximum size.
 *
 * This class is intended to hold a set of Logcat tags & package names. While we do not expect these
 * to exceed a safe size, this class ensures that they don't.
 *
 * In practice, the actual size should not exceed several hundred elements.
 */
internal class MostRecentlyAddedSet<T>(private var maxSize: Int) : LinkedHashSet<T>() {
  init {
    assert(maxSize > 0)
  }

  /**
   * Add an element.
   *
   * If the element already exists, put it at the end of the list. If the addition results in
   * exceeding [maxSize], remove the first (oldest) element.
   */
  override fun add(element: T): Boolean {
    val exists = remove(element)
    if (!exists && size == maxSize) {
      remove(first())
    }
    super.add(element)
    return !exists
  }
}
