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
import com.google.common.collect.Lists
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import org.jetbrains.uast.*
import java.io.BufferedWriter
import java.io.FileWriter
import java.io.PrintWriter
import java.util.*
import kotlin.collections.LinkedHashMap

private val LOG = Logger.getInstance(CallGraph::class.java)

sealed class CallTarget(open val element: UElement) {
  data class Method(override val element: UMethod) : CallTarget(element)
  data class Lambda(override val element: ULambdaExpression) : CallTarget(element)
  data class DefaultConstructor(override val element: UClass) : CallTarget(element)
}

/** A graph in which nodes represent methods (including lambdas) and edges indicate that one method calls another. */
interface CallGraph {
  val nodes: Collection<Node>

  interface Node {
    val caller: CallTarget
    val edges: Collection<Edge>
    val likelyEdges: Collection<Edge> get() = edges.filter { it.isLikely }
  }

  /**
   * An edge to [node] of type [kind] due to [call].
   * [call] can be null for, e.g., implicit calls to super constructors.
   * [node] can be null for variable function invocations in Kotlin.
   */
  data class Edge(val node: Node?, val call: UCallExpression?, val kind: Kind) {
    val isLikely: Boolean get() = kind.isLikely

    enum class Kind {
      DIRECT, // A statically dispatched call.
      UNIQUE, // A call that appears to have a single implementation.
      TYPE_EVIDENCED, // A call evidenced by runtime type estimates.
      BASE, // The base method of a call (always added unless the base method is also direct, unique, or type-evidenced).
      NON_UNIQUE_OVERRIDE, // A call that is one of several overriding implementations.
      INVOKE; // A function expression invocation in Kotlin.

      val isLikely: Boolean get() = when (this) {
        DIRECT, UNIQUE, TYPE_EVIDENCED -> true
        BASE, NON_UNIQUE_OVERRIDE, INVOKE -> false
      }
    }
  }

  fun getNode(element: UElement): Node

