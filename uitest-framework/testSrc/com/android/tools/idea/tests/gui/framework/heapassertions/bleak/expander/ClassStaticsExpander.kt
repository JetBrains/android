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
package com.android.tools.idea.tests.gui.framework.heapassertions.bleak.expander

import com.android.tools.idea.tests.gui.framework.heapassertions.bleak.DoNotTrace
import com.android.tools.idea.tests.gui.framework.heapassertions.bleak.Edge
import com.android.tools.idea.tests.gui.framework.heapassertions.bleak.ReflectionUtil
import sun.misc.Unsafe
import java.lang.reflect.Modifier

/** [ClassStaticsExpander] takes a Class object and generates children for all of its static fields,
 * using [FieldLabel]s for the edge labels.
 */
class ClassStaticsExpander: Expander() {
  override fun canExpand(obj: Any) = obj is Class<*>
  override fun expand(n: Node) {
    if ((n.obj as Class<*>).isInitialized() && !DoNotTrace::class.java.isAssignableFrom(n.obj)) {
      for (field in ReflectionUtil.getAllFields(n.obj)) {
        if ((field.modifiers and Modifier.STATIC) != 0) {
          val value = field.get(null)
          if (value != null) {
            n.addEdgeTo(value, FieldLabel(field))
          }
        }
      }
    }
  }

  override fun expandCorrespondingEdge(n: Node, e: Edge): Node? {
    if (e.label is FieldLabel && (e.label.field.modifiers and Modifier.STATIC) != 0) {
      val obj = e.label.field.get(null)
      if (obj != null) return n.addEdgeTo(obj, e.label)
    }
    return null
  }

  override fun getChildForLabel(n: Node, label: Label): Node? {
    if (label is FieldLabel && (label.field.modifiers and Modifier.STATIC) != 0) {
      return n.getNode(label.field.get(null))
    }
    return null
  }

  companion object {
    private val Unsafe_shouldBeInitialized = Unsafe::class.java.getDeclaredMethod("shouldBeInitialized", Class::class.java)
    private val unsafe: Unsafe

    init {
      Unsafe_shouldBeInitialized.isAccessible = true
      val theUnsafeField = Unsafe::class.java.getDeclaredField("theUnsafe")
      theUnsafeField.isAccessible = true
      unsafe = theUnsafeField.get(null) as Unsafe
    }

    private fun Class<*>.isInitialized(): Boolean {
      return !(Unsafe_shouldBeInitialized.invoke(unsafe, this) as Boolean)
    }
  }

}