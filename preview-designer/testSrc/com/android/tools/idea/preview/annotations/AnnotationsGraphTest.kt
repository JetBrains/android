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

import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiClass
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.tryResolve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private class DfsSubtreeEdges {
  var treeEdgesCount = 0
  var backEdgesCount = 0
  var forwardEdgesCount = 0
  var crossEdgesCount = 0
}

private class TestNodeInfo(
  override val parent: NodeInfo<DfsSubtreeEdges>?,
  override val element: UElement,
) : NodeInfo<DfsSubtreeEdges> {
  override val subtreeInfo: DfsSubtreeEdges = DfsSubtreeEdges()
  // Timers or step counters needed to differentiate cross edges from forward edges
  // (more info https://www.cs.yale.edu/homes/aspnes/pinewiki/DepthFirstSearch.html)
  var inTime: Int = 1 + ((parent as? TestNodeInfo)?.curTime ?: 0)
  private var curTime: Int = inTime

  // Flag used to distinguish back edges
  private var traversing = false

  override fun onSkippedChildTraversal(child: NodeInfo<DfsSubtreeEdges>) {
    // Skipped edges are back, forward or cross
    if ((child as TestNodeInfo).traversing) subtreeInfo.backEdgesCount++
    else if (child.inTime > inTime) subtreeInfo.forwardEdgesCount++
    else subtreeInfo.crossEdgesCount++
  }

  override fun onAfterChildTraversal(child: NodeInfo<DfsSubtreeEdges>) {
    assertTrue(traversing)
    traversing = false
    curTime = (child as TestNodeInfo).curTime + 1
    subtreeInfo.treeEdgesCount += child.subtreeInfo.treeEdgesCount
    subtreeInfo.backEdgesCount += child.subtreeInfo.backEdgesCount
    subtreeInfo.forwardEdgesCount += child.subtreeInfo.forwardEdgesCount
    subtreeInfo.crossEdgesCount += child.subtreeInfo.crossEdgesCount
  }

  override fun onBeforeChildTraversal(child: NodeInfo<DfsSubtreeEdges>) {
    assertFalse(traversing)
    traversing = true
    // Traversed edges are tree edges
    subtreeInfo.treeEdgesCount++
  }
}

private object TestNodeInfoFactory : NodeInfoFactory<DfsSubtreeEdges> {
  override fun create(
    parent: NodeInfo<DfsSubtreeEdges>?,
    curElement: UElement,
  ): NodeInfo<DfsSubtreeEdges> {
    return TestNodeInfo(parent, curElement)
  }
}

/**
 * A result factory that for each visited annotation, it returns a string with its name after
 * removing the prefix "node" from it. And also, for the root method, it returns its subtreeInfo.
 */
private object TestResultFactory : ResultFactory<DfsSubtreeEdges, Any> {
  override fun create(node: NodeInfo<DfsSubtreeEdges>): Sequence<Any> =
    if (node.element is UAnnotation) sequenceOf(node.element.name)
    else sequenceOf(node.subtreeInfo!!)

  private val UElement.name: String
    get() = runReadAction {
      if (this is UMethod) this.name
      else (this.tryResolve() as PsiClass).name!!.removePrefix("node")
    }
}

/**
 * The [AnnotationsGraph] could be used in different ways, and different information could be
 * extracted from it depending on the use case. Which information is extracted, strictly depends on
 * the implementation of the different factories involved. For the tests in this file, the factories
 * are defined so that the result is formed by two different things:
 *
 * 1- The number of tree, back, cross and forward edges found during the DFS traversal (this type of
 * edges are simple concepts associated with a DFS on a directed graph, more information here:
 * https://en.wikipedia.org/wiki/Depth-first_search). This information is directly related to DFS,
 * and as a consequence, it allows us to test the DFS ([AnnotationsGraph.traverse]) implementation.
 *
 * 2- The name of each visited annotation. This allows us to test that the annotations graph can be
 * used to correctly find all the corresponding direct and indirect annotations of a given UElement.
 */
class AnnotationsGraphTest {
  @get:Rule val projectRule: AndroidProjectRule = AndroidProjectRule.inMemory()

  private val project
    get() = projectRule.project

  private val fixture
    get() = projectRule.fixture

  private val annotationsGraph = AnnotationsGraph(TestNodeInfoFactory, TestResultFactory)

