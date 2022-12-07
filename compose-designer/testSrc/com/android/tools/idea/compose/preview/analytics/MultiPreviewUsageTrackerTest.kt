/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.analytics

import com.android.flags.junit.FlagRule
import com.android.tools.compose.COMPOSABLE_ANNOTATION_NAME
import com.android.tools.compose.COMPOSABLE_FQ_NAMES
import com.android.tools.idea.annotations.findAnnotatedMethodsValues
import com.android.tools.idea.compose.ComposeProjectRule
import com.android.tools.idea.compose.preview.AnnotationFilePreviewElementFinder.getPreviewNodes
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.addFileToProjectAndInvalidate
import com.android.tools.idea.util.androidFacet
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.ComposeMultiPreviewEvent
import com.intellij.openapi.vfs.VirtualFile
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.junit.Rule
import org.junit.Test

private const val COMPOSABLE_ANNOTATION_FQN = "androidx.compose.runtime"
private const val PREVIEW_TOOLING_PACKAGE = "androidx.compose.ui.tooling.preview"

class MultiPreviewUsageTrackerTest {

  @get:Rule val multiPreviewRule = FlagRule(StudioFlags.COMPOSE_MULTIPREVIEW, true)

  @get:Rule
  val projectRule =
    ComposeProjectRule(
      previewAnnotationPackage = PREVIEW_TOOLING_PACKAGE,
      composableAnnotationPackage = COMPOSABLE_ANNOTATION_FQN
    )
  private val project
    get() = projectRule.project
  private val fixture
    get() = projectRule.fixture

  private val baseFileContent =
    """
      package a

      import $PREVIEW_TOOLING_PACKAGE.Preview
      import $COMPOSABLE_ANNOTATION_FQN.Composable

      annotation class EmptyAnnotation

      @Preview // first preview
      @EmptyAnnotation
      annotation class MyAnnotation

      @MyAnnotation
      @Composable
      @Preview // second preview
      fun MyFun(){
      }
      """

  @Test
  fun testLogEvent_empty() {
    val multiPreviewUsageTracker = MultiPreviewUsageTracker.getInstance(null)
    val multiPreviewEvent = MultiPreviewEvent(listOf(), "fileName")
    val androidStudioEvent = multiPreviewUsageTracker.logEvent(multiPreviewEvent)

    assertEquals(AndroidStudioEvent.EventKind.COMPOSE_MULTI_PREVIEW, androidStudioEvent.kind)
    assertEquals(0, androidStudioEvent.composeMultiPreviewEvent.multiPreviewNodesList.size)
  }

  /**
   * For more details about the graph used in this test, see the README at
   * [com.android.tools.idea.compose.preview.analytics]
   */
  @Test
  fun testLogEvent_tree() { // The MultiPreview graph has a tree structure
    val psiFile =
      fixture.addFileToProjectAndInvalidate(
        "src/Test.kt",
        // language=kotlin
        """
        import $PREVIEW_TOOLING_PACKAGE.Preview
        import $COMPOSABLE_ANNOTATION_FQN.Composable

        ${
        buildMultiPreviewGraph(1, 7,
                               listOf(Pair(0, 1), Pair(1, 2), Pair(1, 3), Pair(2, 4), Pair(2, 5), Pair(3, 6), Pair(3, 7)),
                               setOf(0, 2, 4, 6))
      }
      """.trimIndent()
      )
    val multiPreviewUsageTracker = runReadAction {
      MultiPreviewUsageTracker.getInstance(psiFile.androidFacet)
    }
    val multiPreviewEvent = MultiPreviewEvent(getPreviewNodes(psiFile.virtualFile), "fileName")
    val androidStudioEvent = multiPreviewUsageTracker.logEvent(multiPreviewEvent)
    assertEquals(AndroidStudioEvent.EventKind.COMPOSE_MULTI_PREVIEW, androidStudioEvent.kind)

    // Check types
    val nodes = androidStudioEvent.composeMultiPreviewEvent.multiPreviewNodesList
    assertEquals(6, nodes.size)
    assertEquals(
      1,
      nodes.count {
        it.nodeType ==
          ComposeMultiPreviewEvent.ComposeMultiPreviewNodeInfo.NodeType
            .ROOT_COMPOSABLE_FUNCTION_NODE
      }
    )
    assertEquals(
      5,
      nodes.count {
        it.nodeType ==
          ComposeMultiPreviewEvent.ComposeMultiPreviewNodeInfo.NodeType.MULTIPREVIEW_NODE
      }
    )

    // Check counters (if root counters are ok, all other counters are probably ok)
    val rootNode =
      nodes.single {
        it.nodeType ==
          ComposeMultiPreviewEvent.ComposeMultiPreviewNodeInfo.NodeType
            .ROOT_COMPOSABLE_FUNCTION_NODE
      }
    assertEquals(1, rootNode.previewChildsCount)
    assertEquals(1, rootNode.multiPreviewChildsCount)
    assertEquals(4, rootNode.subtreePreviewsCount)
    assertEquals(5, rootNode.subtreeMultiPreviewsCount)
    assertEquals(2, rootNode.subtreeUselessNodesCount)

    // Check depth levels
    assertEquals(listOf(0, 1, 2, 2, 3, 3), nodes.map { it.depthLevel }.sorted())

    // Check composable ids
    assertTrue(nodes.all { it.hasAnonymizedComposableId() })
    assertEquals(1, nodes.map { it.anonymizedComposableId }.distinct().size)
  }

