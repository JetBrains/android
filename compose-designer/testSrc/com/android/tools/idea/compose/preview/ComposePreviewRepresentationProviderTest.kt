/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tools.idea.compose.ComposeProjectRule
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.DumbServiceImpl
import com.intellij.psi.PsiFile
import com.intellij.util.ThrowableRunnable
import org.intellij.lang.annotations.Language
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private fun ComposePreviewRepresentationProvider.accept(file: PsiFile) =
  accept(file.project, file.virtualFile)

class ComposePreviewRepresentationProviderTest {
  @get:Rule
  val projectRule = ComposeProjectRule()
  private val project get() = projectRule.project
  private val fixture get() = projectRule.fixture

  @Before
  fun setUp() {
    StudioFlags.NELE_SOURCE_CODE_EDITOR.override(true)
  }

  @After
  fun tearDown() {
    StudioFlags.NELE_SOURCE_CODE_EDITOR.clearOverride()
  }

  @Test
  fun testAcceptFile() {
    val provider = ComposePreviewRepresentationProvider()

    @Language("kotlin")
    val noPreviewFile = fixture.addFileToProject("src/NoPreviews.kt", """
      import androidx.compose.Composable

      fun method() {
      }

      @Composable
      fun Preview2() {

      }
    """.trimIndent())

    @Language("kotlin")
    val previewFile = fixture.addFileToProject("src/Preview.kt", """
      import androidx.ui.tooling.preview.Preview
      import androidx.ui.tooling.preview.Configuration
      import androidx.compose.Composable

      @Preview
      @Composable
      fun PreviewTest() {

      }
    """.trimIndent())

    assertFalse(provider.accept(noPreviewFile))
    assertTrue(provider.accept(previewFile))

    // Exercise the representation initialization to make sure no exception is thrown
    ReadAction.compute<ComposePreviewRepresentation, Throwable> { provider.createRepresentation(previewFile) }
  }

  /**
   * This test ensures that we fail if we disable the preview.
   */
  @Test
  fun testDoesNotAcceptByDefault() {
    StudioFlags.COMPOSE_PREVIEW.override(false)
    val provider = ComposePreviewRepresentationProvider()

    @Language("kotlin")
    val previewFile = fixture.addFileToProject("src/Preview.kt", """
      import androidx.ui.tooling.preview.Preview
      import androidx.ui.tooling.preview.Configuration
      import androidx.compose.Composable

      @Preview
      @Composable
      fun PreviewTest() {

      }
    """.trimIndent())

    assertFalse(provider.accept(previewFile))
  }

  /**
   * [ComposeFileEditorProvider#accept] might be called on dumb mode. Make sure that we do not run any smart mode operations.
   */
  @Test
  fun testAcceptOnDumbMode() {
    val provider = ComposePreviewRepresentationProvider()

    @Language("kotlin")
    val previewFile = fixture.addFileToProject("src/Preview.kt", """
      import androidx.ui.tooling.preview.Preview
      import androidx.compose.Composable

      @Preview
      @Composable
      fun PreviewTest() {

      }
    """.trimIndent())

    WriteAction.runAndWait(ThrowableRunnable<Exception> {
      DumbServiceImpl.getInstance(project).isDumb = true
    })
    try {
      assertTrue(provider.accept(previewFile))
    }
    finally {
      WriteAction.runAndWait(ThrowableRunnable<Exception> {
        DumbServiceImpl.getInstance(project).isDumb = false
      })
    }
  }

  @Test
  fun testOnlyAcceptKotlinFiles() {
    val provider = ComposePreviewRepresentationProvider()

    @Language("java")
    val previewFile = fixture.addFileToProject("src/KOnly.java", """
      import androidx.ui.tooling.preview.Preview;
      import androidx.compose.Composable;

      public class KOnly {
        @Preview
        @Composable
        public void PreviewTest() {
        }
      }
    """.trimIndent())

    assertFalse(provider.accept(previewFile))
  }
}