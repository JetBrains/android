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

import com.android.tools.componenttree.api.ViewNodeType

class ItemNodeType : ViewNodeType<Item>() {
  override val clazz = Item::class.java

  override fun tagNameOf(node: Item) = node.tagName

  override fun idOf(node: Item) = node.id

  override fun textValueOf(node: Item) = node.textValue

  override fun iconOf(node: Item) = node.treeIcon

  override fun parentOf(node: Item) = node.parent

  override fun childrenOf(node: Item) = node.children

  override fun isEnabled(node: Item) = true
}
