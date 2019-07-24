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

import com.android.tools.idea.tests.gui.framework.heapassertions.bleak.Edge
import com.android.tools.idea.tests.gui.framework.heapassertions.bleak.HeapGraph
import com.intellij.util.ref.DebugReflectionUtil
import java.lang.reflect.Modifier

/** [ClassStaticsExpander] takes a Class object and generates children for all of its static fields,
 * using [FieldLabel]s for the edge labels.
 */
class ClassStaticsExpander(g: HeapGraph): DefaultObjectExpander(g, { _, _, _ -> false}) {
  override fun canExpand(obj: Any) = obj is Class<*>
  override fun expand(n: Node) {
    if (DebugReflectionUtil.isInitialized(n.obj as Class<*>)) {
      for (field in DebugReflectionUtil.getAllFields(n.obj)) {
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
    // for the instance fields of the Class object. Note the classLoader field is not exposed via reflection.
    return super.expandCorrespondingEdge(n, e)
  }

  override fun getChildForLabel(n: Node, label: Label): Node? {
    if (label is FieldLabel && (label.field.modifiers and Modifier.STATIC) != 0) {
      return n.getNode(label.field.get(null))
    }
    // for the instance fields of the Class object. Note the classLoader field is not exposed via reflection.
    return super.getChildForLabel(n, label)
  }

}