  @Test
  fun testTraverse_backEdge() =
    runBlocking<Unit> {
      val fileContent =
        """
      // Graph illustration:
      // rootMethod --> 0 --> 1 --> 2 --> 3
      //                      ^-----------'
      // Note that all edges are tree edges, except 3->1, that is a back edge.
      @node2
      annotation class node1

      @node3
      annotation class node2

      @node1
      annotation class node3

      @node1
      annotation class node0

      @node0
      fun rootMethod(){}
    """
          .trimIndent()

      val psiFile = fixture.configureByText(KotlinFileType.INSTANCE, fileContent)
      val rootMethod = runReadAction {
        findAnnotations(project, psiFile.virtualFile, "node0").single().let {
          it.getContainingUMethodAnnotatedWith(it.qualifiedName!!)
        }!!
      }
      val traverseResult = annotationsGraph.traverse(listOf(rootMethod)).toList()
      // Results are computed in post-order
      assertEquals(listOf("3", "2", "1", "0"), traverseResult.filterIsInstance<String>())
      val edgesResult = traverseResult.filterIsInstance<DfsSubtreeEdges>().single()
      assertEquals(4, edgesResult.treeEdgesCount)
      assertEquals(1, edgesResult.backEdgesCount)
      assertEquals(0, edgesResult.forwardEdgesCount)
      assertEquals(0, edgesResult.crossEdgesCount)
    }

  @Test
  fun testTraverse_forwardEdge() =
    runBlocking<Unit> {
      val fileContent =
        """
      // Graph illustration:
      // rootMethod --> 0 --> 1 --> 2 --> 3
      //                      '-----------^
      // Note that all edges are tree edges, except 1->3, that is a forward edge.
      @node2
      @node3
      annotation class node1

      @node3
      annotation class node2

      annotation class node3

      @node1
      annotation class node0

      @node0
      fun rootMethod(){}
    """
          .trimIndent()

      val psiFile = fixture.configureByText(KotlinFileType.INSTANCE, fileContent)
      val rootMethod = runReadAction {
        findAnnotations(project, psiFile.virtualFile, "node0").single().let {
          it.getContainingUMethodAnnotatedWith(it.qualifiedName!!)
        }!!
      }
      val traverseResult = annotationsGraph.traverse(listOf(rootMethod)).toList()
      // Results are computed in post-order
      assertEquals(listOf("3", "2", "1", "0"), traverseResult.filterIsInstance<String>())
      val edgesResult = traverseResult.filterIsInstance<DfsSubtreeEdges>().single()
      assertEquals(4, edgesResult.treeEdgesCount)
      assertEquals(0, edgesResult.backEdgesCount)
      assertEquals(1, edgesResult.forwardEdgesCount)
      assertEquals(0, edgesResult.crossEdgesCount)
    }

  @Test
  fun testTraverse_crossEdge() =
    runBlocking<Unit> {
      val fileContent =
        """
      // Graph illustration:
      // rootMethod --> 0 --> 1 --> 2 --> 3
      //                      '---> 4 ----^
      // Note that all edges are tree edges, except 4->3, that is a cross edge.
      @node2
      @node4
      annotation class node1

      @node3
      annotation class node2

      annotation class node3

      @node3
      annotation class node4

      @node1
      annotation class node0

      @node0
      fun rootMethod(){}
    """
          .trimIndent()

      val psiFile = fixture.configureByText(KotlinFileType.INSTANCE, fileContent)
      val rootMethod = runReadAction {
        findAnnotations(project, psiFile.virtualFile, "node0").single().let {
          it.getContainingUMethodAnnotatedWith(it.qualifiedName!!)
        }!!
      }
      val traverseResult = annotationsGraph.traverse(listOf(rootMethod)).toList()
      // Results are computed in post-order
      assertEquals(listOf("3", "2", "4", "1", "0"), traverseResult.filterIsInstance<String>())
      val edgesResult = traverseResult.filterIsInstance<DfsSubtreeEdges>().single()
      assertEquals(5, edgesResult.treeEdgesCount)
      assertEquals(0, edgesResult.backEdgesCount)
      assertEquals(0, edgesResult.forwardEdgesCount)
      assertEquals(1, edgesResult.crossEdgesCount)
    }

  private val fileContentWithAllEdgeTypes =
    """
      // This graph is the result of merging together the graphs in
      // testTraverse_backEdge, testTraverse_forwardEdge and testTraverse_crossEdge.
      @node2
      @node4
      @node3
      annotation class node1

      @node3
      annotation class node2

      @node1
      annotation class node3

      @node3
      annotation class node4

      @node1
      annotation class node0

      @node0
      fun rootMethod(){}
    """
      .trimIndent()

