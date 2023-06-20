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
package com.android.tools.idea.bleak

import com.android.tools.idea.bleak.expander.BootstrapClassloaderPlaceholder
import com.android.tools.idea.bleak.expander.Expander
import com.android.tools.idea.bleak.expander.ExpanderChooser
import com.android.tools.idea.bleak.expander.Node
import java.lang.ref.Reference
import java.lang.ref.SoftReference
import java.lang.ref.WeakReference
import java.lang.reflect.Modifier
import java.time.Duration
import java.util.ArrayDeque
import java.util.IdentityHashMap
import kotlin.system.measureTimeMillis

// marker interface for BLeak internals, so we can avoid tracking our own objects (this would lead
// to memory consumption exponential in the iteration count, for no useful purpose)
interface DoNotTrace

typealias Node = HeapGraph.Node

/** [HeapGraph] represents a slightly-abstracted snapshot of the Java object reference graph.
 * Each node corresponds to a single object, and edges represent references, either real, or
 * abstracted. [Expander]s are responsible for defining the nature of this abstraction.
 */
class HeapGraph(private val expanderChooser: ExpanderChooser, private val forbiddenObjects: List<Any> = listOf()): DoNotTrace {

  private val objToNode: MutableMap<Any, Node> = IdentityHashMap()
  private val rootNodes: List<Node> = mutableListOf(Node(jniHelper, true))
  private val nodes: MutableCollection<Node>
    get() = objToNode.values
  val leakRoots: MutableList<Node> = mutableListOf()

  inner class Node(val obj: Any, val isRootNode: Boolean = false): DoNotTrace {
    val expander = expanderChooser.expanderFor(obj)
    val edges = mutableListOf<Edge>()
    val type: Class<*> = obj.javaClass
    var incomingEdge: Edge? = if (isRootNode) Edge(this, this, expander.RootLoopbackLabel()) else null
    val children: List<Node>
      get() = edges.map { it.end }
    val childObjects: List<Any>
      get() = edges.map { it.end.obj }
    val degree: Int
      get() = edges.size
    var mark = 0
    var growing = false
      private set
    private var approximateSize = -1L

    init {
      objToNode[obj] = this
    }

    fun expand() {
      expander.expand(this)
    }

    fun expandCorrespondingEdge(e: Edge) = expander.expandCorrespondingEdge(this, e)

    fun addEdgeTo(obj: Any, label: Expander.Label): Node? {
      if (forbiddenObjects.any { it === obj }) return null
      val e = Edge(this, getOrCreateNode(obj), label)
      edges.add(e)
      return e.end
    }

    // This is done lazily, as it is only of interest on the final iteration, and the computation would be
    // wasteful on previous iterations.
    fun getApproximateSize(): Long {
      if (approximateSize == -1L) {
        approximateSize = ReflectionUtil.estimateSize(obj)
      }
      return approximateSize
    }

    operator fun get(e: Edge) = expander.getChildForLabel(this, e.label)

    // returns a path from a root to this Node, by following incomingEdge references
    fun getPath(isRoot: (Edge) -> Boolean = { it.label is Expander.RootLoopbackLabel }): Path {
      var e = incomingEdge
      val edges = mutableListOf<Edge>()
      while (e != null && !isRoot(e)) {
        edges.add(e)
        e = e.previous()
      }
      return edges.reversed()
    }

    fun getLeaktrace(): Leaktrace {
      val path = getPath()
      if (path.isEmpty()) return Leaktrace(listOf(LeaktraceElement("ROOT", "", null)))
      return Leaktrace(path.map { it.signature() }.plus(LeaktraceElement(path.tip().type.name, "", path.tip().obj)))
    }

    fun markAsGrowing() {
      if (!growing) {
        growing = true
        leakRoots.add(this)
      }
    }

    fun unmarkGrowing() {
      growing = false
    }

    fun getNode(obj: Any?): Node? = if (obj != null) objToNode[obj] else null

    /* The following methods aren't used directly, but might be useful for debugging leaks */
    // trashes marks
    private fun isReachableFrom(n: Node, followWeakSoftRefs: Boolean = false): Boolean {
      var found = false
      bfs(roots = listOf(n), followWeakSoftRefs = followWeakSoftRefs) { if (this@Node === this@bfs) found = true; return@bfs }
      return found
    }

    // trashes marks and incomingEdges
    fun shortestPathTo(n: Node, followWeakSoftRefs: Boolean = false): Path? {
      var found = false
      bfs(roots = listOf(this), setIncomingEdges = true, followWeakSoftRefs = followWeakSoftRefs) { if (n === this@bfs) found = true; return@bfs }
      return if(found) n.getPath { it.end === this } else null
    }

    // trashes marks
    fun dominates(target: Node, roots: Collection<Node> = rootNodes, followWeakSoftRefs: Boolean = false): Boolean {
      var found = false
      bfs (roots = roots, followWeakSoftRefs = followWeakSoftRefs, childFilter = { it !== this }) {
        if (this@bfs === target) {
          found = true
          return@bfs
        }
      }
      return found && target.isReachableFrom(this)
    }

    // trashes marks
    private fun dominatedNodes(roots: Collection<Node> = rootNodes, followWeakSoftRefs: Boolean = false) = dominatedNodes(setOf(this), roots)

    fun retainedSize() = dominatedNodes().fold(0L) { acc, node -> acc + node.approximateSize }
  }

