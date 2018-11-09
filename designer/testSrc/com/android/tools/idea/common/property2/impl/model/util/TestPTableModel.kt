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

import com.android.tools.adtui.ptable2.PTableGroupItem
import com.android.tools.adtui.ptable2.PTableItem
import com.android.tools.adtui.ptable2.PTableModel

class TestPTableModel(expanded: Boolean,
                      values: Map<String, String>,
                      groups: List<PTableGroupItem>): PTableModel {
  override val items = mutableListOf<PTableItem>()
  var refreshCalled = false

  init {
    items.addAll(values.map { (name, value) -> TestTableItem(name, value) })
    if (!expanded) {
      items.addAll(groups)
    }
    else {
      groups.forEach { items.add(it); items.addAll(it.children) }
    }
  }

  override fun refresh() {
    refreshCalled = true
  }
}

class TestTableItem(override val name: String, override val value: String?) : PTableItem

class TestGroupItem(override val name: String, items: Map<String, String>): PTableGroupItem {
  override val value: String? = null
  override val children = items.map { (name, value) -> TestTableItem(name, value) }
}
