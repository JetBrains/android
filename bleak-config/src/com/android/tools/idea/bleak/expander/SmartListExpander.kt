/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.intellij.util.SmartList

/** [SmartList] is a list optimized for holding 0 or 1 element. It has an Object field 'elem' which either
 * contains the single element of the list, or an array of elements if the list is larger. Without the special
 * handling provided by this class, BLeak would not be able to follow the expansion of the list across the
 * boundary from one to multiple elements.
 */
class SmartListExpander: Expander() {
  override fun canExpand(obj: Any) = obj is SmartList<*>

  override fun canPotentiallyGrowIndefinitely(n: Node) = true

  override fun expand(n: Node) {
    val elem = myElemField.get(n.obj)
    val size = mySizeField.get(n.obj) as Int
    if (size == 1) {
      n.addEdgeTo(elem, ObjectLabel(elem))
    } else if (size > 1) {
      for (e in elem as Array<Any?>) {
        if (e != null) {
          n.addEdgeTo(e, ObjectLabel(e))
        }
      }
    }
  }

  companion object {
    val myElemField = SmartList::class.java.getDeclaredField("myElem")
    val mySizeField = SmartList::class.java.getDeclaredField("mySize")

    init {
      myElemField.isAccessible = true
      mySizeField.isAccessible = true
    }
  }
}