  /**
   * For more details about the graph used in this test, see the README at
   * [com.android.tools.idea.compose.preview.analytics]
   */
  @Test
  fun testLogEvent_DAG() { // The MultiPreview graph is a direct acyclic graph
    val psiFile =
      fixture.addFileToProjectAndInvalidate(
        "src/Test.kt",
        // language=kotlin
        """
        import $PREVIEW_TOOLING_PACKAGE.Preview
        import $COMPOSABLE_ANNOTATION_FQN.Composable

        ${
        buildMultiPreviewGraph(2, 11,
                               listOf(Pair(0, 2), Pair(0, 3), Pair(1, 4), Pair(1, 5), Pair(2, 6), Pair(2, 10), Pair(3, 7),
                                      Pair(4, 7), Pair(5, 8), Pair(5, 9), Pair(6, 10), Pair(7, 11), Pair(8, 12), Pair(9, 12)),
                               setOf(0, 2, 4, 6, 12))
      }
      """.trimIndent()
      )
    val multiPreviewUsageTracker = runReadAction {
      MultiPreviewUsageTracker.getInstance(psiFile.androidFacet)
    }
    val multiPreviewEvent = MultiPreviewEvent(getPreviewNodes(psiFile.virtualFile), "fileName")
    val androidStudioEvent = multiPreviewUsageTracker.logEvent(multiPreviewEvent)
    assertEquals(AndroidStudioEvent.EventKind.COMPOSE_MULTI_PREVIEW, androidStudioEvent.kind)

    // Check types
    val nodes = androidStudioEvent.composeMultiPreviewEvent.multiPreviewNodesList
    assertEquals(8, nodes.size)
    assertEquals(
      2,
      nodes.count {
        it.nodeType ==
          ComposeMultiPreviewEvent.ComposeMultiPreviewNodeInfo.NodeType
            .ROOT_COMPOSABLE_FUNCTION_NODE
      }
    )
    assertEquals(
      6,
      nodes.count {
        it.nodeType ==
          ComposeMultiPreviewEvent.ComposeMultiPreviewNodeInfo.NodeType.MULTIPREVIEW_NODE
      }
    )

    // Check counters (if roots counters are ok, all other counters are probably ok)
    val rootNode0 =
      nodes.first {
        it.nodeType ==
          ComposeMultiPreviewEvent.ComposeMultiPreviewNodeInfo.NodeType
            .ROOT_COMPOSABLE_FUNCTION_NODE
      }
    assertEquals(1, rootNode0.previewChildsCount)
    assertEquals(1, rootNode0.multiPreviewChildsCount)
    assertEquals(3, rootNode0.subtreePreviewsCount)
    assertEquals(2, rootNode0.subtreeMultiPreviewsCount)
    assertEquals(4, rootNode0.subtreeUselessNodesCount)
    val rootNode1 =
      nodes.last {
        it.nodeType ==
          ComposeMultiPreviewEvent.ComposeMultiPreviewNodeInfo.NodeType
            .ROOT_COMPOSABLE_FUNCTION_NODE
      }
    assertEquals(0, rootNode1.previewChildsCount)
    assertEquals(2, rootNode1.multiPreviewChildsCount)
    assertEquals(2, rootNode1.subtreePreviewsCount)
    assertEquals(4, rootNode1.subtreeMultiPreviewsCount)
    assertEquals(3, rootNode1.subtreeUselessNodesCount)

    // Check depth levels
    assertEquals(listOf(0, 0, 1, 1, 1, 2, 2, 3), nodes.map { it.depthLevel }.sorted())

    // Check composable ids
    assertTrue(nodes.all { it.hasAnonymizedComposableId() })
    val idsList = nodes.map { it.anonymizedComposableId }.distinct()
    assertEquals(2, idsList.size)
    assertEquals(
      listOf(3, 5),
      listOf(
          nodes.count { it.anonymizedComposableId == idsList[0] },
          nodes.count { it.anonymizedComposableId == idsList[1] }
        )
        .sorted()
    )
  }

