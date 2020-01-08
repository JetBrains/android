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

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.android.tools.idea.layoutinspector.common.StringTable
import com.android.tools.layoutinspector.proto.LayoutInspectorProto
import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import com.intellij.util.text.nullize

class TestStringTable : StringTable {
  private val strings: BiMap<String, Int> = HashBiMap.create()

  fun add(value: String?): Int =
    value.nullize()?.let {
      strings.getOrPut(it) {
        strings.size + 1
      }
    } ?: 0

  fun add(value: ResourceReference?): LayoutInspectorProto.Resource? =
    value?.let {
      LayoutInspectorProto.Resource.newBuilder().apply {
        namespace = add(it.namespace.packageName)
        name = add(it.name)
        type = add(it.resourceType.getName())
      }.build()
    }

  fun asEntryList(): List<LayoutInspectorProto.StringEntry> =
    strings.entries
      .map { LayoutInspectorProto.StringEntry.newBuilder().apply { id = it.value; str = it.key }.build() }

  override operator fun get(id: Int): String =
    strings.inverse()[id] ?: ""

  override fun get(resource: LayoutInspectorProto.Resource?): ResourceReference? {
    if (resource == null) {
      return null
    }
    val type = get(resource.type)
    val namespace = get(resource.namespace)
    val name = get(resource.name)
    val resNamespace = ResourceNamespace.fromPackageName(namespace)
    val resType = ResourceType.fromFolderName(type) ?: return null
    return ResourceReference(resNamespace, resType, name)
  }
}
