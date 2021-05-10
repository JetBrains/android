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
package com.android.tools.idea.tests.gui.layoutinspector

import com.android.ide.common.rendering.api.ResourceReference
import com.android.tools.idea.layoutinspector.common.StringTable
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.convert
import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import com.intellij.util.text.nullize
import layoutinspector.view.inspection.LayoutInspectorViewProtocol

/**
 * StringTable for use in ui tests.
 *
 * Used to build proto buffers that are received from the agent.
 * These buffers hold a separate string table. This table will help
 * gather all the strings needed for that string table in a layout
 * inspector event.
 */
class TestStringTable : StringTable {
  private val strings: BiMap<String, Int> = HashBiMap.create()
  override val keys: Set<Int> = strings.values

  fun add(value: String?): Int =
    value.nullize()?.let {
      strings.getOrPut(it) {
        strings.size + 1
      }
    } ?: 0

  fun add(value: ResourceReference?): LayoutInspectorViewProtocol.Resource? =
    value?.let {
      LayoutInspectorViewProtocol.Resource.newBuilder().apply {
        namespace = add(it.namespace.packageName)
        name = add(it.name)
        type = add(it.resourceType.getName())
      }.build()
    }

  fun asEntryList(): List<LayoutInspectorViewProtocol.StringEntry> =
    strings.entries
      .map { LayoutInspectorViewProtocol.StringEntry.newBuilder().apply { id = it.value; str = it.key }.build() }

  override operator fun get(id: Int): String = strings.inverse()[id].orEmpty()

  fun get(resource: LayoutInspectorViewProtocol.Resource?): ResourceReference? {
    return resource?.convert()?.createReference(this)
  }
}