  /**
   * For more details about the graph used in this test, see the README at
   * [com.android.tools.idea.compose.preview.analytics]
   */
  @Test
  fun testLogEvent_withCycle() { // The MultiPreview graph has a cycle
    val psiFile =
      fixture.addFileToProjectAndInvalidate(
        "src/Test.kt",
        // language=kotlin
        """
        import $PREVIEW_TOOLING_PACKAGE.Preview
        import $COMPOSABLE_ANNOTATION_FQN.Composable

        ${
        buildMultiPreviewGraph(2, 11,
                               listOf(Pair(0, 2), Pair(0, 3), Pair(1, 4), Pair(1, 5), Pair(2, 6), Pair(2, 10),
                                      Pair(3, 7), Pair(4, 7), Pair(5, 8), Pair(5, 9), Pair(6, 10),
                                      Pair(7, 11), Pair(8, 12), Pair(9, 12), Pair(11, 3), Pair(11, 4)),
                               setOf(0, 2, 4, 6, 12))
      }
      """.trimIndent()
      )
    val multiPreviewUsageTracker = runReadAction {
      MultiPreviewUsageTracker.getInstance(psiFile.androidFacet)
    }
    val multiPreviewEvent = MultiPreviewEvent(getPreviewNodes(psiFile.virtualFile), "fileName")
    val androidStudioEvent = multiPreviewUsageTracker.logEvent(multiPreviewEvent)
    assertEquals(AndroidStudioEvent.EventKind.COMPOSE_MULTI_PREVIEW, androidStudioEvent.kind)

    // Check types
    val nodes = androidStudioEvent.composeMultiPreviewEvent.multiPreviewNodesList
    assertEquals(12, nodes.size)
    assertEquals(
      2,
      nodes.count {
        it.nodeType ==
          ComposeMultiPreviewEvent.ComposeMultiPreviewNodeInfo.NodeType
            .ROOT_COMPOSABLE_FUNCTION_NODE
      }
    )
    assertEquals(
      10,
      nodes.count {
        it.nodeType ==
          ComposeMultiPreviewEvent.ComposeMultiPreviewNodeInfo.NodeType.MULTIPREVIEW_NODE
      }
    )

    // Check counters (if roots counters are ok, all other counters are probably ok)
    val rootNode0 =
      nodes.first {
        it.nodeType ==
          ComposeMultiPreviewEvent.ComposeMultiPreviewNodeInfo.NodeType
            .ROOT_COMPOSABLE_FUNCTION_NODE
      }
    assertEquals(1, rootNode0.previewChildsCount)
    assertEquals(2, rootNode0.multiPreviewChildsCount)
    assertEquals(4, rootNode0.subtreePreviewsCount)
    assertEquals(6, rootNode0.subtreeMultiPreviewsCount)
    assertEquals(1, rootNode0.subtreeUselessNodesCount)
    val rootNode1 =
      nodes.last {
        it.nodeType ==
          ComposeMultiPreviewEvent.ComposeMultiPreviewNodeInfo.NodeType
            .ROOT_COMPOSABLE_FUNCTION_NODE
      }
    assertEquals(0, rootNode1.previewChildsCount)
    assertEquals(2, rootNode1.multiPreviewChildsCount)
    assertEquals(2, rootNode1.subtreePreviewsCount)
    assertEquals(4, rootNode1.subtreeMultiPreviewsCount)
    assertEquals(4, rootNode1.subtreeUselessNodesCount)

    // Check depth levels
    assertEquals(listOf(0, 0, 1, 1, 1, 1, 2, 2, 2, 3, 3, 4), nodes.map { it.depthLevel }.sorted())

    // Check composable ids
    assertTrue(nodes.all { it.hasAnonymizedComposableId() })
    val idsList = nodes.map { it.anonymizedComposableId }.distinct()
    assertEquals(2, idsList.size)
    assertEquals(
      listOf(5, 7),
      listOf(
          nodes.count { it.anonymizedComposableId == idsList[0] },
          nodes.count { it.anonymizedComposableId == idsList[1] }
        )
        .sorted()
    )
  }

