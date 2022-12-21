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
package com.android.tools.componenttree.util

import java.awt.datatransfer.Transferable
import javax.swing.Icon

class Item(
  val tagName: String,
  val id: String? = null,
  val textValue: String? = null,
  var treeIcon: Icon? = null,
  var parent: Item? = null
) {
  val children = mutableListOf<Any>()
  var column1: Int = tagName.hashCode().rem(5)
  var column2: Int = tagName.hashCode().rem(6)
  var badge1: Icon? = null
  var badge2: Icon? = null
  var badge3: Icon? = null
  var hover3: Icon? = null
  var canInsert: Boolean? = null
  var acceptInsert = true
  var enabled = true
  var deEmphasized = false
  val insertions = mutableListOf<Insertion>()

  init {
    parent?.children?.add(this)
  }

  fun add(vararg extraChildren: Item) {
    children.addAll(extraChildren)
    extraChildren.forEach { it.parent = this }
  }

  override fun toString() = "$tagName ${id.orEmpty()}"

  data class Insertion(val data: Transferable, val before: Any?, val isMove: Boolean, val draggedFromTree: List<Any>)
}
