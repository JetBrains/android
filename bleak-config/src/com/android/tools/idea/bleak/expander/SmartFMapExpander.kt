/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.bleak.expander

import com.intellij.util.SmartFMap

/** [SmartFMap] is a map optimized for holding a small number of items. It has an Object field 'myMap' which either
 * contains an Object[] containing keys and corresponding values in consecutive slots, or a Map once the size grows
 * past [SmartFMap.ARRAY_THRESHOLD].
 * Without the handling provided by this class, BLeak would not be able to follow the expansion of the map across
 * this boundary.
 */
class SmartFMapExpander(): Expander() {
  override fun canExpand(obj: Any) = obj is SmartFMap<*,*>

  override fun canPotentiallyGrowIndefinitely(n: Node) = true

  override fun expand(n: Node) {
    val map = myMapField.get(n.obj)
    when (map) {
      is Array<*> -> map.filterNotNull().forEach { n.addEdgeTo(it, ObjectLabel(it)) }
      is Map<*, *> -> map.entries.forEach {
        val key = it.key
        val value = it.value
        if (key != null) {
          n.addEdgeTo(key, ObjectLabel(key))
        }
        if (value != null) {
          n.addEdgeTo(value, ObjectLabel(value))
        }
      }
    }
  }

  companion object {
    val myMapField = SmartFMap::class.java.getDeclaredField("myMap")

    init {
      myMapField.isAccessible = true
    }
  }
}
