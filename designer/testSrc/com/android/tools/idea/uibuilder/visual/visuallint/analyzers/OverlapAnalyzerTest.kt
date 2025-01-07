/*
 * Copyright (C) 2021 The Android Open Source Project
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
import com.android.tools.rendering.RenderTask
import com.intellij.openapi.application.ApplicationManager
import org.intellij.lang.annotations.Language
import org.jetbrains.android.facet.AndroidFacet
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class OverlapAnalyzerTest {

  @get:Rule val projectRule = AndroidProjectRule.withSdk()

  @Before
  fun setup() {
    RenderTestUtil.beforeRenderTestCase()
  }

  @After
  fun tearDown() {
    ApplicationManager.getApplication().invokeAndWait { RenderTestUtil.afterRenderTestCase() }
  }

  @Test
  fun testTextHiddenIndex() {
    // Text hidden because image is defined after text
    @Language("XML")
    val content =
      """<AbsoluteLayout xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="match_parent"
            android:layout_height="match_parent">

            <TextView
              android:id="@+id/text_view"
              android:layout_width="200dp"
              android:layout_height="200dp"
              android:text="Text"
              android:layout_x="0dp"
              android:layout_y="0dp"/>

            <ImageView
              android:id="@+id/image_view"
              android:layout_width="200dp"
              android:layout_height="200dp"
              android:layout_x="0dp"
              android:layout_y="0dp"/>

         </AbsoluteLayout>"""

    val file = projectRule.fixture.addFileToProject("res/layout/is_hidden.xml", content).virtualFile
    val configuration = RenderTestUtil.getConfiguration(projectRule.module, file)
    val facet = AndroidFacet.getInstance(projectRule.module)!!

    RenderTestUtil.withRenderTask(facet, file, configuration) { task: RenderTask ->
      task.setDecorations(false)
      try {
        val result = task.render().get()
        val issues = OverlapAnalyzer.findIssues(result, configuration)
        assertEquals(1, issues.size)
        assertEquals("text_view <TextView> is covered by image_view <ImageView>", issues[0].message)
      } catch (ex: java.lang.Exception) {
        throw RuntimeException(ex)
      }
    }
  }

  @Test
  fun testTextShownIndex() {
    // Text shown because image is defined before text
    @Language("XML")
    val content =
      """<AbsoluteLayout xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="match_parent"
            android:layout_height="match_parent">

            <ImageView
              android:id="@+id/image_view"
              android:layout_width="200dp"
              android:layout_height="200dp"
              android:layout_x="0dp"
              android:layout_y="0dp"/>

            <TextView
              android:id="@+id/text_view"
              android:layout_width="200dp"
              android:layout_height="200dp"
              android:text="Text"
              android:layout_x="0dp"
              android:layout_y="0dp"/>

         </AbsoluteLayout>"""

    val file =
      projectRule.fixture.addFileToProject("res/layout/is_not_hidden.xml", content).virtualFile
    val configuration = RenderTestUtil.getConfiguration(projectRule.module, file)
    val facet = AndroidFacet.getInstance(projectRule.module)!!

    RenderTestUtil.withRenderTask(facet, file, configuration) { task: RenderTask ->
      task.setDecorations(false)
      try {
        val result = task.render().get()
        val issues = OverlapAnalyzer.findIssues(result, configuration)
        assertEquals(0, issues.size)
      } catch (ex: java.lang.Exception) {
        throw RuntimeException(ex)
      }
    }
  }

  @Test
  fun testTextHiddenElevation() {
    // Text hidden because image has higher elevation than text, even tho text view is defined
    // later.
    @Language("XML")
    val content =
      """<AbsoluteLayout xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="match_parent"
            android:layout_height="match_parent">

            <ImageView
              android:layout_width="200dp"
              android:layout_height="200dp"
              android:layout_x="0dp"
              android:layout_y="0dp"
              android:elevation="20dp"/>

            <TextView
              android:id="@+id/text_view"
              android:layout_width="200dp"
              android:layout_height="200dp"
              android:text="Text"
              android:layout_x="0dp"
              android:layout_y="0dp"/>

         </AbsoluteLayout>"""

    val file = projectRule.fixture.addFileToProject("res/layout/is_hidden.xml", content).virtualFile
    val configuration = RenderTestUtil.getConfiguration(projectRule.module, file)
    val facet = AndroidFacet.getInstance(projectRule.module)!!

    RenderTestUtil.withRenderTask(facet, file, configuration) { task: RenderTask ->
      task.setDecorations(false)
      try {
        val result = task.render().get()
        val issues = OverlapAnalyzer.findIssues(result, configuration)
        assertEquals(1, issues.size)
        assertEquals("text_view <TextView> is covered by ImageView", issues[0].message)
      } catch (ex: java.lang.Exception) {
        throw RuntimeException(ex)
      }
    }
  }

  @Test
  fun testTextShownElevation() {
    // Text shown because text has higher elevation
    @Language("XML")
    val content =
      """<AbsoluteLayout xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="match_parent"
            android:layout_height="match_parent">

            <TextView
              android:layout_width="200dp"
              android:layout_height="200dp"
              android:text="Text"
              android:layout_x="0dp"
              android:layout_y="0dp"
              android:elevation="25dp"/>

            <ImageView
              android:layout_width="200dp"
              android:layout_height="200dp"
              android:layout_x="0dp"
              android:layout_y="0dp"
              android:elevation="20dp"/>

         </AbsoluteLayout>"""

    val file =
      projectRule.fixture.addFileToProject("res/layout/is_not_hidden.xml", content).virtualFile
    val configuration = RenderTestUtil.getConfiguration(projectRule.module, file)
    val facet = AndroidFacet.getInstance(projectRule.module)!!

    RenderTestUtil.withRenderTask(facet, file, configuration) { task: RenderTask ->
      task.setDecorations(false)
      try {
        val result = task.render().get()
        val issues = OverlapAnalyzer.findIssues(result, configuration)
        assertEquals(0, issues.size)
      } catch (ex: java.lang.Exception) {
        throw RuntimeException(ex)
      }
    }
  }

  @Test
  fun testTextHidden60Percent() {
    // Text hidden because image is covering 60%
    @Language("XML")
    val content =
      """<AbsoluteLayout xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="match_parent"
            android:layout_height="match_parent">

            <TextView
              android:layout_width="100dp"
              android:layout_height="100dp"
              android:text="Text"
              android:layout_x="0dp"
              android:layout_y="0dp"/>

            <ImageView
              android:layout_width="60dp"
              android:layout_height="100dp"
              android:layout_x="0dp"
              android:layout_y="0dp"/>

         </AbsoluteLayout>"""

    val file = projectRule.fixture.addFileToProject("res/layout/is_hidden.xml", content).virtualFile
    val configuration = RenderTestUtil.getConfiguration(projectRule.module, file)
    val facet = AndroidFacet.getInstance(projectRule.module)!!

    RenderTestUtil.withRenderTask(facet, file, configuration) { task: RenderTask ->
      task.setDecorations(false)
      try {
        val result = task.render().get()
        val issues = OverlapAnalyzer.findIssues(result, configuration)
        assertEquals(1, issues.size)
      } catch (ex: java.lang.Exception) {
        throw RuntimeException(ex)
      }
    }
  }

  @Test
  fun testTextHidden40Percent() {
    // Text not hidden because image is only covering 40%
    @Language("XML")
    val content =
      """<AbsoluteLayout xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="match_parent"
            android:layout_height="match_parent">

            <TextView
              android:layout_width="100dp"
              android:layout_height="100dp"
              android:text="Text"
              android:layout_x="0dp"
              android:layout_y="0dp"/>

            <ImageView
              android:layout_width="40dp"
              android:layout_height="100dp"
              android:layout_x="0dp"
              android:layout_y="0dp"/>

         </AbsoluteLayout>"""

    val file =
      projectRule.fixture.addFileToProject("res/layout/is_not_hidden.xml", content).virtualFile
    val configuration = RenderTestUtil.getConfiguration(projectRule.module, file)
    val facet = AndroidFacet.getInstance(projectRule.module)!!

    RenderTestUtil.withRenderTask(facet, file, configuration) { task: RenderTask ->
      task.setDecorations(false)
      try {
        val result = task.render().get()
        val issues = OverlapAnalyzer.findIssues(result, configuration)
        assertEquals(0, issues.size)
      } catch (ex: java.lang.Exception) {
        throw RuntimeException(ex)
      }
    }
  }

  @Test
  fun testNoOverlap() {
    @Language("XML")
    val content =
      """<AbsoluteLayout xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="match_parent"
            android:layout_height="match_parent">

            <TextView
              android:layout_width="20dp"
              android:layout_height="20dp"
              android:text="Text"
              android:layout_x="0dp"
              android:layout_y="0dp"/>

            <ImageView
              android:layout_width="30dp"
              android:layout_height="30dp"
              android:layout_x="160dp"
              android:layout_y="160dp"/>

         </AbsoluteLayout>"""

    val file =
      projectRule.fixture.addFileToProject("res/layout/is_not_hidden.xml", content).virtualFile
    val configuration = RenderTestUtil.getConfiguration(projectRule.module, file)
    val facet = AndroidFacet.getInstance(projectRule.module)!!

    RenderTestUtil.withRenderTask(facet, file, configuration) { task: RenderTask ->
      task.setDecorations(false)
      try {
        val result = task.render().get()
        val issues = OverlapAnalyzer.findIssues(result, configuration)
        assertEquals(0, issues.size)
      } catch (ex: java.lang.Exception) {
        throw RuntimeException(ex)
      }
    }
  }
}
