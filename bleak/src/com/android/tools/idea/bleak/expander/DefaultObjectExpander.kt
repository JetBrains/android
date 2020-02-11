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

import com.android.tools.idea.bleak.DoNotTrace
import com.android.tools.idea.bleak.Edge
import com.android.tools.idea.bleak.ReflectionUtil
import java.lang.ref.Reference
import java.lang.reflect.Field
import java.lang.reflect.Modifier

/** [DefaultObjectExpander] is used for most objects. It reflectively inspects the object and
 * creates a child for every "nontrivial" field of the object (i.e., those that are not null,
 * primitive, primitive arrays, Strings, or [Class]es) and labels it with a [FieldLabel]
 * which records the [Field] used to access it.
 *
 * The use of [FieldLabel]s here means that if a field of an object is assigned a new value between
 * iterations, paths in the previous grpah that include that edge can have valid corresponding
 * paths in the new graph. This is especially important for resizing data structures, like
 * ArrayList: the reference from the list to its backing array is identified by the same FieldLabel
 * throughout its lifetime, even though its referent can be replaced with a different object (when
 * the list needs to grow).
 */
open class DefaultObjectExpander(val shouldOmitEdge: (Any, Field, Any) -> Boolean =
  { receiver, field, value -> value is DoNotTrace // avoid expanding BLeak internals
                              || receiver.javaClass.name == "com.intellij.openapi.util.objectTree.ObjectNode" && field.name == "myParent"
                              || (!FOLLOW_WEAK_REFS && receiver is Reference<*> && field.name in listOf("referent", "discovered")) // only expand Weak/Soft refs if that's enabled
  }): Expander() {

  override fun canExpand(obj: Any) = true

  override fun expand(n: Node) {
    for (field in ReflectionUtil.getAllFields(
      n.type).filter { it.modifiers and Modifier.STATIC == 0 }) {
      val value = field.get(n.obj)
      if (value != null && !shouldOmitEdge(n.obj, field, value)) {
        n.addEdgeTo(value, FieldLabel(field))
      }
    }
  }

  override fun expandCorrespondingEdge(n: Node, e: Edge): Node? {
    if (e.label is FieldLabel && e.label.field.declaringClass.isAssignableFrom(n.type)) {
      val obj = e.label.field.get(n.obj)
      if (obj != null) return n.addEdgeTo(obj, e.label)
    }
    return null
  }

  companion object {
    private val FOLLOW_WEAK_REFS = System.getProperty("bleak.follow.weak.refs") == "true"
  }
}