/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.compose.preview

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.compose.ComposeProjectRule
import com.android.tools.idea.testing.ApplicationServiceRule
import com.android.tools.idea.testing.addFileToProjectAndInvalidate
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreferredVisibility
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.RuleChain
import kotlinx.coroutines.runBlocking
import org.jetbrains.android.uipreview.AndroidEditorSettings
import org.jetbrains.android.uipreview.AndroidEditorSettings.EditorMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.spy
import kotlin.test.assertFalse

class ComposePreviewRepresentationProviderTest {
  private val projectRule = ComposeProjectRule()

  private val androidEditorSettings = AndroidEditorSettings()

  @get:Rule
  val rule =
    RuleChain(
      projectRule,
      ApplicationServiceRule(AndroidEditorSettings::class.java, androidEditorSettings),
    )

  private val project
    get() = projectRule.project

  private val fixture
    get() = projectRule.fixture

  private val previewProvider = ComposePreviewRepresentationProvider {
    AnnotationFilePreviewElementFinder
  }

  @Test
  fun testDefaultLayout_withPreview() = runBlocking {
    @Suppress("TestFunctionName")
    val file =
      fixture.addFileToProjectAndInvalidate(
        "Preview.kt",
        // language=kotlin
        """
        import androidx.compose.ui.tooling.preview.Devices
        import androidx.compose.ui.tooling.preview.Preview
        import androidx.compose.runtime.Composable

        @Composable
        @Preview
        fun Preview1() {
        }

        @Composable
        @Preview(name = "preview2", apiLevel = 12, group = "groupA", showBackground = true)
        fun Preview2() {
        }
      """
          .trimIndent(),
      )

    assertTrue(previewProvider.accept(project, file))
    assertEquals(PreferredVisibility.SPLIT, file.getPreferredVisibility())

    androidEditorSettings.globalState.preferredKotlinEditorMode = EditorMode.CODE
    androidEditorSettings.globalState.showSplitViewForPreviewFiles = false
    assertEquals(PreferredVisibility.HIDDEN, file.getPreferredVisibility())

    androidEditorSettings.globalState.preferredKotlinEditorMode = EditorMode.DESIGN
    androidEditorSettings.globalState.showSplitViewForPreviewFiles = false
    assertEquals(PreferredVisibility.FULL, file.getPreferredVisibility())

    androidEditorSettings.globalState.preferredKotlinEditorMode = EditorMode.CODE
    androidEditorSettings.globalState.showSplitViewForPreviewFiles = true
    assertEquals(PreferredVisibility.SPLIT, file.getPreferredVisibility())

    androidEditorSettings.globalState.preferredKotlinEditorMode = EditorMode.SPLIT
    androidEditorSettings.globalState.showSplitViewForPreviewFiles = true
    assertEquals(PreferredVisibility.SPLIT, file.getPreferredVisibility())

    androidEditorSettings.globalState.preferredKotlinEditorMode = EditorMode.DESIGN
    androidEditorSettings.globalState.showSplitViewForPreviewFiles = true
    assertEquals(PreferredVisibility.SPLIT, file.getPreferredVisibility())
  }

  @Test
  fun testDefaultLayout_composable() = runBlocking {
    @Suppress("TestFunctionName")
    val file =
      fixture.addFileToProjectAndInvalidate(
        "Composable.kt",
        // language=kotlin
        """
        import androidx.compose.ui.tooling.preview.Devices
        import androidx.compose.ui.tooling.preview.Preview
        import androidx.compose.runtime.Composable

        @Composable
        fun Composable1() {
        }

        @Composable
        fun Composable2() {
        }
      """
          .trimIndent(),
      )

    assertTrue(previewProvider.accept(project, file))
    assertEquals(PreferredVisibility.HIDDEN, file.getPreferredVisibility())

    androidEditorSettings.globalState.preferredKotlinEditorMode = EditorMode.CODE
    assertEquals(PreferredVisibility.HIDDEN, file.getPreferredVisibility())

    androidEditorSettings.globalState.preferredKotlinEditorMode = EditorMode.SPLIT
    assertEquals(PreferredVisibility.SPLIT, file.getPreferredVisibility())

    androidEditorSettings.globalState.preferredKotlinEditorMode = EditorMode.DESIGN
    assertEquals(PreferredVisibility.FULL, file.getPreferredVisibility())
  }

  @Test
  fun testDefaultLayout_kotlin() = runBlocking {
    val file =
      fixture.addFileToProjectAndInvalidate(
        "Kotlin.kt",
        // language=kotlin
        """

        fun helloMethod() {
        }
      """
          .trimIndent(),
      )

    assertTrue(previewProvider.accept(project, file))
    assertEquals(PreferredVisibility.HIDDEN, file.getPreferredVisibility())

    androidEditorSettings.globalState.showSplitViewForPreviewFiles = false

    androidEditorSettings.globalState.preferredKotlinEditorMode = EditorMode.CODE
    assertEquals(PreferredVisibility.HIDDEN, file.getPreferredVisibility())

    androidEditorSettings.globalState.preferredKotlinEditorMode = EditorMode.SPLIT
    assertEquals(PreferredVisibility.SPLIT, file.getPreferredVisibility())

    androidEditorSettings.globalState.preferredKotlinEditorMode = EditorMode.DESIGN
    assertEquals(PreferredVisibility.FULL, file.getPreferredVisibility())

    androidEditorSettings.globalState.showSplitViewForPreviewFiles = true

    androidEditorSettings.globalState.preferredKotlinEditorMode = EditorMode.CODE
    assertEquals(PreferredVisibility.HIDDEN, file.getPreferredVisibility())

    androidEditorSettings.globalState.preferredKotlinEditorMode = EditorMode.DESIGN
    assertEquals(PreferredVisibility.FULL, file.getPreferredVisibility())
  }

  @Test
  fun accept_javaFile_notAccepted() = runBlocking {
    val file =
      fixture.addFileToProjectAndInvalidate(
        "Java.java",
        // language=java
        """

        class Java {
        }
      """
          .trimIndent(),
      )

    assertFalse(previewProvider.accept(project, file))
  }

  @Test
  fun accept_fileInLibrary_notAccepted() = runBlocking {
    val mockProjectRootManager = spy(ProjectRootManager.getInstance(project))
    val mockProjectFileIndex = mock<ProjectFileIndex>()
    val mockPsiFile = mock<PsiFile>()
    whenever(mockProjectRootManager.fileIndex).thenReturn(mockProjectFileIndex)
    whenever(mockPsiFile.virtualFile).thenReturn(LightVirtualFile())
    whenever(mockProjectFileIndex.isInLibrary(any())).thenReturn(true)

    assertFalse(previewProvider.accept(project, mockPsiFile))
  }

  private fun PsiFile.getPreferredVisibility() =
    getRepresentationForFile(this, project, fixture, previewProvider).preferredInitialVisibility
}
