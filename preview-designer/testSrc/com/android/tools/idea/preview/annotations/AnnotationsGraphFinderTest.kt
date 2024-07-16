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
import com.android.tools.idea.testing.addFileToProjectAndInvalidate
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

  @Test
  fun testFindMultiPreviewsInAndroidx() {
    fixture.addFileToProjectAndInvalidate(
      "src/ThePreview.kt",
      // language=kotlin
      """
        package androidx

        // This annotation class is the "Preview" in this context (see TestMultiPreviewNodeInfo.isPreview)
        annotation class MyTestPreview
        """
        .trimIndent(),
    )
    // Add 3 files "simulating" them to be from androidx and containing a MultiPreview with a valid
    // package name.
    fixture.addFileToProjectAndInvalidate(
      "src/File1.kt",
      // language=kotlin
      """
        package androidx.preview.valid.somepackage

        import androidx.MyTestPreview

        @MyTestPreview
        annotation class MyValidAnnotation1
        """
        .trimIndent(),
    )
    fixture.addFileToProjectAndInvalidate(
      "src/File2.kt",
      // language=kotlin
      """
        package androidx.valid.preview.somepackage

        import androidx.MyTestPreview

        @MyTestPreview
        annotation class MyValidAnnotation2
        """
        .trimIndent(),
    )
    fixture.addFileToProjectAndInvalidate(
      "src/File3.kt",
      // language=kotlin
      """
        package androidx.valid.somepackage.preview

        import androidx.MyTestPreview

        @MyTestPreview
        annotation class MyValidAnnotation3
        """
        .trimIndent(),
    )

    // Add 3 files "simulating" them to be from androidx and containing a MultiPreview with an
    // invalid package name.
    fixture.addFileToProjectAndInvalidate(
      "src/File4.kt",
      // language=kotlin
      """
        // Doesn't contain preview
        package androidx.invalid.somepackage

        import androidx.MyTestPreview

        @MyTestPreview
        annotation class MyInvalidAnnotation1
        """
        .trimIndent(),
    )
    fixture.addFileToProjectAndInvalidate(
      "src/File5.kt",
      // language=kotlin
      """
        // 'mypreview' is not valid
        package androidx.invalid.mypreview.somepackage

        import androidx.MyTestPreview

        @MyTestPreview
        annotation class MyInvalidAnnotation2
        """
        .trimIndent(),
    )
    fixture.addFileToProjectAndInvalidate(
      "src/File6.kt",
      // language=kotlin
      """
        // 'pre.view' is not valid
        package androidx.invalid.pre.view.somepackage

        import androidx.MyTestPreview

        @MyTestPreview
        annotation class MyInvalidAnnotation3
        """
        .trimIndent(),
    )

    val previewTest =
      fixture.addFileToProjectAndInvalidate(
        "src/Test.kt",
        // language=kotlin
        """
        package com.example.test

        import androidx.MyTestPreview
        import androidx.preview.valid.somepackage.MyValidAnnotation1
        import androidx.valid.preview.somepackage.MyValidAnnotation2
        import androidx.valid.somepackage.preview.MyValidAnnotation3
        import androidx.invalid.somepackage.MyInvalidAnnotation1
        import androidx.invalid.mypreview.somepackage.MyInvalidAnnotation2
        import androidx.invalid.pre.view.somepackage.MyInvalidAnnotation3

        @MyTestPreview
        @MyValidAnnotation1
        @MyValidAnnotation2
        @MyInvalidAnnotation1
        @MyInvalidAnnotation2
        @MyValidAnnotation3
        @MyInvalidAnnotation3
        fun Preview1() {
        }
        """
          .trimIndent(),
      )

    val rootMethod = previewTest.getMethodAnnotatedBy("MyTestPreview")

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
        "MyValidAnnotation1",
        "MyTestPreview",
        "MyValidAnnotation2",
        "MyTestPreview",
        "MyValidAnnotation3",
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
