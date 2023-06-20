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
package com.android.tools.idea.bleak.expander

import com.android.tools.idea.bleak.BleakHelper
import java.util.Vector

object BootstrapClassloaderPlaceholder

/** Expands a ClassLoader, with an edge to each class it defines. The bootstrap ClassLoader is represented by
 * null in [Class.getClassLoader], but every Node must correspond to a non-null object.
 * [BootstrapClassloaderPlaceholder] serves as a placeholder for the bootstrap class loader for this purpose.
 */
class ClassLoaderExpander(val bleakHelper: BleakHelper): Expander() {
  private val labelToNodeMap: MutableMap<Node, MutableMap<Label, Node>> = mutableMapOf()

  override fun canExpand(obj: Any): Boolean = obj is ClassLoader || obj === BootstrapClassloaderPlaceholder

  override fun canPotentiallyGrowIndefinitely(n: Node) = true

  override fun expand(n: Node) {
    val map = mutableMapOf<Label, Node>()
    labelToNodeMap[n] = map
    val cl = if (n.obj === BootstrapClassloaderPlaceholder) null else n.obj as ClassLoader
    bleakHelper.classesFor(cl)?.filterNot { it.isArray }?.forEach {
      val label = ObjectLabel(it)
      val childNode = n.addEdgeTo(it, label)
      if (childNode != null) map[label] = childNode
    }
  }

  override fun getChildForLabel(n: Node, label: Label): Node? {
    return labelToNodeMap[n]?.get(label) ?: super.getChildForLabel(n, label)
  }

}