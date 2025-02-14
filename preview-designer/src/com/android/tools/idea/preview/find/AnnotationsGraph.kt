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
package com.android.tools.idea.preview.find

import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiModifierListOwner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import org.jetbrains.kotlin.backend.common.pop
import org.jetbrains.kotlin.backend.common.push
import org.jetbrains.kotlin.utils.ifEmpty
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.tryResolve

/**
 * Base structure for the information that is relevant during a graph traversal, for each visited
 * node of the [AnnotationsGraph].
 *
 * This should be used to keep track of different contextual information that [ResultFactory] will
 * need to create the corresponding results.
 *
 * Given that the NodeInfo instance will live in the context of a graph traversal, then each of its
 * edges could impact on its relevant information. And when traversing a graph, there are 3 possible
 * moments where and edge could have different meanings: 1- When an edge is traversed forward (see
 * [onBeforeChildTraversal]). 2- When an edge is traversed backward (see [onAfterChildTraversal]).
 * 3- When an edge is not traversed as a consequence of leading to an already visited node (see
 * [onSkippedChildTraversal]).
 */
interface NodeInfo<S> {
  val parent: NodeInfo<S>?
  val element: UElement
  val subtreeInfo: S?

  /**
   * Method called during [AnnotationsGraph.traverse], immediately after traversing the edge `this`
   * -> [child], for every traversed edge. At this point [subtreeInfo] is usually not set, and this
   * method's main purpose is to provide some contextual information from `this` to [child]. This
   * could be used, for example, to count the number of specific child annotations already visited
   * (e.g. @Preview).
   */
  suspend fun onBeforeChildTraversal(child: NodeInfo<S>)

  /**
   * Method called during [AnnotationsGraph.traverse], for every traversed edge `this` -> [child],
   * after the whole subtree rooted at [child] was already traversed. At this point, the [child]'s
   * [subtreeInfo] should be already set, and this method's main purpose is to provide some
   * contextual information from the [child]'s subtree to `this`. This could be used, for example,
   * for metrics collection (e.g. to accumulate the total number of direct and indirect @Preview
   * found under this annotation)
   */
  suspend fun onAfterChildTraversal(child: NodeInfo<S>)

  /**
   * Method called during [AnnotationsGraph.traverse], for every not traversed edge `this` ->
   * [child], where the [child] has already been visited earlier during the graph traversal. At this
   * point, the [child]'s [subtreeInfo] should be already set, and this method's could be useful in
   * some cases to provide some contextual information from the [child]'s subtree to `this`. This
   * could be used, for example, to detect cycles.
   */
  suspend fun onSkippedChildTraversal(child: NodeInfo<S>)
}

/**
 * Factory used by the [AnnotationsGraph] to produce relevant information about the nodes visited
 * during a graph traversal.
 */
interface NodeInfoFactory<S> {
  suspend fun create(parent: NodeInfo<S>?, curElement: UElement): NodeInfo<S>
}

/** Factory used by the [AnnotationsGraph] to produce the output flow for a graph traversal. */
interface ResultFactory<S, T> {
  fun create(node: NodeInfo<S>): Flow<T>
}

/**
 * The annotations graph of a project is the graph formed by every [UElement] as node, and such that
 * each annotation of a given element is considered as a directed edge from the annotated element to
 * the annotation class of which the given annotation is instance.
 *
 * Then this could be used to traverse the annotations graph and gather some useful information from
 * it.
 *
 * @param nodeInfoFactory: used to create a [NodeInfo] for each [UElement] visited during a
 *   traversal, in pre-order.
 * @param resultFactory: used to create a sequence of [T] for each [NodeInfo] visited during a
 *   traversal, in post-order.
 *
 * For more information see [traverse], [NodeInfo], [NodeInfoFactory] and [ResultFactory].
 */
