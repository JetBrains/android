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
package com.android.tools.idea.editors.layoutInspector.ptable

import com.android.layoutinspector.model.ViewProperty
import com.android.tools.adtui.ptable.PTableGroupItem
import com.android.tools.adtui.ptable.PTableItem
import com.android.tools.idea.editors.layoutInspector.EditHandler
import java.util.stream.Collectors

class LITableGroupItem(private val myName: String, properties: List<ViewProperty>, editHandler: EditHandler) : PTableGroupItem() {
  private val myChildren: List<PTableItem>
  private var isExpanded: Boolean = false

  init {
    myChildren = properties.map { prop -> LITableItem(prop, this, editHandler) }.sorted()
    isExpanded = false
  }

  override fun getName(): String {
    return myName
  }

  override fun getValue(): String? {
    return null
  }

  override fun setValue(value: Any?) {}

  override fun hasChildren(): Boolean {
    return true
  }

  override fun getChildren(): List<PTableItem> {
    return myChildren
  }

  override fun getChildLabel(item: PTableItem): String {
    return item.name
  }

  override fun isExpanded(): Boolean {
    return isExpanded
  }

  override fun setExpanded(expanded: Boolean) {
    isExpanded = expanded
  }
}
