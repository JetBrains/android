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
package com.android.tools.idea.preview.annotations

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiModifierListOwner
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
  fun onBeforeChildTraversal(child: NodeInfo<S>)

  /**
   * Method called during [AnnotationsGraph.traverse], for every traversed edge `this` -> [child],
   * after the whole subtree rooted at [child] was already traversed. At this point, the [child]'s
   * [subtreeInfo] should be already set, and this method's main purpose is to provide some
   * contextual information from the [child]'s subtree to `this`. This could be used, for example,
   * for metrics collection (e.g. to accumulate the total number of direct and indirect @Preview
   * found under this annotation)
   */
  fun onAfterChildTraversal(child: NodeInfo<S>)

  /**
   * Method called during [AnnotationsGraph.traverse], for every not traversed edge `this` ->
   * [child], where the [child] has already been visited earlier during the graph traversal. At this
   * point, the [child]'s [subtreeInfo] should be already set, and this method's could be useful in
   * some cases to provide some contextual information from the [child]'s subtree to `this`. This
   * could be used, for example, to detect cycles.
   */
  fun onSkippedChildTraversal(child: NodeInfo<S>)
}

/**
 * Factory used by the [AnnotationsGraph] to produce relevant information about the nodes visited
 * during a graph traversal.
 */
interface NodeInfoFactory<S> {
  fun create(parent: NodeInfo<S>?, curElement: UElement): NodeInfo<S>
}

/** Factory used by the [AnnotationsGraph] to produce the output sequence for a graph traversal. */
interface ResultFactory<S, T> {
  fun create(node: NodeInfo<S>): Sequence<T>
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
   * @param annotationFilter: function used to decide which edges of the graph to use during the
   *   traversal. It should return true for the edges ([UElement] --annotated_with--> [UAnnotation])
   *   that should be traversed.
   * @param isLeafAnnotation: function used to identify "leaf" annotations during the traversal. It
   *   should return true for the [UAnnotation]s that are considered "leaf" annotations. Such
   *   annotations could be visited multiple times, but the DFS won't traverse through them.
   */
  fun traverse(
    sourceElements: List<UElement>,
    annotationFilter: (UElement, UAnnotation) -> Boolean = { _, _ -> true },
    isLeafAnnotation: (UAnnotation) -> Boolean = { false },
  ): Sequence<T> {
    val visitedAnnotationClasses: MutableMap<String?, NodeInfo<S>> = mutableMapOf()

    return sequence {
        yield(
          sourceElements.asSequence().flatMap {
            it.traverse(visitedAnnotationClasses, annotationFilter, isLeafAnnotation, parent = null)
          }
        )
      }
      .flatten()
  }

  private fun UElement.traverse(
    visitedAnnotationClasses: MutableMap<String?, NodeInfo<S>>,
    annotationFilter: (UElement, UAnnotation) -> Boolean,
    isLeafAnnotation: (UAnnotation) -> Boolean,
    parent: NodeInfo<S>?,
  ): Sequence<T> {
    val curNode = nodeInfoFactory.create(parent, this)
    parent?.onBeforeChildTraversal(curNode)
    // If `this` is an annotation, then mark it as visited by adding its fqcn to the map.
    // Note that the annotation class corresponding to leaf annotations could be visited
    // multiple times, and in such cases the value for the given annotation class will be
    // overwritten every time.
    if (this is UAnnotation) {
      runReadAction { this.qualifiedName }
        .let {
          visitedAnnotationClasses[it] = curNode
          // Log any unexpected null name. This could make the traversal to filter out some
          // annotations later if another null name happens
          if (it == null) logger.warn("Failed to resolve annotation qualified name")
        }
    }

    val result: Sequence<T>
    if ((this is UAnnotation) && isLeafAnnotation(this)) {
      result = resultFactory.create(curNode).also { parent?.onAfterChildTraversal(curNode) }
    } else {
      val annotations = this.getUAnnotations()
      result =
        sequence {
            yield(
              annotations
                .asSequence()
                .filter { annotationFilter(this@traverse, it) }
                .map { it to runReadAction { it.qualifiedName } }
                .flatMap { (annotation, name) ->
                  if (
                    (isLeafAnnotation(annotation) || !visitedAnnotationClasses.containsKey(name))
                  ) {
                    annotation.traverse(
                      visitedAnnotationClasses,
                      annotationFilter,
                      isLeafAnnotation,
                      curNode,
                    )
                  } else {
                    emptySequence<T>().also { _ ->
                      curNode.onSkippedChildTraversal(visitedAnnotationClasses[name]!!)
                    }
                  }
                }
            )

            yield(resultFactory.create(curNode).also { parent?.onAfterChildTraversal(curNode) })
          }
          .flatten()
    }

    return result
  }
}

/**
 * Helper function to try getting the annotations that this UElement is annotated with. It works
 * correctly when this element is a [UMethod] or a [UAnnotation], but it is not guaranteed that it
 * will work with other types of elements.
 */
fun UElement.getUAnnotations() = runReadAction {
  val annotations =
    (this as? UMethod)?.uAnnotations
      ?: (this.tryResolve() as? PsiModifierListOwner)?.annotations?.mapNotNull {
        it.toUElementOfType() as? UAnnotation
      }
      ?: emptyList()
  annotations.flatMap { annotation ->
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
private fun UAnnotation.extractFromContainer() = runReadAction {
  findDeclaredAttributeValue(null)?.sourcePsi?.children?.mapNotNull {
    it.toUElement() as? UAnnotation
  } ?: emptyList()
}