  @Test
  fun testGraphHashCode_different() {
    val multiPreviewEvents =
      listOf(
          baseFileContent,
          baseFileContent
            .replace("package a", "package b")
            .replace("@Preview // first preview", ""),
          baseFileContent.replace("package a", "package c").replace("@EmptyAnnotation", ""),
          baseFileContent.replace("package a", "package d").replace("@MyAnnotation", ""),
          baseFileContent.replace("package a", "package e").replace("@Composable", ""),
          baseFileContent
            .replace("package a", "package f")
            .replace("@Preview // second preview", ""),
        )
        .mapIndexed { idx: Int, testFileContent: String ->
          addFileAndCreateMultiPreviewEvent("testFile$idx", testFileContent)
        }

    // Each annotation in the base file is part of the MultiPreview graph, so all the files
    // generated by removing each of them individually should generate a different hashCode
    assertEquals(6, multiPreviewEvents.map { it.hashCode() }.distinct().size)
  }

  @Test
  fun testGraphHashCode_extraPreviewCode() {
    val baseEvent = addFileAndCreateMultiPreviewEvent("baseFile", baseFileContent)
    val testFileContent = baseFileContent.replace("package a", "package b")
    val extraCode =
      """
      @Composable
      @Preview
      fun otherPreviewFun() {
      }
    """
    val withExtraCodeEvent =
      addFileAndCreateMultiPreviewEvent("testFile", testFileContent + extraCode)
    // The extra code affects the MultiPreview graph and the hashCode
    assertNotEquals(baseEvent.hashCode(), withExtraCodeEvent.hashCode())
  }

  @Test
  fun testGraphHashCode_extraNotPreviewCode() {
    val baseEvent = addFileAndCreateMultiPreviewEvent("baseFile", baseFileContent)
    val testFileContent = baseFileContent.replace("package a", "package b")
    val extraCode =
      """
      annotation class NotPreviewRelatedAnnotation

      fun otherNotPreviewFun() {
      }
    """
    val withExtraCodeEvent =
      addFileAndCreateMultiPreviewEvent("testFile", testFileContent + extraCode)
    // Code not related with the MultiPreview graph shouldn't affect the hashCode
    assertEquals(baseEvent.hashCode(), withExtraCodeEvent.hashCode())
  }

  @Test
  fun testGraphHashCode_annotationName() {
    val baseEvent = addFileAndCreateMultiPreviewEvent("baseFile", baseFileContent)
    val testFileContent =
      baseFileContent
        .replace("package a", "package b")
        .replace("MyAnnotation", "MyDifferentAnnotation")
    val withExtraCodeEvent = addFileAndCreateMultiPreviewEvent("testFile", testFileContent)
    // The annotation names shouldn't affect the hashCode
    assertEquals(baseEvent.hashCode(), withExtraCodeEvent.hashCode())
  }

  @Test
  fun testGraphHashCode_previewParameter() {
    val baseEvent = addFileAndCreateMultiPreviewEvent("baseFile", baseFileContent)
    val testFileContent =
      baseFileContent
        .replace("package a", "package b")
        .replaceFirst("@Preview", "@Preview(name = \"nameParam\")")
    val withExtraCodeEvent = addFileAndCreateMultiPreviewEvent("testFile", testFileContent)
    // Changes in Preview parameters shouldn't affect the hashCode
    assertEquals(baseEvent.hashCode(), withExtraCodeEvent.hashCode())
  }

