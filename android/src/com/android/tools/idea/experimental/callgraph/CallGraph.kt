/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.experimental.callgraph

import com.android.tools.idea.experimental.callgraph.CallGraph.Edge
import com.android.tools.idea.experimental.callgraph.CallGraph.Node
import com.intellij.psi.PsiElement
import java.util.*
import kotlin.collections.LinkedHashMap

/** A graph in which nodes represent methods (including lambdas) and edges indicate that one method calls another. */
interface CallGraph {
  val nodes: Collection<Node>

  interface Node {
    val method: PsiElement
    val edges: Collection<Edge>
    val likelyEdges: Collection<Edge> get() = edges.filter { it.isLikely }
  }

  data class Edge(val node: Node, val kind: Kind) {
    val isLikely: Boolean get() = kind.isLikely

    enum class Kind {
      DIRECT, // A statically dispatched call.
      UNIQUE_OVERRIDE, // A call that appears to have a single implementation.
      TYPE_EVIDENCED, // A call evidenced by runtime type estimates.
      NON_UNIQUE_OVERRIDE; // A call that is one of several overriding implementations.

      val isLikely: Boolean get() = when (this) {
        DIRECT, UNIQUE_OVERRIDE, TYPE_EVIDENCED -> true
        NON_UNIQUE_OVERRIDE -> false
      }
    }
  }

  fun getNode(method: PsiElement): Node
}

class MutableCallGraph : CallGraph {
  private val nodeMap = LinkedHashMap<PsiElement, MutableNode>()
  override val nodes get() = nodeMap.values

  class MutableNode(
      override val method: PsiElement,
      override val edges: MutableCollection<Edge> = ArrayList()
  ) : Node

  override fun getNode(method: PsiElement): MutableNode = nodeMap.getOrPut(method) { MutableNode(method) }

  // Helper functions for adding edges.
  fun addEdge(caller: MutableNode, callee: MutableNode, kind: Edge.Kind) {
    caller.edges.add(Edge(callee, kind))
  }
  fun addEdge(caller: MutableNode, callee: PsiElement, kind: Edge.Kind) = addEdge(caller, getNode(callee), kind)
  fun addEdge(caller: PsiElement, callee: PsiElement, kind: Edge.Kind) = addEdge(getNode(caller), getNode(callee), kind)

  override fun toString(): String {
    val numEdges = nodes.asSequence().map { it.edges.size }.sum()
    return "Call graph: ${nodeMap.size} nodes, ${numEdges} edges"
  }
}

/** Returns non-intersecting paths from nodes in [sources] to nodes in [sinks]. */
fun <T : Any> searchForPaths(sources: Collection<T>, sinks: Collection<T>, getNeighbors: (T) -> Collection<T>): Collection<List<T>> {
  val res = ArrayList<List<T>>()
  val sinkSet = LinkedHashSet(sinks)
  val prev = LinkedHashMap<T, T?>(sources.associate { Pair(it, null) })
  fun T.seen() = this in prev
  val used = LinkedHashSet<T>() // Nodes already part of a result path.
  val q = ArrayDeque<T>(sources.toSet())
  while (!q.isEmpty()) {
    val n = q.removeFirst()
    if (n in sinkSet) {
      // Keep things O(n) by preempting path construction if it intersects with one already seen.
      val path = generateSequence(n) { if (it in used) null else prev[it] }.toList().reversed()
      if (path.none { it in used })
        res.add(path)
      used.addAll(path)
    }
    else {
      getNeighbors(n).filter { !it.seen() }.forEach {
        q.addLast(it)
        prev[it] = n
      }
    }
  }
  return res
}