  @Test
  fun testTraverse_allEdges() =
    runBlocking<Unit> {
      val psiFile = fixture.configureByText(KotlinFileType.INSTANCE, fileContentWithAllEdgeTypes)
      val rootMethod = runReadAction {
        findAnnotations(project, psiFile.virtualFile, "node0").single().let {
          it.getContainingUMethodAnnotatedWith(it.qualifiedName!!)
        }!!
      }
      val traverseResult = annotationsGraph.traverse(listOf(rootMethod)).toList()
      // Results are computed in post-order
      assertEquals(listOf("3", "2", "4", "1", "0"), traverseResult.filterIsInstance<String>())
      val edgesResult = traverseResult.filterIsInstance<DfsSubtreeEdges>().single()
      assertEquals(5, edgesResult.treeEdgesCount)
      assertEquals(1, edgesResult.backEdgesCount)
      assertEquals(1, edgesResult.forwardEdgesCount)
      assertEquals(1, edgesResult.crossEdgesCount)
    }

  @Test
  fun testIsLeafAnnotation() =
    runBlocking<Unit> {
      val psiFile = fixture.configureByText(KotlinFileType.INSTANCE, fileContentWithAllEdgeTypes)
      val rootMethod = runReadAction {
        findAnnotations(project, psiFile.virtualFile, "node0").single().let {
          it.getContainingUMethodAnnotatedWith(it.qualifiedName!!)
        }!!
      }
      val traverseResult =
        annotationsGraph
          .traverse(
            listOf(rootMethod),
            isLeafAnnotation = { annotation ->
              runReadAction { annotation.qualifiedName!!.contains("node3") }
            },
          )
          .toList()
      // Results are computed in post-order, and 3 is a "leaf", so its visited many times
      assertEquals(
        listOf("3", "2", "3", "4", "3", "1", "0"),
        traverseResult.filterIsInstance<String>(),
      )
      // As node3 is a leaf annotation, then:
      // 1- All its incoming edges should be tree edges (the forward and cross edges become tree
      // edges),
      // 2- All its outgoing edges should be ignored (the back edge is ignored)
      val edgesResult = traverseResult.filterIsInstance<DfsSubtreeEdges>().single()
      assertEquals(7, edgesResult.treeEdgesCount)
      assertEquals(0, edgesResult.backEdgesCount)
      assertEquals(0, edgesResult.forwardEdgesCount)
      assertEquals(0, edgesResult.crossEdgesCount)
    }

  @Test
  fun testAnnotationFilter() =
    runBlocking<Unit> {
      val psiFile = fixture.configureByText(KotlinFileType.INSTANCE, fileContentWithAllEdgeTypes)
      val rootMethod = runReadAction {
        findAnnotations(project, psiFile.virtualFile, "node0").single().let {
          it.getContainingUMethodAnnotatedWith(it.qualifiedName!!)
        }!!
      }
      val traverseResult =
        annotationsGraph
          .traverse(
            listOf(rootMethod),
            annotationFilter = { _, annotation ->
              runReadAction { !annotation.qualifiedName!!.contains("node4") }
            },
          )
          .toList()
      // Results are computed in post-order, and node4 is filtered out due to the annotationFilter
      assertEquals(listOf("3", "2", "1", "0"), traverseResult.filterIsInstance<String>())
      // As node4 is filtered out, then:
      // 1- All its incoming edges should be ignored (a tree edge is ignored),
      // 2- All its outgoing edges should be ignored (the cross edge is ignored)
      val edgesResult = traverseResult.filterIsInstance<DfsSubtreeEdges>().single()
      assertEquals(4, edgesResult.treeEdgesCount)
      assertEquals(1, edgesResult.backEdgesCount)
      assertEquals(1, edgesResult.forwardEdgesCount)
      assertEquals(0, edgesResult.crossEdgesCount)
    }

  // Stress test intended to catch any significant performance regression.
  @Test
  fun testPerformanceDeepTree() =
    runBlocking<Unit> {
      // Graph illustration:
      // rootMethod --> 1 --> 2 --> 3 --> ... --> 10.000
      val nodesCount = 1e4.toInt()
      val fileContent = buildString {
        repeat(nodesCount) {
          if (it == 0) {
            appendLine("@node1")
            appendLine("fun rootMethod(){}")
          } else {
            appendLine("")
            if (it + 1 < nodesCount) appendLine("@node${it+1}")
            appendLine("annotation class node$it")
          }
        }
      }
      val psiFile = fixture.configureByText(KotlinFileType.INSTANCE, fileContent)
      val rootMethod = runReadAction {
        findAnnotations(project, psiFile.virtualFile, "node1").single().let {
          it.getContainingUMethodAnnotatedWith(it.qualifiedName!!)
        }!!
      }
      // TODO: b/381539736 Use the same timeout once we understand why K2 is slower and fix the
      // issue
      val timeout = if (KotlinPluginModeProvider.isK2Mode()) 50.seconds else 25.seconds
      val traverseResult =
        withTimeout(timeout) {
          async { annotationsGraph.traverse(listOf(rootMethod)).toList() }.await()
        }
      // Results are computed in post-order
      assertEquals(
        List(nodesCount - 1) { "${(nodesCount - it - 1)}" },
        traverseResult.filterIsInstance<String>(),
      )
      val edgesResult = traverseResult.filterIsInstance<DfsSubtreeEdges>().single()
      assertEquals(nodesCount - 1, edgesResult.treeEdgesCount)
      assertEquals(0, edgesResult.backEdgesCount)
      assertEquals(0, edgesResult.forwardEdgesCount)
      assertEquals(0, edgesResult.crossEdgesCount)
    }

