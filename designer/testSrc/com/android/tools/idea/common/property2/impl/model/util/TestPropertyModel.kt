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
package com.android.tools.idea.common.property2.impl.model.util

import com.android.tools.idea.common.property2.api.PropertiesModel
import com.android.tools.idea.common.property2.api.PropertiesModelListener
import com.android.tools.idea.common.property2.impl.support.PropertiesTableImpl
import com.google.common.collect.HashBasedTable

class TestPropertyModel : PropertiesModel<TestPropertyItem> {

  private val listeners = mutableListOf<PropertiesModelListener<TestPropertyItem>>()

  val table = HashBasedTable.create<String, String, TestPropertyItem>()!!

  override val properties = PropertiesTableImpl<TestPropertyItem>(table)

  fun add(property: TestPropertyItem) {
    table.put(property.namespace, property.name, property)
  }

  override fun deactivate() {
  }

  override fun addListener(listener: PropertiesModelListener<TestPropertyItem>) {
    listeners.add(listener)
  }

  override fun removeListener(listener: PropertiesModelListener<TestPropertyItem>) {
    listeners.remove(listener)
  }

  fun propertiesGenerated() {
    listeners.forEach { it.propertiesGenerated(this) }
  }
}
