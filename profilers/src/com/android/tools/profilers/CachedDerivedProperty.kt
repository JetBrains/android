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
package com.android.tools.profilers

import kotlin.reflect.KProperty

/**
 * Delegation for derived properties based on `source` that are expensive to compute.
 * It only recomputes if `source` has changed since last time.
 *
 * The implementer of this property has the option to look at the last computation
 * to incrementally derive the new one instead of computing from scratch.
 *
 * `derive` is supposed to be a pure function not dependent on any outside state,
 * otherwise the cache is unsound.
 *
 * Example usage:
 * ```
 *     val obj = object {
 *         var n: Int = 0
 *         val sumUpToN: Int by CachedDerivedProperty(::n, ::sumUpTo)
 *
 *         fun sumUpTo(n: Int) = (1 .. n).sum()
 *     }
 *     obj.n = 100 // `sumUpToN` not updated
 *     obj.n = 200 // `sumUpToN` not updated
 *     obj.sumUptoN // `sumUpToN` updated
 * ```
 */
class CachedDerivedProperty<K, V: Any>(private val getSourceProperty: () -> K,
                                       private val getDerivedProperty: (K, Pair<K, V>?) -> V) {
  constructor(getSourceProperty: () -> K, getDerivedProperty: (K) -> V)
    : this(getSourceProperty, { k, _ -> getDerivedProperty(k) })

  private var lastKey: K? = null
  private var lastVal: V? = null

  operator fun getValue(thisRef: Any?, property: KProperty<*>): V {
    val key = getSourceProperty()
    return when {
      key != lastKey -> {
        val memo = lastKey?.let { lastKey -> lastVal?.let { lastVal -> lastKey to lastVal }}
        getDerivedProperty(key, memo).also {
          lastKey = key
          lastVal = it
        }
      }
      else -> lastVal!!
    }
  }
}