  @Test
  fun testGraphHashCode_codeOrder() {
    val baseEvent = addFileAndCreateMultiPreviewEvent("baseFile", baseFileContent)
    var testFileContent =
      """
      package b // use a different package that the one baseFile uses

      import $PREVIEW_TOOLING_PACKAGE.Preview
      import $COMPOSABLE_ANNOTATION_FQN.Composable

      @Preview // change annotation order
      @MyAnnotation
      @Composable
      fun MyFun(){
      }

      // change declaration order

      annotation class EmptyAnnotation

      @EmptyAnnotation
      @Preview // change annotation order
      annotation class MyAnnotation
      """
    val differentOrderEvent = addFileAndCreateMultiPreviewEvent("testFile", testFileContent)
    // The annotation and declaration order shouldn't affect the hashCode as long as the DFS tree
    // remains the same
    assertEquals(baseEvent.hashCode(), differentOrderEvent.hashCode())
  }

  private fun addFileAndCreateMultiPreviewEvent(
    fileName: String,
    fileContent: String
  ): MultiPreviewEvent {
    val vFile =
      fixture.addFileToProjectAndInvalidate("src/$fileName.kt", fileContent.trimIndent())
        .virtualFile

    // Don't use the real file nor composable names, so that they don't affect the hashCode
    return MultiPreviewEvent(clearComposableFqnData(getPreviewNodes(vFile)), "fileName")
  }

  private fun clearComposableFqnData(nodes: List<MultiPreviewNode>): List<MultiPreviewNode> {
    nodes.forEach { it.nodeInfo.withComposableFqn("") }
    return nodes
  }

  private fun getPreviewNodes(vFile: VirtualFile) = runBlocking {
    findAnnotatedMethodsValues(project, vFile, COMPOSABLE_FQ_NAMES, COMPOSABLE_ANNOTATION_NAME) {
        methods ->
        getPreviewNodes(methods, true).asSequence()
      }
      .filterIsInstance<MultiPreviewNode>()
  }
}

/**
 * @param nComposable number of composable methods (their ids will be the numbers in the range [0,
 * nComposable))
 * @param nAnnotClasses number of annotation classes (their ids will be the numbers in the range
 * [nComposable, nComposable+nAnnotClasses))
 * @param edges list of directed edges from [Pair.first] to [Pair.second], where the elements of
 * each Pair must correspond to a
 * ```
 *        valid node id according to the values of nComposable and nAnnotClasses
 * @param idsWithPreview
 * ```
 * set of ids corresponding to the Composable methods and annotation classes that should be
 * annotated with Preview
 */
private fun buildMultiPreviewGraph(
  nComposable: Int,
  nAnnotClasses: Int,
  edges: List<Pair<Int, Int>>,
  idsWithPreview: Set<Int>
): String {
  // validate parameters
  assertTrue(nComposable >= 0 && nAnnotClasses >= 0)
  assertTrue(edges.all { it.first >= 0 && it.first < nComposable + nAnnotClasses })
  assertTrue(edges.all { it.second >= nComposable && it.second < nComposable + nAnnotClasses })
  assertTrue(idsWithPreview.all { it >= 0 && it < nComposable + nAnnotClasses })

  val edgesFrom = mutableMapOf<Int, MutableList<Int>>()
  edges.forEach { edgesFrom.getOrPut(it.first) { mutableListOf() }.add(it.second) }

  return buildString {
    // add composable methods
    repeat(nComposable) { id ->
      this.appendLine("@Composable")
      if (idsWithPreview.contains(id)) this.appendLine("@Preview")
      edgesFrom[id]?.forEach { annotationId -> this.appendLine("@Node${annotationId}") }
      this.appendLine("fun Composable${id}(){}")
      this.appendLine("")
    }

    // add annotation classes
    repeat(nAnnotClasses) {
      val id = it + nComposable
      if (idsWithPreview.contains(id)) this.appendLine("@Preview")
      edgesFrom[id]?.forEach { annotationId -> this.appendLine("@Node${annotationId}") }
      this.appendLine("annotation class Node${id}")
      this.appendLine("")
    }
  }
}
