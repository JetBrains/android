/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.properties

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.ATTR_NAME
import com.android.tools.property.panel.api.PropertiesTable
import com.android.tools.idea.layoutinspector.model.InspectorView
import com.google.common.collect.HashBasedTable
import com.google.common.collect.Table

class PropertiesProvider(private val model: InspectorPropertiesModel) {

  fun getProperties(view: InspectorView?): PropertiesTable<InspectorPropertyItem> {
    if (view == null) {
      return PropertiesTable.emptyTable()
    }
    val generator = Generator(view, model)
    return PropertiesTable.create(generator.generate())
  }

  private class Generator(private val view: InspectorView, private val model: InspectorPropertiesModel) {
    private val table = HashBasedTable.create<String, String, InspectorPropertyItem>()

    fun generate(): Table<String, String, InspectorPropertyItem> {
      add(ATTR_NAME, view.type)
      addDimension(ATTR_LAYOUT_WIDTH, view.width)
      addDimension(ATTR_LAYOUT_HEIGHT, view.height)
      return table
    }

    private fun add(name: String, value: String?) {
      add(InspectorPropertyItem(ANDROID_URI, name, value, view, model))
    }

    private fun addDimension(name: String, value: Int) {
      add(name, "${value}px")
    }

    private fun add(item: InspectorPropertyItem) {
      table.put(item.namespace, item.name, item)
    }
  }
}
