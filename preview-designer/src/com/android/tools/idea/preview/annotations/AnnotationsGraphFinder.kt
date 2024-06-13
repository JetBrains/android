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

import com.android.annotations.concurrency.Slow
import com.intellij.openapi.application.runReadAction
import com.intellij.util.containers.sequenceOfNotNull
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UElement

/**
 * Data class containing graph traversal information for a [UAnnotation].
 *
 * Note: the [children] field gets populated as the graph is traversed.
 */
data class UAnnotationSubtreeInfo(
  val depth: Int,
  val topLevelAnnotation: UAnnotation?,
  val children: MutableList<NodeInfo<UAnnotationSubtreeInfo>> = mutableListOf(),
)

/**
 * Specific implementation of [NodeInfo] that populates a [UAnnotationSubtreeInfo.children] field
 * before each child is traversed.
 *
 * The [onTraversal] parameter is invoked for each child, after it is traversed.
 */
private class UAnnotationNodeInfo(
  override val parent: NodeInfo<UAnnotationSubtreeInfo>?,
  override val element: UElement,
  override val subtreeInfo: UAnnotationSubtreeInfo,
  private val onTraversal: ((NodeInfo<UAnnotationSubtreeInfo>) -> Unit)? = null,
) : NodeInfo<UAnnotationSubtreeInfo> {

  override fun onSkippedChildTraversal(child: NodeInfo<UAnnotationSubtreeInfo>) {}

  override fun onAfterChildTraversal(child: NodeInfo<UAnnotationSubtreeInfo>) {
    onTraversal?.invoke(child)
  }

  override fun onBeforeChildTraversal(child: NodeInfo<UAnnotationSubtreeInfo>) {
    subtreeInfo.children += child
  }
}

/**
 * [NodeInfoFactory] implementation that creates [UAnnotationNodeInfo] for each node traversed.
 *
 * The [onTraversal] parameter is invoked after each node's child is traversed.
 */
private class UAnnotationNodeInfoFactory(
  private val onTraversal: ((NodeInfo<UAnnotationSubtreeInfo>) -> Unit)? = null
) : NodeInfoFactory<UAnnotationSubtreeInfo> {
  override fun create(
    parent: NodeInfo<UAnnotationSubtreeInfo>?,
    curElement: UElement,
  ): NodeInfo<UAnnotationSubtreeInfo> {
    return UAnnotationNodeInfo(
      parent = parent,
      element = curElement,
      subtreeInfo =
        UAnnotationSubtreeInfo(
          depth = parent?.subtreeInfo?.depth?.let { it + 1 } ?: 0,
          topLevelAnnotation = parent?.subtreeInfo?.topLevelAnnotation ?: curElement as? UAnnotation,
        ),
      onTraversal = onTraversal,
    )
  }
}

/**
 * [ResultFactory] that creates a [NodeInfo] of type [UAnnotationSubtreeInfo] for each annotation
 * that matches a given [filter] predicate.
 */
private class UAnnotationResultFactory(private val filter: (UAnnotation) -> Boolean) :
  ResultFactory<UAnnotationSubtreeInfo, NodeInfo<UAnnotationSubtreeInfo>> {
  override fun create(
    node: NodeInfo<UAnnotationSubtreeInfo>
  ): Sequence<NodeInfo<UAnnotationSubtreeInfo>> {
    return sequenceOfNotNull(
      node.takeIf {
        val element = it.element
        element is UAnnotation && filter(element)
      }
    )
  }
}

/**
 * Given an [UElement] used as the root element to search from, this method searches all reachable
 * annotations in the annotation graph and returns a list of [NodeInfo] of type
 * [UAnnotationSubtreeInfo] containing all annotations matching the [filter] predicate.
 *
 * The parameter [shouldTraverse] can be provided to determine which [UAnnotation]s in the graph
 * should be traversed. By default, all [UAnnotation]s are traversed.
 *
 * The [onTraversal] parameter, if provided, is invoked for each node that is traversed in the
 * graph. The traversal and invocation of this method is done post-order, see [ResultFactory].
 *
 * Note: [UAnnotation]s matching the [filter] predicate act as leaves when traversing the graph.
 */
@Slow
fun UElement.findAllAnnotationsInGraph(
  shouldTraverse: (UAnnotation) -> Boolean = ::shouldTraverse,
  onTraversal: ((NodeInfo<UAnnotationSubtreeInfo>) -> Unit)? = null,
  filter: (UAnnotation) -> Boolean,
): Sequence<NodeInfo<UAnnotationSubtreeInfo>> {
  val annotationsGraph =
    AnnotationsGraph(
      UAnnotationNodeInfoFactory(onTraversal),
      UAnnotationResultFactory { filter(it) },
    )
  return annotationsGraph.traverse(
    listOf(this),
    annotationFilter = { _, annotation -> shouldTraverse(annotation) || filter(annotation) },
    isLeafAnnotation = { filter(it) },
  )
}

/**
 * In Multipreview, every annotation is traversed in the DFS for finding Previews. This list is used
 * as an optimization to avoid traversing annotations which fqcn starts with any of these prefixes,
 * as those annotations will never lead to a Preview.
 */
private val NON_MULTIPREVIEW_PREFIXES = listOf("android.", "kotlin.", "kotlinx.", "java.")

/**
 * Returns true if one of the following is true:
 * 1. This annotation's class is defined in androidx (i.e. its fqcn starts with 'androidx.'), and it
 *    contains 'preview' as one of its subpackages (e.g. 'package androidx.example.preview' or
 *    'package androidx.preview.example')
 * 2. This annotation's fqcn doesn't start with 'androidx.' nor with any of the prefixes in
 *    [NON_MULTIPREVIEW_PREFIXES].
 */
@Slow
private fun UAnnotation.couldBeMultiPreviewAnnotation(): Boolean {
  return runReadAction { this.qualifiedName }
    ?.let { fqcn ->
      if (fqcn.startsWith("androidx.")) fqcn.contains(".preview.")
      else NON_MULTIPREVIEW_PREFIXES.none { fqcn.startsWith(it) }
    } == true
}

/**
 * Returns true when [annotation] is @Preview, or when it is a potential MultiPreview annotation.
 */
@Slow
private fun shouldTraverse(annotation: UAnnotation): Boolean =
  runReadAction { annotation.isPsiValid } && annotation.couldBeMultiPreviewAnnotation()