  // Stress test intended to catch any significant performance regression.
  @Test
  fun testPerformanceStarTree() =
    runBlocking<Unit> {
      // Graph illustration:
      // rootMethod --> 1
      //           '--> 2
      //           '--> 3
      //           ...
      //           '--> 10.000
      val nodesCount = 1e4.toInt()
      val fileContent = buildString {
        repeat(nodesCount) { i ->
          if (i == 0) {
            repeat(nodesCount - 1) { j -> appendLine("@node${j + 1}") }
            appendLine("fun rootMethod(){}")
          } else {
            appendLine("")
            appendLine("annotation class node$i")
          }
        }
      }
      val psiFile = fixture.configureByText(KotlinFileType.INSTANCE, fileContent)
      val rootMethod = runReadAction {
        findAnnotations(project, psiFile.virtualFile, "node1").single().let {
          it.getContainingUMethodAnnotatedWith(it.qualifiedName!!)
        }!!
      }
      val traverseResult =
        withTimeout(15.seconds) {
          async { annotationsGraph.traverse(listOf(rootMethod)).toList() }.await()
        }
      // Results are computed in post-order
      assertEquals(
        List(nodesCount - 1) { "${(it + 1)}" },
        traverseResult.filterIsInstance<String>(),
      )
      val edgesResult = traverseResult.filterIsInstance<DfsSubtreeEdges>().single()
      assertEquals(nodesCount - 1, edgesResult.treeEdgesCount)
      assertEquals(0, edgesResult.backEdgesCount)
      assertEquals(0, edgesResult.forwardEdgesCount)
      assertEquals(0, edgesResult.crossEdgesCount)
    }

  // Stress test intended to catch any significant performance regression.
  //
  // Dfs tree of the traversal will look like the DeepTree test above, but shorter, and the rest of
  // the edges will be back and forward edges, half each.
  @Test
  fun testPerformanceCompleteGraph() =
    runBlocking<Unit> {
      // Graph illustration: https://en.wikipedia.org/wiki/Complete_graph
      // Number of edges will be O(nodesCount^2), that's why square root is used here
      val nodesCount = sqrt(1e4).toInt()
      // # edges = all pairs of annotation classes in both directions + fun to every annotation
      // class
      val totalEdges = (nodesCount - 1) * (nodesCount - 2) + nodesCount - 1
      val fileContent = buildString {
        repeat(nodesCount) { i ->
          appendLine("")
          repeat(nodesCount) { j -> if (j > 0 && i != j) appendLine("@node$j") }
          if (i == 0) {
            appendLine("fun rootMethod(){}")
          } else {
            appendLine("annotation class node$i")
          }
        }
      }
      val psiFile = fixture.configureByText(KotlinFileType.INSTANCE, fileContent)
      val rootMethod = runReadAction {
        findAnnotations(project, psiFile.virtualFile, "node1")
          .mapNotNull { it.getContainingUMethodAnnotatedWith(it.qualifiedName!!) }
          .single()
      }
      val traverseResult =
        withTimeout(15.seconds) {
          async { annotationsGraph.traverse(listOf(rootMethod)).toList() }.await()
        }
      // Results are computed in post-order
      assertEquals(
        List(nodesCount - 1) { "${(nodesCount - it - 1)}" },
        traverseResult.filterIsInstance<String>(),
      )
      val edgesResult = traverseResult.filterIsInstance<DfsSubtreeEdges>().single()
      assertEquals(nodesCount - 1, edgesResult.treeEdgesCount)
      val nonTreeEdges = totalEdges - edgesResult.treeEdgesCount
      assertEquals(nonTreeEdges / 2, edgesResult.backEdgesCount)
      assertEquals(nonTreeEdges / 2, edgesResult.forwardEdgesCount)
      assertEquals(0, edgesResult.crossEdgesCount)
    }
}
