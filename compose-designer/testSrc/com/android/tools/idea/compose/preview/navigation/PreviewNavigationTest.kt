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
package com.android.tools.idea.compose.preview.navigation

import com.android.tools.idea.compose.ComposeGradleProjectRule
import com.android.tools.idea.compose.preview.SIMPLE_COMPOSE_PROJECT_PATH
import com.android.tools.idea.compose.preview.renderer.renderPreviewElementForResult
import com.android.tools.idea.compose.preview.util.SinglePreviewElementInstance
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

class PreviewNavigationTest {
  @get:Rule
  val projectRule = ComposeGradleProjectRule(SIMPLE_COMPOSE_PROJECT_PATH)

  /**
   * Checks the rendering of the default `@Preview` in the Compose template.
   */
  @Test
  fun testComposableNavigation() {
    val project = projectRule.project

    renderPreviewElementForResult(projectRule.androidFacet(":app"),
                                  SinglePreviewElementInstance.forTesting("google.simpleapplication.MainActivityKt.TwoElementsPreview"))
      .thenAccept { renderResult ->
        val rootView = renderResult!!.rootViews.single()!!
        ReadAction.run<Throwable> {
          // Find the boundaries for the root element. This will cover the whole layout
          val bounds = parseViewInfo(rootView).map { it.bounds }.first()

          // Check clicking outside of the boundaries
          assertTrue(findComponentHits(project, rootView, -30, -30).isEmpty())
          assertNull(findNavigatableComponentHit(project, rootView, -30, -30))
          assertTrue(findComponentHits(project, rootView, -1, 0).isEmpty())
          assertTrue(findComponentHits(project, rootView, bounds.right * 2, 10).isEmpty())
          assertTrue(findComponentHits(project, rootView, 10, bounds.bottom * 2).isEmpty())

          // Check filtering
          assertNotNull(findNavigatableComponentHit(project, rootView, 0, 0))
          assertNull(findNavigatableComponentHit(project, rootView, 0, 0) { false })

          // Click the Text("Hello 2") by clicking (0, 0)
          // The hits will be, in that other: Text > Column > MaterialTheme
          // TODO(b/156744111): The Text hit is currently broken
          assertEquals("""
            MainActivity.kt:46
            MainActivity.kt:45
          """.trimIndent(), findComponentHits(project, rootView, 0, 0)
            .filter { it.fileName == "MainActivity.kt" }
            .joinToString("\n") { "${it.fileName}:${it.lineNumber}" })

          // Click the Button by clicking (0, bounds.bottom)
          // The hits will be, in that other: Button > Column > MaterialTheme
          assertEquals("""
            MainActivity.kt:48
            MainActivity.kt:46
            MainActivity.kt:45
          """.trimIndent(), findComponentHits(project, rootView, 0, bounds.bottom)
            .filter { it.fileName == "MainActivity.kt" }
            .joinToString("\n") { "${it.fileName}:${it.lineNumber}" })
        }
      }.join()
  }

  /**
   * Checks the rendering of the default `@Preview` in the Compose template.
   */
  @Ignore("b/156744111") // Disabled until dev 12 merged
  @Test
  fun testInProjectNavigation() {
    val project = projectRule.project

    renderPreviewElementForResult(projectRule.androidFacet(":app"),
                                  SinglePreviewElementInstance.forTesting("google.simpleapplication.MainActivityKt.NavigatablePreview"))
      .thenAccept { renderResult ->
        val rootView = renderResult!!.rootViews.single()!!
        ReadAction.run<Throwable> {
          val descriptor = findNavigatableComponentHit(project, rootView, 0, 0) { it.fileName == "MainActivity.kt" } as OpenFileDescriptor
          assertEquals("MainActivity.kt", descriptor.file.name)
          assertEquals(46, descriptor.line)

          val descriptorInOtherFile = findNavigatableComponentHit(project, rootView, 0, 0) as OpenFileDescriptor
          assertEquals("OtherPreviews.kt", descriptorInOtherFile.file.name)
          assertEquals(31, descriptor.line)
        }
      }.join()
  }
}