  private fun forEachNode(action: Node.() -> Unit) = nodes.forEach { it.action() }

  fun getOrCreateNode(obj: Any): Node = objToNode[obj] ?: Node(obj)

  fun expandWholeGraph(initialRun: Boolean = false): HeapGraph {
    withThreadsPaused {
        time("Expanding graph") {
          bfs { expand(); if (initialRun && expander.canPotentiallyGrowIndefinitely(this)) markAsGrowing() }
        }
    }
    println("Graph has ${nodes.size} nodes")
    return this
  }

  // note: path may or may not be a path in this graph
  private fun getNodeForPath(path: Path, expand: Boolean = false): Node? {
    if (path.isEmpty()) return null // if it's a root, meh
    val correspondingRoot = objToNode[path.root().obj]
    if (correspondingRoot == null) return null
    var node: Node = correspondingRoot
    for (e in path) {
      val child = if (expand) node.expandCorrespondingEdge(e) else node[e]
      if (child != null) {
        node = child
      } else {
        return null
      }
    }
    return node
  }

  private fun markAll(value: Int = 0) = forEachNode { mark = value }

  /** Performs breadth-first search on the graph.
   *
   * @param clearMarks If true, sets all node marks to [markValue]-1
   * @param markValue When a node is encountered, its [mark] is set to [markValue] so it won't be traversed again.
   * @param setIncomingEdges If true, sets each traversed node's [incomingEdge] to the edge that caused it to be added to the queue.
   * @param followWeakSoftRefs Whether to follow weak and soft references.
   * @param childFilter provides an opportunity to restrict the search scope: nodes for which this returns false will not be
   * added to the queue.
   * @param rootNodes The starting points for the search. Defaults to the HeapGraph roots.
   * @param action is executed on each node as it is removed from the queue
   */
  private fun bfs(clearMarks: Boolean = true, markValue: Int = 1, setIncomingEdges: Boolean = false, followWeakSoftRefs: Boolean = true,
                  childFilter: (Node) -> Boolean = { true }, roots: Collection<Node> = rootNodes, action: Node.() -> Unit) {
    if (clearMarks) markAll(markValue - 1)
    if (setIncomingEdges) nodes.forEach { it.incomingEdge = null }
    roots.forEach {it.mark = markValue}
    with (ArrayDeque<Node>()) {
      addAll(roots)
      while (isNotEmpty()) {
        val n = pop()
        n.action()
        for (e in n.edges) {
          val child = e.end
          if (child.mark != markValue && childFilter(child) && !(followWeakSoftRefs && Reference::class.java.isAssignableFrom(child.type))) {
            if (setIncomingEdges && child.incomingEdge == null) child.incomingEdge = e
            add(child)
          }
          child.mark = markValue
        }
      }
    }
  }

  fun propagateGrowing(newGraph: HeapGraph) {
    time("Propagate growing") {
      withThreadsPaused {
        newGraph.markAll(0)
        val q = ArrayDeque<Pair<Node, Node>>()
        with(q) {
          addAll(rootNodes.zip(newGraph.rootNodes))
          newGraph.rootNodes.forEach { it.mark = 1 }
          while (isNotEmpty()) {
            val (old, new) = pop()
            if (old.growing && old.degree < new.degree) {
              new.markAsGrowing()
            }
            for (e in old.edges) {
              val correspondingNewNode = new[e]
              if (correspondingNewNode != null && correspondingNewNode.mark == 0) {
                correspondingNewNode.mark = 1
                add(e.end to correspondingNewNode)
              }
            }
          }
        }
      }
    }
    println("New graph has ${newGraph.leakRoots.size} potential leak roots")
  }

