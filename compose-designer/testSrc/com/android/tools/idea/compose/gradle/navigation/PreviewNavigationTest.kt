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
package com.android.tools.idea.compose.gradle.navigation

import com.android.tools.idea.compose.gradle.ComposeGradleProjectRule
import com.android.tools.idea.compose.preview.SIMPLE_COMPOSE_PROJECT_PATH
import com.android.tools.idea.compose.preview.navigation.findComponentHits
import com.android.tools.idea.compose.preview.navigation.findNavigatableComponentHit
import com.android.tools.idea.compose.preview.navigation.parseViewInfo
import com.android.tools.idea.compose.preview.renderer.renderPreviewElementForResult
import com.android.tools.idea.compose.preview.util.SingleComposePreviewElementInstance
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PreviewNavigationTest {
  private val LOG = Logger.getInstance(PreviewNavigationTest::class.java)

  @get:Rule val projectRule = ComposeGradleProjectRule(SIMPLE_COMPOSE_PROJECT_PATH)

  /** Checks the rendering of the default `@Preview` in the Compose template. */
  @Test
  fun testComposableNavigation() {
    val facet = projectRule.androidFacet(":app")
    val module = facet.mainModule

    renderPreviewElementForResult(
        facet,
        SingleComposePreviewElementInstance.forTesting(
          "google.simpleapplication.MainActivityKt.TwoElementsPreview"
        )
      )
      .thenAccept { renderResult ->
        val rootView = renderResult!!.rootViews.single()!!
        ReadAction.run<Throwable> {
          // Find the boundaries for the root element. This will cover the whole layout
          val bounds = parseViewInfo(rootView, logger = LOG).map { it.bounds }.first()

          // Check clicking outside of the boundaries
          assertTrue(findComponentHits(module, rootView, -30, -30).isEmpty())
          assertNull(findNavigatableComponentHit(module, rootView, -30, -30))
          assertTrue(findComponentHits(module, rootView, -1, 0).isEmpty())
          assertTrue(findComponentHits(module, rootView, bounds.right * 2, 10).isEmpty())
          assertTrue(findComponentHits(module, rootView, 10, bounds.bottom * 2).isEmpty())

          // Check filtering
          assertNotNull(findNavigatableComponentHit(module, rootView, 0, 0))
          assertNull(findNavigatableComponentHit(module, rootView, 0, 0) { false })

          // Click the Text("Hello 2") by clicking (0, 0)
          // The hits will be, in that other: Text > Column > MaterialTheme
          assertEquals(
            """
            MainActivity.kt:50
            MainActivity.kt:49
            MainActivity.kt:48
          """.trimIndent(),
            findComponentHits(module, rootView, 0, 0)
              .filter { it.fileName == "MainActivity.kt" }
              .joinToString("\n") { "${it.fileName}:${it.lineNumber}" }
          )

          // Click the Button by clicking (0, bounds.bottom)
          // The hits will be, in that other: Button > Column > MaterialTheme
          assertEquals(
            """
            MainActivity.kt:51
            MainActivity.kt:49
            MainActivity.kt:48
          """.trimIndent(),
            findComponentHits(module, rootView, 0, bounds.bottom)
              .filter { it.fileName == "MainActivity.kt" }
              .joinToString("\n") { "${it.fileName}:${it.lineNumber}" }
          )
        }
      }
      .join()
  }

  /** Checks the rendering of the default `@Preview` in the Compose template. */
  @Test
  fun testInProjectNavigation() {
    val facet = projectRule.androidFacet(":app")
    val module = facet.mainModule

    renderPreviewElementForResult(
        facet,
        SingleComposePreviewElementInstance.forTesting(
          "google.simpleapplication.MainActivityKt.NavigatablePreview"
        )
      )
      .thenAccept { renderResult ->
        val rootView = renderResult!!.rootViews.single()!!
        ReadAction.run<Throwable> {
          val descriptor =
            findNavigatableComponentHit(module, rootView, 0, 0) {
              it.fileName == "MainActivity.kt"
            } as
              OpenFileDescriptor
          assertEquals("MainActivity.kt", descriptor.file.name)
          // TODO(b/156744111)
          // assertEquals(46, descriptor.line)

          val descriptorInOtherFile =
            findNavigatableComponentHit(module, rootView, 0, 0) as OpenFileDescriptor
          assertEquals("OtherPreviews.kt", descriptorInOtherFile.file.name)
          // TODO(b/156744111)
          // assertEquals(31, descriptor.line)
        }
      }
      .join()
  }

  /**
   * Regression test for b/157129712 where we would navigate to the wrong file when the file names
   * were equal.
   */
  @Test
  fun testDuplicateFileNavigation() {
    val facet = projectRule.androidFacet(":app")
    val module = facet.mainModule

    renderPreviewElementForResult(
        facet,
        SingleComposePreviewElementInstance.forTesting(
          "google.simpleapplication.MainActivityKt.OnlyATextNavigation"
        )
      )
      .thenAccept { renderResult ->
        val rootView = renderResult!!.rootViews.single()!!
        ReadAction.run<Throwable> {
          // We click a Text() but we should not navigate to the local Text.kt file since it's not
          // related to the androidx.compose.ui.foundation.Text
          // Assert disabled for dev16 because of b/162066489
          // assertTrue(findComponentHits(module, rootView, 2, 2).any { it.fileName == "Text.kt" })
          assertTrue(
            (findNavigatableComponentHit(module, rootView, 2, 2) as OpenFileDescriptor).file.name ==
              "MainActivity.kt"
          )
        }
      }
      .join()
  }
}
