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

/** Expands the synthetic root node, whose children are the ClassLoader instances. This enables tracking growth
 * in the number of ClassLoaders just like any other leak. This fake root node doesn't really correspond to any
 * object, but since one must be provided, we use an instance of [BleakHelper], as it is responsible for determining
 * the loaded classes.
 */
class RootExpander: Expander() {
  override fun canExpand(obj: Any): Boolean = obj is BleakHelper

  override fun canPotentiallyGrowIndefinitely(n: Node) = true

  override fun expand(n: Node) {
    val classLoaders = (n.obj as BleakHelper).allClassLoaders().filterNotNull()
    classLoaders.filterNot { it.javaClass.name == "jdk.internal.reflect.DelegatingClassLoader" }.forEach {
      n.addEdgeTo(it, ObjectLabel(it))
    }
    n.addEdgeTo(BootstrapClassloaderPlaceholder, ObjectLabel(
      BootstrapClassloaderPlaceholder))
  }

}