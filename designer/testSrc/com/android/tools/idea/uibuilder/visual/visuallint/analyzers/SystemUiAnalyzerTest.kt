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
package com.android.tools.idea.uibuilder.visual.visuallint.analyzers

import com.android.tools.idea.rendering.RenderTestUtil
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.visual.visuallint.CustomVisualLintViewInfoProvider
import com.android.tools.idea.uibuilder.visual.visuallint.toVisualLintConfiguration
import com.android.tools.idea.uibuilder.visual.visuallint.toVisualLintRenderResult
import com.android.tools.rendering.RenderTask
import com.android.tools.visuallint.ViewInfoProvider
import com.android.tools.visuallint.analyzers.SystemUiAnalyzer
import com.intellij.openapi.application.ApplicationManager
import org.intellij.lang.annotations.Language
import org.jetbrains.android.facet.AndroidFacet
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SystemUiAnalyzerTest {

  @get:Rule val projectRule = AndroidProjectRule.withSdk()

  @Before
  fun setup() {
    RenderTestUtil.beforeRenderTestCase()
    ViewInfoProvider.setCustomProvider(CustomVisualLintViewInfoProvider)
  }

  @After
  fun tearDown() {
    ViewInfoProvider.setCustomProvider(null)
    ApplicationManager.getApplication().invokeAndWait { RenderTestUtil.afterRenderTestCase() }
  }

  @Test
  fun testSystemUiOverlap() {
    @Language("XML")
    val content =
      """<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="match_parent"
            android:layout_height="match_parent">

            <TextView
              android:id="@+id/text_view"
              android:layout_width="match_parent"
              android:layout_height="match_parent"
              android:text="Text"/>

         </FrameLayout>"""

    val file = projectRule.fixture.addFileToProject("res/layout/layout.xml", content).virtualFile
    val configuration = RenderTestUtil.getConfiguration(projectRule.module, file)
    val facet = AndroidFacet.getInstance(projectRule.module)!!

    RenderTestUtil.withRenderTask(facet, file, configuration) { task: RenderTask ->
      task.setDecorations(true)
      try {
        val result = task.render().get()
        val issues =
          SystemUiAnalyzer.findIssues(
            renderResult = result.toVisualLintRenderResult(),
            configuration = configuration.toVisualLintConfiguration(),
          )
        assertEquals(1, issues.size)
        assertEquals("text_view <TextView> is covered by System UI", issues[0].message)
      } catch (ex: java.lang.Exception) {
        throw RuntimeException(ex)
      }
    }
  }

  @Test
  fun testNoSystemUiOverlap() {
    @Language("XML")
    val content =
      """<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:fitsSystemWindows="true">

            <TextView
              android:id="@+id/text_view"
              android:layout_width="match_parent"
              android:layout_height="match_parent"
              android:layout_marginTop="50dp"
              android:layout_marginBottom="50dp"
              android:text="Text"/>

         </FrameLayout>"""

    val file = projectRule.fixture.addFileToProject("res/layout/layout_no_overlap.xml", content).virtualFile
    val configuration = RenderTestUtil.getConfiguration(projectRule.module, file)
    val facet = AndroidFacet.getInstance(projectRule.module)!!

    RenderTestUtil.withRenderTask(facet, file, configuration) { task: RenderTask ->
      task.setDecorations(true)
      try {
        val result = task.render().get()
        val issues =
          SystemUiAnalyzer.findIssues(
            renderResult = result.toVisualLintRenderResult(),
            configuration = configuration.toVisualLintConfiguration(),
          )
        assertEquals(0, issues.size)
      } catch (ex: java.lang.Exception) {
        throw RuntimeException(ex)
      }
    }
  }
}