  /** Describes all contents of the call graph (for debugging). */
  @Suppress("UNUSED")
  fun dump(filter: (Edge) -> Boolean = { true }) = buildString {
    for (node in nodes.sortedBy { it.shortName }) {
      val callees = node.edges.filter(filter)
      appendln(node.shortName)
      callees.forEach { appendln("    ${it.node.shortName} [${it.kind}]") }
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

/** Describes this node in a form like class#method, using "anon" for anonymous classes and lambdas. */
val Node?.shortName: String get() {
  if (this == null)
    return "[unresolved invoke]"
  val containingClass = caller.element.getContainingUClass()
  val containingClassStr =
      if (containingClass?.name == "Companion") containingClass.containingClass?.name ?: "anon"
      else containingClass?.name ?: "anon"
  val containingMethod = caller.element.getContainingUMethod()?.name ?: "anon"
  val caller = caller // Enables smart casts.
  return when (caller) {
    is Method -> "$containingClassStr#${caller.element.name}"
    is Lambda -> "$containingClassStr#$containingMethod#lambda"
    is DefaultConstructor -> "${caller.element.name}#${caller.element.name}"
  }
}

class MutableCallGraph : CallGraph {
  private val nodeMap = LinkedHashMap<PsiElement, MutableNode>()
  override val nodes get() = nodeMap.values

  class MutableNode(override val caller: CallTarget,
                    override val edges: MutableCollection<Edge> = ArrayList()) : Node {
    override fun toString() = shortName
  }

  override fun getNode(element: UElement): MutableNode {
    // TODO(kotlin-uast-cleanup)
    // We hash Psi rather than Uast because not all Uast nodes implement hashCode/equals correctly.
    // For example, KotlinULambdaExpression uses pointer equality rather than comparing the underlying psi.
    // Pointer equality is insufficient because Uast nodes can be reparsed.
    // This should be fixed once we have access to the Kotlin plugin source code.
    val psi = element.psi
    psi ?: throw Error("Expected psi for element $element")
    return nodeMap.getOrPut(psi) {
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
    return "Call graph: ${nodeMap.size} nodes, $numEdges edges"
  }
}

/** Returns non-intersecting paths from nodes in [sources] to nodes for which [isSink] returns true. */
fun <T : Any> searchForPaths(sources: Collection<T>,
                             isSink: (T) -> Boolean,
                             getNeighbors: (T) -> Collection<T>): Collection<List<T>> {
  val res = ArrayList<List<T>>()
  val prev = HashMap<T, T?>(sources.associate { Pair(it, null) })
  fun T.seen() = this in prev
  val used = HashSet<T>() // Nodes already part of a result path.
  val q = ArrayDeque<T>(sources.toSet())
  while (!q.isEmpty()) {
    val n = q.removeFirst()
    if (isSink(n)) {
      // Keep running time linear by preempting path construction if it intersects with one already seen.
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
  LOG.info("Number of nodes in path search: ${prev.size}")
  return res
}

/** Describes a parameter specialization tuple, mapping each parameter to a single concrete receiver. */
data class ParamContext(val params: List<Pair<UParameter, Receiver>>, val implicitThis: Receiver?) {
  operator fun get(param: UParameter) = params.firstOrNull { it.first == param }?.second

  companion object {
    val EMPTY = ParamContext(emptyList(), /*implicitReceiver*/ null)
  }

  // TODO(kotlin-uast-cleanup)
  // We hash Psi below rather than Uast because some Kotlin Uast nodes do not implement equals/hashCode correctly.
  // We should be able to remove the methods below once we can fix this issue in the Kotlin source code.

  private fun List<Pair<UParameter, Receiver>>.mapToPsi() = map { (param, receiver) ->
    Pair(param.psi.navigationElement, receiver.element.psi?.navigationElement)
  }

  private fun Receiver?.mapToPsi() = this?.element?.psi?.navigationElement

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    val o = other as? ParamContext ?: return false
    return implicitThis.mapToPsi() == o.implicitThis.mapToPsi() && params.mapToPsi() == o.params.mapToPsi()
  }

  override fun hashCode(): Int {
    return 31 * (implicitThis.mapToPsi()?.hashCode() ?: 0) + params.mapToPsi().hashCode()
  }
}

/**
 * A specialization of a call graph node on a parameter context.
 */
@Suppress("DataClassPrivateConstructor")
data class SearchNode private constructor(val node: Node, val paramContext: ParamContext) {
  // The [cause] field indicates the UAST element (usually a call expression) that led to this search node.
  // It does not participate in the data class equality method, as in the end we only care about the path that *first*
  // reaches a given node with a given parameter context. We want others to be preempted during the BFS.
  lateinit var cause: UElement

  constructor(node: Node, paramContext: ParamContext, cause: UElement) : this(node, paramContext) {
    this.cause = cause
  }
}

/** Augments the non-contextual receiver evaluator with a parameter context. */
class ContextualReceiverEvaluator(val paramContext: ParamContext,
                                  val nonContextualReceiverEval: CallReceiverEvaluator) : CallReceiverEvaluator {

  override fun get(element: UElement): Collection<Receiver> = when (element) {
    is UThisExpression -> getForImplicitThis() // TODO: Qualified `this` not yet supported in UAST.
    is UParameter -> nonContextualReceiverEval[element] + listOfNotNull(paramContext[element])
    is USimpleNameReferenceExpression -> element.resolve()?.navigationElement?.toUElement()?.let { this[it] } ?: emptyList()
    else -> {
      // TODO(kotlin-uast-cleanup)
      // The Kotlin plugin appears to use two completely separate sets of Psi elements: one for the compiler frontend,
      // and another for integrating with Lint and other code that expects Java-like Psi behavior. The parameter context
      // uses the latter, but sometimes we have to use the former to help with name resolution. Thus here we
      // use the [navigationElement] to get the original Kotlin Psi while doing equality checks, in case [element] is
      // a parameter in disguise.
      val ours = paramContext.params.find { (param, _) -> param.psi.navigationElement.toUElement() == element }?.second
      nonContextualReceiverEval[element] + listOfNotNull(ours)
    }
  }

  override fun getForImplicitThis(): Collection<Receiver> = listOfNotNull(paramContext.implicitThis)
}

/**
 * Builds parameter contexts for the target of [call] by taking the Cartesian product of the call argument receivers.
 * Returns an empty parameter context if there are no call argument receivers.
 * See "The Cartesian Product Algorithm" by Ole Agesen.
 */
fun CallTarget.buildParamContextsFromCall(call: UCallExpression, receiverEval: CallReceiverEvaluator): Collection<ParamContext> {
  val params = when (this) {
    is Method -> element.uastParameters
    is Lambda -> element.valueParameters
    is DefaultConstructor -> emptyList()
  }
  val args = call.valueArguments
  val argReceivers = args.map { receiverEval[it].toList() }

  // Note that the size of params may not match the size of args when there is
  // a variadic parameter; in that case the variadic parameter is ignored.

  // We will take a Cartesian product, so filter out empty receiver sets.
  val (paramsWithReceivers, nonEmptyArgReceivers) = params.zip(argReceivers)
      .filter { it.second.isNotEmpty() }
      .unzip()

  // The potential for an implicit receiver argument to the callee complicates the logic here.
  // When creating the Cartesian product we include the implicit receiver argument as a "normal" call argument, then
  // pull it out again when creating parameter contexts.

  val callHasReceiver = when (this) {
    is Method -> !element.isStatic
    is Lambda -> false // TODO: Kotlin lambdas can have receivers, but this seems to not be reflected in UAST (yet?).
    is DefaultConstructor -> false // Constructors technically take in a pointer to `this`, but it doesn't help this analysis.
  }

  val callReceiver = call.receiver
  val implicitReceiverReceivers = when {
    callHasReceiver && callReceiver != null -> receiverEval[callReceiver].toList()
    callHasReceiver && callReceiver == null -> receiverEval.getForImplicitThis().toList()
    else -> emptyList()
  }

  val hasImplicitReceivers = implicitReceiverReceivers.isNotEmpty()
  val allReceiverLists =
      if (hasImplicitReceivers) listOf(implicitReceiverReceivers) + nonEmptyArgReceivers
      else nonEmptyArgReceivers
  val numImplicitArgs = if (hasImplicitReceivers) 1 else 0

  if (allReceiverLists.isEmpty())
    return listOf(ParamContext.EMPTY) // Optimization.

  // Zip formal parameters with all possible argument receiver combinations.
  val cartesianProd = Lists.cartesianProduct(allReceiverLists)
  val maxNumParamContexts = 1000
  if (cartesianProd.size > maxNumParamContexts)
    LOG.warn("Combinatorial explosion for call: ${call.methodName}")
  val paramContexts = cartesianProd
      .take(maxNumParamContexts)
      .map { receiverTuple ->
        val zippedArgs = paramsWithReceivers.zip(receiverTuple.drop(numImplicitArgs))
        // TODO: There is potential to also extract implicit `this` args from callable references, though may need CHA.
        val implicitThis = receiverTuple.take(numImplicitArgs).filterIsInstance<Receiver.Class>().firstOrNull()
        ParamContext(zippedArgs, implicitThis)
      }
  assert(paramContexts.isNotEmpty())
  return paramContexts
}

/** Examines call sites to find contextualized neighbors of a search node. */
fun SearchNode.getNeighbors(callGraph: CallGraph,
                            nonContextualReceiverEval: CallReceiverEvaluator): Collection<SearchNode> {
  val contextualReceiverEval = ContextualReceiverEvaluator(paramContext, nonContextualReceiverEval)
  return node.edges.flatMap { edge ->
    fun CallTarget.buildParamContextsFromThisEdge() =
        if (edge.call == null) listOf(ParamContext.EMPTY)
        else buildParamContextsFromCall(edge.call, contextualReceiverEval)

    val cause = edge.call ?: node.caller.element
    when {
      edge.isLikely && edge.node != null -> {
        val paramContexts = edge.node.caller.buildParamContextsFromThisEdge()
        paramContexts.map { calleeContext -> SearchNode(edge.node, calleeContext, cause) }
      }
      (edge.kind == Edge.Kind.BASE || edge.kind == Edge.Kind.INVOKE) && edge.call != null -> {
        // Try to refine the base method to a concrete target.
        edge.call.getTargets(contextualReceiverEval).flatMap { target ->
          val paramContexts = target.buildParamContextsFromThisEdge()
          paramContexts.map { calleeContext -> SearchNode(callGraph.getNode(target.element), calleeContext, cause) }
        }
      }
      else -> emptyList()
    }
  }
}

/**
 * To find initial parameter contexts, we employ a nice trick: we do a BFS from *all* nodes in the
 * call graph (specialized on empty parameter contexts) and take note of all parameter contexts discovered for each node.
 * These parameter contexts are used to form search nodes that can be used to better initialize a subsequent path search.
 *
 * This is useful, e.g., for thread annotation checking. When a method is annotated with @UiThread and takes a lambda as an argument,
 * we want to make sure that we take note of all the lambdas passed to this method, as one of the lambdas may lead to
 * a @WorkerThread method. If we instead initialized the path search with empty parameter contexts,
 * then we would never see evidenced thread violations through the lambda parameter.
 */
fun CallGraph.buildAllReachableSearchNodes(nonContextualReceiverEval: CallReceiverEvaluator): Collection<SearchNode> {
  val res = ArrayList<SearchNode>()
  val allSources = nodes.map { SearchNode(it, ParamContext.EMPTY, cause = it.caller.element) }
  searchForPaths(
      sources = allSources,
      isSink = { res.add(it); false },
      getNeighbors = { it.getNeighbors(this, nonContextualReceiverEval) })
  return res
}

/** A context-sensitive search for paths from contextualized [searchSources] to [searchSinks]. */
fun CallGraph.searchForPathsFromSearchNodes(searchSources: Collection<SearchNode>,
                                            searchSinks: Collection<SearchNode>,
                                            nonContextualReceiverEval: CallReceiverEvaluator): Collection<List<SearchNode>> {
  val sinkSet = searchSinks.toSet()
  return searchForPaths(
      sources = searchSources,
      isSink = { it in sinkSet },
      getNeighbors = { it.getNeighbors(this, nonContextualReceiverEval) })
}

/** A context-sensitive search for paths from [sources] to [sinks]. */
fun CallGraph.searchForPaths(sources: Collection<Node>,
                             sinks: Collection<Node>,
                             nonContextualReceiverEval: CallReceiverEvaluator): Collection<List<SearchNode>> {

  val allSearchNodes = buildAllReachableSearchNodes(nonContextualReceiverEval)
  val sourceSet = sources.toSet()
  val sinkSet = sinks.toSet()
  val searchSources = allSearchNodes.filter { it.node in sourceSet }
  val searchSinks = allSearchNodes.filter { it.node in sinkSet }
  return searchForPathsFromSearchNodes(searchSources, searchSinks, nonContextualReceiverEval)
}