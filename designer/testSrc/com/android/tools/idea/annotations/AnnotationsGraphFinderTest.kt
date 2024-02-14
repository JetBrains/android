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
package com.android.tools.idea.annotations

import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.evaluateString
import org.jetbrains.uast.tryResolve
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AnnotationsGraphFinderTest {
  @get:Rule val projectRule: AndroidProjectRule = AndroidProjectRule.inMemory()

  private val project
    get() = projectRule.project

  private val fixture
    get() = projectRule.fixture

  @Test
  fun testTraverse_backEdge() {
    @Language("kotlin")
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
    val rootMethod = psiFile.getMethodAnnotatedBy("node0")

    val annotations =
      rootMethod
        .findAllAnnotationsInGraph { runReadAction { it.qualifiedName == "node3" } }
        .toList()
    assertThat(annotations.map { (it.element as UAnnotation).qualifiedName })
      .containsExactly("node3")
  }

  @Test
  fun testTraverse_forwardEdge() {
    @Language("kotlin")
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
    val rootMethod = psiFile.getMethodAnnotatedBy("node0")

    val annotations =
      rootMethod
        .findAllAnnotationsInGraph { runReadAction { it.qualifiedName == "node3" } }
        .toList()
    assertThat(annotations.map { (it.element as UAnnotation).qualifiedName })
      .containsExactly("node3", "node3")
  }

  @Test
  fun testTraverse_crossEdge() {
    @Language("kotlin")
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
    val rootMethod = psiFile.getMethodAnnotatedBy("node0")

    val annotations =
      rootMethod
        .findAllAnnotationsInGraph { runReadAction { it.qualifiedName == "node3" } }
        .toList()
    assertThat(annotations.map { (it.element as UAnnotation).qualifiedName })
      .containsExactly("node3", "node3")
  }

  @Test
  fun testTraverse_differentParameters() {
    @Language("kotlin")
    val fileContent =
      """
      // Graph illustration:
      // rootMethod --> 0 --> 1 --> 2 --> 3(name"1")
      //                      '---> 3(name="2")

      @node2
      @node3(name="2")
      annotation class node1

      @node3(name="1")
      annotation class node2

      annotation class node3(name: String)

      @node3
      annotation class node4

      @node1
      annotation class node0

      @node0
      fun rootMethod(){}
    """
        .trimIndent()

    val psiFile = fixture.configureByText(KotlinFileType.INSTANCE, fileContent)
    val rootMethod = psiFile.getMethodAnnotatedBy("node0")

    val annotations =
      rootMethod
        .findAllAnnotationsInGraph { runReadAction { it.qualifiedName == "node3" } }
        .toList()
    runReadAction {
      assertThat(
          annotations
            .map { it.element as UAnnotation }
            .map { it.qualifiedName to it.findAttributeValue("name")?.evaluateString() }
        )
        .containsExactly("node3" to "1", "node3" to "2")
    }
  }

  @Test
  fun testFindMultiPreviewsExample() {
    @Language("kotlin")
    val fileContent =
      """
      // This annotation class is the "Preview" in this context (see TestMultiPreviewNodeInfo.isPreview)
      annotation class MyTestPreview

      // Using this annotation shouldn't have any effect
      annotation class EmptyAnnotation

      @MyTestPreview
      annotation class NotReachableFromSourceElements

      @MyTestPreview
      @EmptyAnnotation
      annotation class Intermediate1 // with 1 direct Preview

      @MyTestPreview
      @MyTestPreview
      annotation class Intermediate2 // with 2 direct Previews

      @MyTestPreview // direct preview
      @Intermediate1
      @Intermediate2
      @EmptyAnnotation
      fun rootMethod(){}
    """
        .trimIndent()

    val psiFile = fixture.configureByText(KotlinFileType.INSTANCE, fileContent)
    val rootMethod = psiFile.getMethodAnnotatedBy("Intermediate1")

    val previews =
      rootMethod
        .findAllAnnotationsInGraph {
          runReadAction { (it.tryResolve() as PsiClass).name == "MyTestPreview" }
        }
        .toList()

    assertThat(previews).hasSize(4)

    val topLevelAnnotationsToDepth =
      previews.map {
        val topLevelAnnotationName = runReadAction {
          (it.subtreeInfo?.topLevelAnnotation?.tryResolve() as PsiClass).name
        }
        val depth = it.subtreeInfo?.depth ?: -1
        topLevelAnnotationName to depth
      }
    assertEquals(
      listOf(
        "Intermediate1" to 2,
        "Intermediate2" to 2,
        "Intermediate2" to 2,
        "MyTestPreview" to 1,
      ),
      topLevelAnnotationsToDepth.sortedBy { it.first },
    )
  }

  @Test
  fun testOnTraversalIsInvokedOnAllTraversedNodesPostOrder() {
    @Language("kotlin")
    val fileContent =
      """
      // This annotation class is the "Preview" in this context (see TestMultiPreviewNodeInfo.isPreview)
      annotation class MyTestPreview

      // Using this annotation shouldn't have any effect
      annotation class EmptyAnnotation

      @MyTestPreview
      annotation class NotReachableFromSourceElements

      @MyTestPreview
      @EmptyAnnotation
      annotation class Intermediate1 // with 1 direct Preview

      @MyTestPreview
      @MyTestPreview
      annotation class Intermediate2 // with 2 direct Previews

      @MyTestPreview // direct preview
      @Intermediate1
      @Intermediate2
      @EmptyAnnotation
      fun rootMethod(){}
    """
        .trimIndent()

    val psiFile = fixture.configureByText(KotlinFileType.INSTANCE, fileContent)
    val rootMethod = psiFile.getMethodAnnotatedBy("Intermediate1")

    val traversedNodes = mutableListOf<NodeInfo<UAnnotationSubtreeInfo>>()
    rootMethod
      .findAllAnnotationsInGraph(onTraversal = { traversedNodes += it }) {
        runReadAction { (it.tryResolve() as PsiClass).name == "MyTestPreview" }
      }
      .toList()

    val traversedNodeNames =
      traversedNodes.mapNotNull { runReadAction { (it.element.tryResolve() as? PsiClass)?.name } }
    // the order should be post-order
    assertEquals(
      //
      listOf(
        "MyTestPreview",
        "MyTestPreview",
        "EmptyAnnotation",
        "Intermediate1",
        "MyTestPreview",
        "MyTestPreview",
        "Intermediate2",
      ),
      traversedNodeNames,
    )
  }

  private fun PsiFile.getMethodAnnotatedBy(annotationShortName: String) = runReadAction {
    findAnnotations(project, virtualFile, annotationShortName).firstNotNullOf {
      it.qualifiedName?.let { qualifiedName -> it.getContainingUMethodAnnotatedWith(qualifiedName) }
    }
  }
}
