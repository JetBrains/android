/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.streaming

import com.intellij.ide.util.PropertiesComponent
import kotlin.math.min

/**
 * Persistent list of strings. The oldest items are removed from the list if it grows larger
 * than [maxSize]. Calling the [contains] method is considered use and moves the element
 * to the head of the list. All methods are O([size]). Not thread safe.
 */
internal class PersistentLruStringList(
  private val properties: PropertiesComponent,
  private val persistenceKey: String,
  private val maxSize: Int
) {

  val size
    get() = values.size

  private val values: ArrayDeque<String>

  init {
    val persistentValues = properties.getList(persistenceKey) ?: emptyList()
    val n = min(persistentValues.size, maxSize)
    values = ArrayDeque(n)
    for (i in 0 until n) {
      values.add(persistentValues[i])
    }
  }

  fun add(value: String): Boolean {
    val i = values.indexOf(value)
    if (i == 0) {
      return false
    }
    if (i > 0) {
      values.removeAt(i)
      values.add(0, value)
      updatePersistentValue()
      return false
    }
    if (values.size >= maxSize) {
      values.removeLast()
    }
    values.add(0, value)
    updatePersistentValue()
    return true
  }

  fun remove(value: String): Boolean {
    val removed = values.remove(value)
    if (removed) {
      updatePersistentValue()
    }
    return removed
  }

  operator fun contains(value: String): Boolean {
    val i = values.indexOf(value)
    if (i > 0) {
      values.removeAt(i)
      values.add(0, value)
    }
    return i >= 0
  }

  private fun updatePersistentValue() {
    properties.setList(persistenceKey, values)
  }

  override fun toString(): String {
    return values.toString()
  }
}