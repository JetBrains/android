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
package com.android.tools.idea.layoutinspector.util

import com.android.ide.common.rendering.api.ResourceReference
import com.android.tools.idea.layoutinspector.common.StringTable
import com.android.tools.idea.layoutinspector.resource.data.Resource
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol
import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import com.intellij.util.text.nullize

class TestStringTable : StringTable {
  private val strings: BiMap<String, Int> = HashBiMap.create()
  override val keys: Set<Int> = strings.values

  fun add(value: String?): Int =
    value.nullize()?.let { strings.getOrPut(it) { strings.size + 1 } } ?: 0

  fun add(value: ResourceReference?): Resource? =
    value?.let {
      Resource(
        type = add(it.resourceType.getName()),
        namespace = add(it.namespace.packageName),
        name = add(it.name),
      )
    }

  fun asEntryList(): List<LayoutInspectorViewProtocol.StringEntry> =
    strings.entries.map {
      LayoutInspectorViewProtocol.StringEntry.newBuilder()
        .apply {
          id = it.value
          str = it.key
        }
        .build()
    }

  override operator fun get(id: Int): String = strings.inverse()[id].orEmpty()

  operator fun get(resource: Resource?): ResourceReference? {
    return resource?.createReference(this)
  }
}
