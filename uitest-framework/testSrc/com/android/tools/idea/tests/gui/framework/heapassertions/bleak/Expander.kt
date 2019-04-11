/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.heapassertions.bleak

import com.intellij.util.ref.DebugReflectionUtil
import gnu.trove.TObjectHash
import java.lang.ref.Reference
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.ConcurrentModificationException
import java.util.Vector

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
abstract class Expander(val g: HeapGraph): DoNotTrace {
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

  abstract fun canExpand(obj: Any): Boolean
  abstract fun expand(n: Node)  // should use n.addEdgeTo() to add edges to the node
  open fun expandCorrespondingEdge(n: Node, e: Edge): Node? = n[e] ?: n.addEdgeTo(e.end.obj, e.label)

  // subclasses are encouraged to override this method to improve lookup performance, e.g, an
  // index-based array expander should just look at the i'th child.
  open fun getChildForLabel(n: Node, label: Label): Node? = n.edges.find { it.label == label }?.end
}

// currently experimental
class CollectionExpander(g: HeapGraph): Expander(g) {
  override fun canExpand(obj: Any): Boolean {
    if (obj is Collection<*>) {
      try {
        obj.iterator()
        return true
      } catch (e: UnsupportedOperationException) {
        println("iterator() not supported for class ${obj.javaClass.name}")
      }
    }
    return false
  }

  override fun expand(n: Node) {
    try {
      (n.obj as Collection<*>).forEach { if (it != null) n.addEdgeTo(it, ObjectLabel(it)) }
    } catch (e: ConcurrentModificationException) {
      println("Concurrent modification on ${n.type}: ${n.obj.toString().take(120)}; path to root is: ")
      println(n.getPath().verboseSignature())
    }
  }

  override fun expandCorrespondingEdge(n: Node, e: Edge): Node? {
    return if ((n.obj as Collection<*>).contains(e.end.obj)) n.addEdgeTo(e.end.obj, ObjectLabel(e.end.obj)) else null
  }
}

/** [ArrayObjectIdentityExpander] expands arrays, creating a child node for each non-null element,
 * with an [ObjectLabel] as the edge label. This means objects in arrays can be tracked regardless
 * of their position in the array, which is particularly important for array-backed data structures
 * like ArrayList and maps where elements move around (e.g. as the result of inserting an element
 * in a list, or rehashing a HashMap (though the possibility of the creation of TreeNodes for
 * overly full buckets throws a wrench in things - CollectionExpander might be more appropriate)).
 *
 * For large arrays, the default implementation of getChildForLabel is slow (linear in the size of
 * the array). For arrays larger than LABEL_MAP_DEGREE_THRESHOLD, a map from labels back to nodes
 * is maintained to accelerate lookups. For small arrays, the extra memory usage is not worth the
 * small (or possibly negative) performance improvement.
 */
class ArrayObjectIdentityExpander(g: HeapGraph): Expander(g) {
  private val labelToNodeMap: MutableMap<Node, MutableMap<Label, Node>> = mutableMapOf()

  override fun canExpand(obj: Any): Boolean = obj.javaClass.isArray && !obj.javaClass.componentType.isPrimitive
  override fun expand(n: Node) {
    val arr = n.obj as Array<*>
    val map = if (arr.size > LABEL_MAP_DEGREE_THRESHOLD) {
      labelToNodeMap[n] = mutableMapOf()
      labelToNodeMap[n]
    } else null
    for (obj in arr) {
      if (obj != null && obj !== TObjectHash.REMOVED && (TRACK_WEAK_REFS_IN_ARRAYS || obj !is WeakReference<*>)) {
        val label = ObjectLabel(obj)
        val childNode = n.addEdgeTo(obj, label)
        if (map != null && map[label] == null) {
          map[label] = childNode
        }
      }
    }
  }

  override fun expandCorrespondingEdge(n: Node, e: Edge): Node? {
    return if ((n.obj as Array<*>).any { it === e.end.obj }) n.addEdgeTo(e.end.obj, ObjectLabel(e.end.obj)) else null
  }

  override fun getChildForLabel(n: Node, label: Label): Node? = labelToNodeMap[n]?.get(label) ?: super.getChildForLabel(n, label)

  companion object {
    private const val LABEL_MAP_DEGREE_THRESHOLD = 50
    private val TRACK_WEAK_REFS_IN_ARRAYS = System.getProperty("bleak.track.weak.refs.in.arrays") == "true"
  }
}

@Deprecated("You probably want ArrayObjectIdentityExpander")
class ArrayIndexExpander(g: HeapGraph): Expander(g) {
  inner class IndexLabel(val index: Int): Label() {
    override fun signature() = index.toString()
    override fun equals(other: Any?) = other is IndexLabel && index == other.index
    override fun hashCode(): Int = index.hashCode()
  }

  override fun canExpand(obj: Any): Boolean = obj.javaClass.isArray && !obj.javaClass.componentType.isPrimitive

  override fun expand(n: Node) {
    (n.obj as Array<*>).forEachIndexed { i, element -> if (element != null) n.addEdgeTo(element, IndexLabel(i)) }
  }

