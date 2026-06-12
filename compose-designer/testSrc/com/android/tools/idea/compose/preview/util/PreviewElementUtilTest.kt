/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.util

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.preview.ComposePreviewElementInstance
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.PsiElement
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PreviewElementUtilTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun testEdgeNotImplementedAndNotImported() = runBlocking {
    val fileWithoutNavigationEvent =
      projectRule.fixture.addFileToProject(
        "src/MyFile.kt",
        """
        fun test() {
            // No edge constants
        }
        """
          .trimIndent(),
      )

    val mockPreview = mock<ComposePreviewElementInstance<PsiElement>>()
    whenever(mockPreview.previewBody).thenReturn(fileWithoutNavigationEvent)

    assertThat(mockPreview.isEdgeNavigationImplemented()).isFalse()
  }

  @Test
  fun testIsEdgeNavigationImplemented() = runBlocking {
    val fileWithEdge =
      projectRule.fixture.addFileToProject(
        "src/MyFile.kt",
        """
        import androidx.navigationevent.NavigationEvent

        fun test() {
            fun function(edge: Int){
                when(edge){
                  NavigationEvent.EDGE_RIGHT -> 30*2
                  NavigationEvent.EDGE_LEFT -> 2
                  else -> 1
                }
            }
        }
        """
          .trimIndent(),
      )

    val fileWithoutEdge =
      projectRule.fixture.addFileToProject(
        "src/MyOtherFile.kt",
        """
        import androidx.navigationevent.NavigationEvent
        fun test() { // No edge constants }
        """
          .trimIndent(),
      )

    val mockPreview = mock<ComposePreviewElementInstance<PsiElement>>()
    whenever(mockPreview.previewBody).thenReturn(fileWithEdge)

    assertThat(mockPreview.isEdgeNavigationImplemented()).isTrue()

    whenever(mockPreview.previewBody).thenReturn(fileWithoutEdge)
    assertThat(mockPreview.isEdgeNavigationImplemented()).isFalse()
  }

  @Test
  fun testIsEdgeNavigationImplemented_fallbackToPreviewElementDefinition() = runBlocking {
    val fileWithEdge =
      projectRule.fixture.addFileToProject(
        "src/MyFile2.kt",
        """
        import androidx.navigationevent.NavigationEvent

        fun test() {
              fun function(){
                  NavigationEvent.EDGE_RIGHT
              }
          }
        """
          .trimIndent(),
      )

    val fileWithoutEdge =
      projectRule.fixture.addFileToProject(
        "src/MyOtherFile2.kt",
        """
        import androidx.navigationevent.NavigationEvent
        fun test() { // No edge constants }
        """
          .trimIndent(),
      )

    val mockPreview = mock<ComposePreviewElementInstance<PsiElement>>()
    whenever(mockPreview.previewBody).thenReturn(null)
    whenever(mockPreview.previewElementDefinition).thenReturn(fileWithEdge)

    assertThat(mockPreview.isEdgeNavigationImplemented()).isTrue()

    whenever(mockPreview.previewElementDefinition).thenReturn(fileWithoutEdge)
    assertThat(mockPreview.isEdgeNavigationImplemented()).isFalse()
  }

  @Test
  fun testIsEdgeNavigationImplemented_qualifiedEdgeRightWithoutImport_isFalse() = runBlocking {
    val fileWithoutImport =
      projectRule.fixture.addFileToProject(
        "src/NoImportFile.kt",
        """
        package test
        // No import of NavigationEvent, but qualified EDGE_RIGHT constant!
        fun test() {
            fun function(){
                NavigationEvent.EDGE_RIGHT
            }
        }
        """
          .trimIndent(),
      )

    val mockPreview = mock<ComposePreviewElementInstance<PsiElement>>()
    whenever(mockPreview.previewBody).thenReturn(fileWithoutImport)

    assertThat(mockPreview.isEdgeNavigationImplemented()).isFalse()
  }

  @Test
  fun testIsEdgeNavigationImplemented_qualifiedEdgeNoneWithoutImport_isFalse() = runBlocking {
    val fileQualified =
      projectRule.fixture.addFileToProject(
        "src/QualifiedFile.kt",
        """
        package test
        // No import, but qualified EDGE_NONE constant!
        fun test() {
            fun function(){
                NavigationEvent.EDGE_NONE
            }
        }
        """
          .trimIndent(),
      )

    val mockPreview = mock<ComposePreviewElementInstance<PsiElement>>()
    whenever(mockPreview.previewBody).thenReturn(fileQualified)

    assertThat(mockPreview.isEdgeNavigationImplemented()).isFalse()
  }

  @Test
  fun testIsEdgeNavigationImplemented_fullyQualifiedWithoutImport_isTrue() = runBlocking {
    val fileFullyQualified =
      projectRule.fixture.addFileToProject(
        "src/FullyQualifiedFile.kt",
        """
        package test
        // No import, but fully qualified constant!
        fun test() {
            fun function(){
               androidx.navigationevent.NavigationEvent.EDGE_RIGHT
            }
        }
        """
          .trimIndent(),
      )

    val mockPreview = mock<ComposePreviewElementInstance<PsiElement>>()
    whenever(mockPreview.previewBody).thenReturn(fileFullyQualified)

    assertThat(mockPreview.isEdgeNavigationImplemented()).isTrue()
  }

  @Test
  fun testIsEdgeNavigationImplemented_inComments_isFalse() = runBlocking {
    val fileWithComment =
      projectRule.fixture.addFileToProject(
        "src/CommentFile.kt",
        """
        package test
        import androidx.navigationevent.NavigationEvent

        /** See [EDGE_RIGHT] */
        fun test() {
            // NavigationEvent.EDGE_RIGHT
            /* EDGE_LEFT */
            /** EDGE_NONE */
        }
        """
          .trimIndent(),
      )

    val mockPreview = mock<ComposePreviewElementInstance<PsiElement>>()
    whenever(mockPreview.previewBody).thenReturn(fileWithComment)

    assertThat(mockPreview.isEdgeNavigationImplemented()).isFalse()
  }

  @Test
  fun testIsEdgeNavigationImplemented_staticImport_isTrue() = runBlocking {
    val fileWithStrings =
      projectRule.fixture.addFileToProject(
        "src/StringFile.kt",
        """
        package test
        import androidx.navigationevent.NavigationEvent.EDGE_RIGHT
        import androidx.navigationevent.NavigationEvent

        fun test() {
            EDGE_RIGHT
            NavigationEvent.EDGE_LEFT
        }
        """
          .trimIndent(),
      )

    val mockPreview = mock<ComposePreviewElementInstance<PsiElement>>()
    whenever(mockPreview.previewBody).thenReturn(fileWithStrings)

    assertThat(mockPreview.isEdgeNavigationImplemented()).isTrue()
  }

  @Test
  fun testIsEdgeNavigationImplemented_substringIdentifiers_isFalse() = runBlocking {
    val fileWithSubstrings =
      projectRule.fixture.addFileToProject(
        "src/SubstringFile.kt",
        """
        package test
        import androidx.navigationevent.NavigationEvent
        fun test() {
           EDGE_RIGHT_ARROW
           CORNER_EDGE_RIGHT
        }
        """
          .trimIndent(),
      )

    val mockPreview = mock<ComposePreviewElementInstance<PsiElement>>()
    whenever(mockPreview.previewBody).thenReturn(fileWithSubstrings)

    assertThat(mockPreview.isEdgeNavigationImplemented()).isFalse()
  }
}
