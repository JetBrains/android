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
import com.android.tools.idea.bleak.HeapGraph
import com.android.tools.idea.bleak.expander.Expander.Label
import java.lang.reflect.Field

typealias Node = HeapGraph.Node

/**
 * [Expander]s enable the HeapGraph to provide a level of abstraction over the raw object reference
 * graph.
 *
 * When [HeapGraph]s are constructed, only their roots are specified. Expanding a [Node] is the act
 * of determining the objects it references, and creating child Nodes and labeled [Edge]s for them.
 * The whole graph may be expanded by [HeapGraph.expandWholeGraph], which expands Nodes in breadth-
 * first order.
 *
 * On intermediate iterations of a test run with BLeak, however, most of the graph is irrelevant:
 * only the paths from roots to relatively few objects are of interest.
 * [Expander.expandCorrespondingEdge] can be used to take an Edge from an existing graph and
 * expand just the "corresponding" edge in this graph. Typically implemented with the aid of data
 * stored in the Edge's [Label], the nature of this correspondence is one of the key defining
 * features of an Expander. [getChildForLabel] finds the referent of an outgoing edge whose label
 * matches the one provided. Subclasses should ensure that if n1 = expandCorrespondingEdge(n, e),
 * then getChildForLabel(n, e.label) == n1 to avoid inconsistency.
 *
 */
abstract class Expander: DoNotTrace {
  abstract inner class Label {
    abstract fun signature(): String
  }
  // it is convenient for Nodes to always have a non-null incomingEdge. To accomplish this, roots
  // have an edge that points back to themselves. RootLoopbackLabel is the label type for such edges.
  inner class RootLoopbackLabel: Label() {
    override fun signature() = "root"
  }
  inner class ObjectLabel(val obj: Any): Label() {
    override fun signature() = "?"
    override fun equals(other: Any?) = other is ObjectLabel && obj === other.obj
    override fun hashCode(): Int = System.identityHashCode(obj)
  }

  inner class FieldLabel(val field: Field): Label() {
    override fun signature() = field.name
    override fun equals(other: Any?) = other is FieldLabel && field == other.field
    override fun hashCode(): Int = field.hashCode()
  }

  abstract fun canExpand(obj: Any): Boolean
  abstract fun expand(n: Node)  // should use n.addEdgeTo() to add edges to the node
  open fun expandCorrespondingEdge(n: Node, e: Edge): Node? = n[e] ?: n.addEdgeTo(e.end.obj, e.label)

  // this determines the initial value for whether a Node is growing (its corresponding Expander will
  // be queried during the first HeapGraph expansion). Returning false will prevent the node from
  // being considered a leak root.
  open fun canPotentiallyGrowIndefinitely(n: Node) = false

  // subclasses are encouraged to override this method to improve lookup performance, e.g, an
  // index-based array expander should just look at the i'th child.
  open fun getChildForLabel(n: Node, label: Label): Node? = n.edges.find { it.label == label }?.end
}

/** When a Node is about to be expanded, an Expander must be chosen. This decision is based on the
 * object the Node represents. The Expanders in the list are queried in turn; the first to declare
 * that it can expand the object is selected. Thus more specific Expanders should be placed at the
 * head of the list.
 */
class ExpanderChooser(private val expanders: List<Expander>) {
  fun expanderFor(obj: Any): Expander {
    for (e in expanders) {
      if (e.canExpand(obj)) {
        return e
      }
    }
    throw IllegalStateException("No matching Expander for object of class ${obj.javaClass.name} ($obj)")
  }
}
