/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.property.panel.impl.support

import com.android.tools.property.panel.api.PropertiesTable
import com.android.tools.property.panel.api.PropertyItem
import com.google.common.collect.Table

/**
 * A [PropertiesTable] which uses a [Table] as a backing store.
 *
 * This API is readonly. No methods in [PropertiesTable] can change the content of the backing
 * [Table].
 */
class PropertiesTableImpl<P : PropertyItem>(private val table: Table<String, String, P>) :
  PropertiesTable<P> {

  override operator fun get(namespace: String, name: String): P {
    return table[namespace, name] ?: throw NoSuchElementException()
  }

  override fun getOrNull(namespace: String, name: String): P? {
    return table.get(namespace, name)
  }

  override fun put(property: P) {
    table.put(property.namespace, property.name, property)
  }

  override fun getByNamespace(namespace: String): Map<String, P> {
    return table.row(namespace)
  }

  override fun sameKeys(other: PropertiesTable<P>): Boolean {
    if (other !is PropertiesTableImpl) {
      return false
    }
    val namespaces = table.rowKeySet()
    if (namespaces != other.table.rowKeySet()) {
      return false
    }
    for (namespace in namespaces) {
      if (getByNamespace(namespace).keys != other.getByNamespace(namespace).keys) {
        return false
      }
    }
    return true
  }

  override fun copyValues(other: PropertiesTable<P>) {
    for (property in other.values) {
      getOrNull(property.namespace, property.name)?.value = property.value
    }
  }

  override val isEmpty: Boolean
    get() = table.isEmpty

  override val first: P?
    get() = table.values().firstOrNull()

  override val size: Int
    get() = table.size()

  override val values: Collection<P>
    get() = table.values()

  override val namespaces: Collection<String>
    get() = table.rowKeySet()
}
