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
package org.jetbrains.android.uipreview

import com.intellij.util.containers.MultiMap
import java.util.Collections
import java.util.WeakHashMap

class WeakMultiMap<K, V> private constructor(private val useWeakValues: Boolean): MultiMap<K, V>(WeakHashMap<K, Collection<V>>()) {
  override fun createCollection(): MutableCollection<V> = if (useWeakValues) {
    Collections.newSetFromMap(WeakHashMap())
  } else {
    mutableSetOf()
  }

  override fun createEmptyCollection(): Collection<V> = setOf()

  companion object {
    fun <K, V> create(): WeakMultiMap<K, V> = WeakMultiMap(false)
    fun <K, V> createWithWeakValues(): WeakMultiMap<K, V> = WeakMultiMap(true)
  }
}