  fun propagateGrowingIncremental(newGraph: HeapGraph) {
    time("Incremental propagate growing") {
      withThreadsPaused {
        for (leakRoot in leakRoots) {
          val newNode = newGraph.getNodeForPath(leakRoot.getPath(), true)
          if (newNode != null) {
            newNode.expand()  // need to expand fully at the end to figure out how many children there are
            if (leakRoot.degree < newNode.degree) {
              newNode.markAsGrowing()
            }
          }
        }
      }
    }
    println("New graph has ${newGraph.leakRoots.size} potential leak roots")
  }

  fun getLeaks(prevGraph: HeapGraph, dominatorTimeout: Duration): List<LeakInfo> {
    val leaks = leakRoots.mapNotNull { root ->
      (prevGraph.getNodeForPath(root.getPath()) ?: prevGraph.leakRoots.find { it.obj === root.obj })?.let { prevRoot ->
        LeakInfo(this, root, prevRoot)
      }
    }
    var startTime = System.currentTimeMillis()
    leaks.forEach { leak ->
      if (System.currentTimeMillis() - startTime > dominatorTimeout.toMillis()) return@forEach
      leak.retainedByNewChildren.addAll(dominatedNodes(leak.addedChildren.toSet()))
      leak.retainedByAllChildren.addAll(dominatedNodes(leak.leakRoot.children.toSet()))
    }
    return leaks
  }

  private fun List<Node>.anyReachableFrom(roots: List<Node>): Boolean {
    var found = false
    bfs(roots = roots, setIncomingEdges = true) { if (this@bfs in this@anyReachableFrom) found = true; return@bfs }
    return found
  }

  fun dominatedNodes(dominators: Set<Node>, traversalRoots: Collection<Node> = rootNodes, followWeakSoftRefs: Boolean = false): List<Node> {
    val dominated = mutableListOf<Node>()
    bfs (roots = traversalRoots, followWeakSoftRefs = followWeakSoftRefs, childFilter = { it !in dominators }) {}
    bfs (clearMarks = false, markValue = 2, roots = dominators, followWeakSoftRefs = followWeakSoftRefs, childFilter = { it.mark != 1 }) {
      dominated.add(this)
    }
    return dominated
  }

  fun computeIncomingEdges(followWeakSoftRefs: Boolean = true): Map<Node, List<Edge>> {
    val incomingEdgeMap = IdentityHashMap<Node, MutableList<Edge>>()
    nodes.forEach { incomingEdgeMap[it] = mutableListOf() }
    for (n in nodes) {
      for (e in n.edges) {
        if (e.label !is Expander.RootLoopbackLabel && (followWeakSoftRefs || e.isStrong())) {
          incomingEdgeMap[e.end]?.add(e)
        }
      }
    }
    return incomingEdgeMap
  }

  fun instancesOf(klass: Class<*>) = nodes.filter { it.type === klass }
  fun instancesOf(className: String) = nodes.filter { it.type.name == className }

  companion object {
    val jniHelper: BleakHelper = if (System.getProperty("bleak.jvmti.enabled") == "true") JniBleakHelper() else JavaBleakHelper()

    fun withThreadsPaused(action: () -> Unit) {
      jniHelper.pauseThreads()
      action()
      jniHelper.resumeThreads()
    }
  }
}

class Edge(val start: Node, val end: Node, val label: Expander.Label): DoNotTrace {
  init {
    if (end.incomingEdge == null) end.incomingEdge = this
  }
  // the signature is only used for ignore-listing
  fun signature(): LeaktraceElement =
    if (start.isRootNode) {
      LeaktraceElement("ROOT", if (end.obj === BootstrapClassloaderPlaceholder) "BootstrapClassLoader" else end.type.simpleName, end.obj)
    } else if (label is Expander.FieldLabel && (label.field.modifiers and Modifier.STATIC) != 0) {
      LeaktraceElement(label.field.declaringClass.name, label.signature(), end.obj)
    } else {
      LeaktraceElement(start.type.name, label.signature(), end.obj)
    }

  fun previous(): Edge? = start.incomingEdge
  private fun isWeak() = start.obj is WeakReference<*>
  private fun isSoft() = start.obj is SoftReference<*>
  fun isStrong() = !(isWeak() || isSoft())

  fun delete() {
    start.edges.remove(this)
  }
}

private fun time (description: String, action: () -> Unit) = println("$description took ${measureTimeMillis(action)}ms")

private typealias Path = List<Edge>
private fun Path.root() = first().start
private fun Path.tip() = last().end