class AnnotationsGraph<S, T>(
  private val nodeInfoFactory: NodeInfoFactory<S>,
  private val resultFactory: ResultFactory<S, T>,
) {
  private val logger = Logger.getInstance(this.javaClass)

  /**
   * DFS to traverse the annotations graph using the given [sourceElements] as starting points.
   *
   * @param annotationFilter function used to decide which edges of the graph to use during the
   *   traversal. It should return true for the edges ([UElement] --annotated_with--> [UAnnotation])
   *   that should be traversed.
   * @param isLeafAnnotation function used to identify "leaf" annotations during the traversal. It
   *   should return true for the [UAnnotation]s that are considered "leaf" annotations. Such
   *   annotations could be visited multiple times, but the DFS won't traverse through them.
   */
  fun traverse(
    sourceElements: List<UElement>,
    annotationFilter: suspend (UElement, UAnnotation) -> Boolean = { _, _ -> true },
    isLeafAnnotation: suspend (UAnnotation) -> Boolean = { false },
  ): Flow<T> {
    val visitedAnnotationClasses: MutableMap<String, NodeInfo<S>> = mutableMapOf()

    return flow {
      sourceElements.forEach {
        emitAll(iterativeDfs(it, visitedAnnotationClasses, annotationFilter, isLeafAnnotation))
      }
    }
  }

  /** Iterative DFS implementation, to avoid stack overflow related problems for big graphs. */
  private fun iterativeDfs(
    rootElement: UElement,
    visitedAnnotationClasses: MutableMap<String, NodeInfo<S>>,
    annotationFilter: suspend (UElement, UAnnotation) -> Boolean,
    isLeafAnnotation: suspend (UAnnotation) -> Boolean,
  ): Flow<T> = flow {
    val stack = mutableListOf(DfsNode(parentInfo = null, rootElement))
    while (stack.isNotEmpty()) {
      val node = stack.pop()
      if (node.status == DfsNodeStatus.PROCESSED) {
        emitAll(
          resultFactory.create(node.nodeInfo).also {
            node.parentInfo?.onAfterChildTraversal(node.nodeInfo)
          }
        )
        continue
      }

      val annotationName =
        (node.element as? UAnnotation)?.let {
          // Log any unexpected null name as this could cause problems in the DFS.
          readAction { it.qualifiedName }
            .also { if (it == null) logger.warn("Failed to resolve annotation qualified name") }
        }

      // Skip already visited annotations
      if (annotationName != null && visitedAnnotationClasses.containsKey(annotationName)) {
        node.parentInfo?.onSkippedChildTraversal(visitedAnnotationClasses[annotationName]!!)
        continue
      }

      // Process the node and schedule its UP for after its children
      node.process()
      stack.push(node)

      // Leaf annotations don't have children and could be visited multiple times, so they should
      // not even be marked as visited.
      if ((node.element as? UAnnotation)?.let { isLeafAnnotation(it) } == true) {
        continue
      }

      // Mark current annotation as visited
      annotationName?.let { visitedAnnotationClasses[it] = node.nodeInfo }

      // Non-leaf annotations go down to its children annotations
      val annotations = node.element.getUAnnotations()
      annotations
        .filter { annotationFilter(node.element, it) }
        .reversed() // reversed to keep correct order in the stack
        .forEach { annotation ->
          stack.push(DfsNode(parentInfo = node.nodeInfo, element = annotation))
        }
    }
  }

  /** Helper class used for iterative DFS implementation. */
  private inner class DfsNode(val parentInfo: NodeInfo<S>?, val element: UElement) {
    var status: DfsNodeStatus = DfsNodeStatus.TO_PROCESS
      private set

    lateinit var nodeInfo: NodeInfo<S>

    suspend fun process() {
      assert(status == DfsNodeStatus.TO_PROCESS)
      nodeInfo = nodeInfoFactory.create(parentInfo, element)
      parentInfo?.onBeforeChildTraversal(nodeInfo)
      status = DfsNodeStatus.PROCESSED
    }
  }

  private enum class DfsNodeStatus {
    /**
     * Indicates that a node hasn't been processed yet, and still needs to traverse its children.
     */
    TO_PROCESS,

    /**
     * Indicates that a node has been processed, and should go up once all its children are
     * processed.
     */
    PROCESSED,
  }
}

/**
 * Helper function to try getting the annotations that this UElement is annotated with. It works
 * correctly when this element is a [UMethod] or a [UAnnotation], but it is not guaranteed that it
 * will work with other types of elements.
 */
suspend fun UElement.getUAnnotations(): List<UAnnotation> {
  val annotations = readAction {
    (this@getUAnnotations as? UMethod)?.uAnnotations
      ?: (this@getUAnnotations.tryResolve() as? PsiModifierListOwner)?.annotations?.mapNotNull {
        it.toUElementOfType() as? UAnnotation
      }
      ?: emptyList()
  }
  return annotations.flatMap { annotation ->
    annotation.extractFromContainer().ifEmpty { listOf(annotation) }
  }
}

/**
 * MultiPreviews imported from a library will put repeated annotations of a given class (e.g.
 * Previews) inside a container (more info at
 * https://kotlinlang.org/docs/annotations.html#repeatable-annotations). This method extracts all
 * annotations of a given container to have a list of individual annotations.
 *
 * When the annotation is not a container it returns an empty list.
 */
private suspend fun UAnnotation.extractFromContainer() = readAction {
  findDeclaredAttributeValue(null)?.sourcePsi?.children?.mapNotNull {
    it.toUElement() as? UAnnotation
  } ?: emptyList()
}