  override fun expandCorrespondingEdge(n: Node, e: Edge): Node? {
    if (e.label is IndexLabel) {
      val i = e.label.index
      if (i >= 0 && i < (n.obj as Array<*>).size) {
        val elem = n.obj[i]
        if (elem != null) {
          return n.addEdgeTo(elem, e.label)
        }
      }
    }
    return null
  }

  // this is broken in the incremental case
  override fun getChildForLabel(n: Node, label: Label): Node? =
    if (label is IndexLabel && label.index >= 0 && label.index < n.degree) n.edges[label.index].end else null
}

/** [ClassStaticsExpander] takes a Class object and generates children for all of its static fields,
 * using [FieldLabel]s for the edge labels.
 */
class ClassStaticsExpander(g: HeapGraph): DefaultObjectExpander(g, { _,_,_ -> false}) {
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
open class DefaultObjectExpander(g: HeapGraph, val shouldOmitEdge: (Any, Field, Any) -> Boolean =
  { receiver, field, value -> value is DoNotTrace // avoid expanding BLeak internals
                    || field.type.isArray && field.type.componentType.isPrimitive // don't expand primitve arrays
                    || receiver.javaClass.name == "com.intellij.openapi.util.objectTree.ObjectNode" && field.name == "myParent"
                    || (!FOLLOW_WEAK_REFS && receiver is Reference<*> && field.name in listOf("referent", "discovered")) // only expand Weak/Soft refs if that's enabled
  }): Expander(g) {

  inner class FieldLabel(val field: Field): Label() {
    override fun signature() = field.name
    override fun equals(other: Any?) = other is FieldLabel && field == other.field
    override fun hashCode(): Int = field.hashCode()
  }

  override fun canExpand(obj: Any) = true

  override fun expand(n: Node) {
    for (field in DebugReflectionUtil.getAllFields(n.type)) {
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

object BootstrapClassloaderPlaceholder

/** Expands a ClassLoader, with an edge to each class it defines. The bootstrap ClassLoader is represented by
 * null in [Class.getClassLoader], but every Node must correspond to a non-null object.
 * [BootstrapClassloaderPlaceholder] serves as a placeholder for the bootstrap class loader for this purpose.
 */
class ClassLoaderExpander(g: HeapGraph, val bleakHelper: BleakHelper): Expander(g) {
  private val labelToNodeMap: MutableMap<Node, MutableMap<Label, Node>> = mutableMapOf()

  override fun canExpand(obj: Any): Boolean = obj is ClassLoader || obj === BootstrapClassloaderPlaceholder

  override fun expand(n: Node) {
    val map = mutableMapOf<Label, Node>()
    labelToNodeMap[n] = map
    if (n.obj === BootstrapClassloaderPlaceholder) {
      bleakHelper.allLoadedClasses().filter{ (it as Class<*>).classLoader == null }.forEach {
        val label = ObjectLabel(it)
        val childNode = n.addEdgeTo(it, label)
        map[label] = childNode
      }
    } else {
      val cl = n.obj as ClassLoader
      val classesField = ClassLoader::class.java.getDeclaredField("classes")
      classesField.isAccessible = true
      val classes = classesField.get(cl) as Vector<Class<*>>
      for (c in classes.filterNot { it.isArray }) {
        val label = ObjectLabel(c)
        val childNode = n.addEdgeTo(c, label)
        map[label] = childNode
      }
    }
  }

  override fun getChildForLabel(n: Node, label: Label): Node? {
    return labelToNodeMap[n]?.get(label) ?: super.getChildForLabel(n, label)
  }

}

/** Expands the synthetic root node, whose children are the ClassLoader instances. This enables tracking growth
 * in the number of ClassLoaders just like any other leak. This fake root node doesn't really correspond to any
 * object, but since one must be provided, we use an instance of [BleakHelper], as it is responsible for determining
 * the loaded classes.
 */
class RootExpander(g: HeapGraph): Expander(g) {
  override fun canExpand(obj: Any): Boolean = obj is BleakHelper

  override fun expand(n: Node) {
    val classes = (n.obj as BleakHelper).allLoadedClasses() as List<Class<*>>
    val classLoaders = classes.map{ it.classLoader }.filterNotNull().toSet() // the bootstrap class loader is represented by null
    classLoaders.forEach {
      n.addEdgeTo(it, ObjectLabel(it))
    }
    n.addEdgeTo(BootstrapClassloaderPlaceholder, ObjectLabel(BootstrapClassloaderPlaceholder))
  }

}

/** When a Node is about to be expanded, an Expander must be chosen. This decision is based on the
 * object the Node represents. The Expanders in the list are queried in turn; the first to declare
 * that it can expand the object is selected. Thus more specific Expanders should be placed at the
 * head of the list.
 */
class ExpanderChooser(val expanders: List<Expander>) {
  fun expanderFor(obj: Any): Expander {
    for (e in expanders) {
      if (e.canExpand(obj)) {
        return e
      }
    }
    throw IllegalStateException("No matching Expander for object of class ${obj.javaClass.name} ($obj)")
  }
}
