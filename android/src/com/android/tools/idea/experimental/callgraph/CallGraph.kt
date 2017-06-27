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
import com.android.tools.idea.experimental.callgraph.CallTarget.*
import org.jetbrains.uast.*
import java.io.BufferedWriter
import java.io.FileWriter
import java.io.PrintWriter
import java.util.*
import kotlin.collections.LinkedHashMap

sealed class CallTarget(open val element: UElement) {
  data class Method(override val element: UMethod): CallTarget(element)
  data class Lambda(override val element: ULambdaExpression): CallTarget(element)
  data class DefaultConstructor(override val element: UClass): CallTarget(element)
}

/** A graph in which nodes represent methods (including lambdas) and edges indicate that one method calls another. */
interface CallGraph {
  val nodes: Collection<Node>

  interface Node {
    val caller: CallTarget
    val edges: Collection<Edge>
    val likelyEdges: Collection<Edge> get() = edges.filter { it.isLikely }

    fun findEdge(other: Node) = edges.find { it.node == other }

    /** Describes this node in a form like class#method, using "anon" for anonymous classes and lambdas. */
    val shortName: String get() {
      val containingClass = caller.element.getContainingUClass()?.name ?: "anon"
      val containingMethod = caller.element.getContainingUMethod()?.name ?: "anon"
      val caller = caller // Enables smart casts.
      return when (caller) {
        is Method -> "${containingClass}#${caller.element.name}"
        is Lambda -> "${containingClass}#${containingMethod}#lambda"
        is DefaultConstructor -> "${caller.element.name}#defaultCtor"
      }
    }
  }

  /** An edge to [node] of type [kind] due to [call]. Note that [call] can be null for, e.g., implicit calls to super constructors. */
  data class Edge(val node: Node, val call: UCallExpression?, val kind: Kind) {
    val isLikely: Boolean get() = kind.isLikely

    enum class Kind {
      DIRECT, // A statically dispatched call.
      UNIQUE, // A call that appears to have a single implementation.
      TYPE_EVIDENCED, // A call evidenced by runtime type estimates.
      NON_UNIQUE_BASE, // The base method of a call that has several overriding implementations.
      NON_UNIQUE_OVERRIDE; // A call that is one of several overriding implementations.

      val isLikely: Boolean get() = when (this) {
        DIRECT, UNIQUE, TYPE_EVIDENCED -> true
        NON_UNIQUE_BASE, NON_UNIQUE_OVERRIDE -> false
      }
    }
  }

  fun getNode(element: UElement): Node

  /** Describes all contents of the call graph (for debugging). */
  @Suppress("UNUSED")
  fun dump(filter: (Edge) -> Boolean = { true }) = buildString {
    for (node in nodes.sortedBy { it.shortName }) {
      val callees = node.edges.filter(filter)
      if (callees.isNotEmpty()) {
        appendln(node.shortName)
        callees.forEach { appendln("    ${it.node.shortName} [${it.kind}]") }
      }
    }
  }

  @Suppress("UNUSED")
  fun outputToDotFile(file: String, filter: (Edge) -> Boolean = { true }) {
    PrintWriter(BufferedWriter(FileWriter(file))).use { writer ->
      writer.println("digraph {")
      for (node in nodes) {
        for ((callee) in node.likelyEdges) {
          writer.print("  \"${node.shortName}\" -> \"${callee.shortName}\"")
        }
      }
      writer.println("}")
    }
  }
}

class MutableCallGraph : CallGraph {
  private val nodeMap = LinkedHashMap<UElement, MutableNode>()
  override val nodes get() = nodeMap.values

  class MutableNode(override val caller: CallTarget,
                    override val edges: MutableCollection<Edge> = ArrayList()) : Node

  override fun getNode(element: UElement): MutableNode {
    return nodeMap.getOrPut(element) {
      val caller = when (element) {
        is UMethod -> Method(element)
        is ULambdaExpression -> Lambda(element)
        is UClass -> DefaultConstructor(element)
        else -> throw Error("Unexpected UElement type ${element.javaClass}")
      }
      MutableNode(caller)
    }
  }

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