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

import com.android.tools.idea.compose.ComposeProjectRule
import com.android.tools.idea.testing.addFileToProjectAndInvalidate
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreferredVisibility
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ComposePreviewRepresentationProviderTest {
  @get:Rule
  val projectRule =
    ComposeProjectRule(
      previewAnnotationPackage = "androidx.compose.ui.tooling.preview",
      composableAnnotationPackage = "androidx.compose.runtime"
    )
  private val project
    get() = projectRule.project
  private val fixture
    get() = projectRule.fixture
  private val previewProvider = ComposePreviewRepresentationProvider {
    AnnotationFilePreviewElementFinder
  }

  @Test
  fun testDefaultLayout() {
    val previewFile =
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
      """.trimIndent()
      )
    val composableFile =
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
      """.trimIndent()
      )
    val kotlinFile =
      fixture.addFileToProjectAndInvalidate(
        "Kotlin.kt",
        // language=kotlin
        """
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.tooling.preview.Devices
        import androidx.compose.ui.tooling.preview.Preview

        fun helloMethod() {
        }
      """.trimIndent()
      )
    val kotlinWithNoComposable =
      fixture.addFileToProjectAndInvalidate(
        "RegularKotlin.kt",
        // language=kotlin
        """
        fun aKotlinMethod() {
        }
      """.trimIndent()
      )
    runBlocking {
      assertTrue(previewProvider.accept(project, previewFile))
      assertTrue(previewProvider.accept(project, composableFile))
      assertTrue(previewProvider.accept(project, kotlinFile))
      assertTrue(previewProvider.accept(project, kotlinWithNoComposable))
    }
    assertEquals(
      PreferredVisibility.SPLIT,
      getRepresentationForFile(previewFile, project, fixture, previewProvider)
        .preferredInitialVisibility
    )
    assertEquals(
      PreferredVisibility.SPLIT,
      getRepresentationForFile(composableFile, project, fixture, previewProvider)
        .preferredInitialVisibility
    )
    assertEquals(
      PreferredVisibility.HIDDEN,
      getRepresentationForFile(kotlinFile, project, fixture, previewProvider)
        .preferredInitialVisibility
    )
    assertEquals(
      PreferredVisibility.HIDDEN,
      getRepresentationForFile(kotlinWithNoComposable, project, fixture, previewProvider)
        .preferredInitialVisibility
    )
  }
}
