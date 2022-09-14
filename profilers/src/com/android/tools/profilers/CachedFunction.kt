/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.profilers

/**
 * This class implements cached functions that can be invalidated to recompute the next time they're called
 */
class CachedFunction<K,V>(private val cache: MutableMap<K,V>, private val compute: (K) -> V): (K) -> V {
  constructor(compute: (K) -> V): this(HashMap(), compute)

  override operator fun invoke(key: K): V = when (val cached = cache[key]) {
    null -> compute(key).also { cache[key] = it }
    else -> cached
  }

  fun invalidate() {
    cache.clear()
  }

  fun invalidate(key: K) {
    cache -= key
  }
}

class CappedLRUMap<K, V>(private val maxSize: Int): LinkedHashMap<K, V>(maxSize, .8f, true) {
  override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?) = size > maxSize
}