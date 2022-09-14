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
class ClassLoaderExpander(private val bleakHelper: BleakHelper): Expander() {
  private val labelToNodeMap: MutableMap<Node, MutableMap<Label, Node>> = mutableMapOf()

  override fun canExpand(obj: Any): Boolean = obj is ClassLoader || obj === BootstrapClassloaderPlaceholder

  override fun canPotentiallyGrowIndefinitely(n: Node) = true

  override fun expand(n: Node) {
    val map = mutableMapOf<Label, Node>()
    labelToNodeMap[n] = map
    if (n.obj === BootstrapClassloaderPlaceholder) {
      bleakHelper.allLoadedClasses().filter{ (it as Class<*>).classLoader == null }.forEach {
        val label = ObjectLabel(it)
        val childNode = n.addEdgeTo(it, label)
        if (childNode != null) map[label] = childNode
      }
    } else {
      val cl = n.obj as ClassLoader
      val classesField = ClassLoader::class.java.getDeclaredField("classes")
      classesField.isAccessible = true
      val classes = (classesField.get(cl) as Vector<Class<*>>).toTypedArray<Class<*>>()
      for (c in classes.filterNot { it.isArray }) {
        val label = ObjectLabel(c)
        val childNode = n.addEdgeTo(c, label)
        if (childNode != null) map[label] = childNode
      }
    }
  }

  override fun getChildForLabel(n: Node, label: Label): Node? {
    return labelToNodeMap[n]?.get(label) ?: super.getChildForLabel(n, label)
  }

}