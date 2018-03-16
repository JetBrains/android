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
import com.android.tools.adtui.ptable.PTableItem
import com.android.tools.idea.editors.layoutInspector.DefaultNoEditHandler
import com.android.tools.idea.editors.layoutInspector.EditHandler

import java.util.Collections

class LITableItem(private val myProp: ViewProperty, parent: PTableItem, private val myEditHandler: EditHandler) : PTableItem(),
  Comparable<PTableItem> {
  private val myValue: String = myProp.value
  private val myName: String =
    if (myProp.fullName.startsWith(parent.name)) myProp.fullName.substring(parent.name.length + 1) else myProp.fullName

  init {
    setParent(parent)
  }

  override fun getName(): String {
    return myName
  }

  override fun getValue(): String? {
    return myValue
  }

  override fun setValue(value: Any?) {
    val strValue = value as String?
    myEditHandler.editProperty(myProp, strValue!!)
  }

  override fun isEditable(col: Int): Boolean {
    return myEditHandler.isEditable(myName) && col == VALUE_COLUMN
  }

  override fun isDefaultValue(value: String?): Boolean {
    return true
  }

  override fun compareTo(other: PTableItem): Int {
    return myName.compareTo(other.name)
  }

  companion object {
    val EMPTY: PTableItem = LITableGroupItem("", emptyList(), DefaultNoEditHandler())
    const val VALUE_COLUMN = 1
  }
}
