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

import java.lang.ref.SoftReference

/**
 * Map that's strong in key and soft in value
 */
class SoftHashMap<K: Any, V: Any>: MutableMap<K, V> {
  private val content = hashMapOf<K, SoftReference<V>>()
  override val entries get() = content.mapNotNullTo(hashSetOf()) { (k, vRef) -> vRef.get()?.let { v -> entry(k, v) } }
  override val keys get() = content.keys
  override val size get() = content.size
  override val values get() = content.values.mapNotNullTo(hashSetOf(), SoftReference<V>::get)
  override fun clear() = content.clear()
  override fun isEmpty() = content.isEmpty()
  override fun remove(key: K): V? = content.remove(key)?.get()
  override fun putAll(from: Map<out K, V>) = from.forEach { (k, v) -> content[k] = SoftReference(v) }
  override fun put(key: K, value: V): V? = content.put(key, SoftReference(value))?.get()
  override fun get(key: K): V? = content[key]?.get()
  override fun containsValue(value: V) = content.values.any { it.get() == value }
  override fun containsKey(key: K) = key in content

  companion object {
    fun<K, V> entry(k: K, v: V) = object: MutableMap.MutableEntry<K, V> {
      override var value = v
      override val key get() = k
      override fun setValue(newValue: V): V = value.also {
        value = newValue
      }
    }
  }
}