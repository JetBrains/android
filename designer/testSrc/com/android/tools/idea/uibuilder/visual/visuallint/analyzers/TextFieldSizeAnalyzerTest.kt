/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.rendering.AndroidBuildTargetReference
import com.android.tools.idea.rendering.RenderTestUtil
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.model.NlComponentRegistrar
import com.android.tools.rendering.RenderTask
import com.intellij.openapi.application.ApplicationManager
import java.util.concurrent.TimeUnit
import junit.framework.Assert
import org.intellij.lang.annotations.Language
import org.jetbrains.android.facet.AndroidFacet
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TextFieldSizeAnalyzerTest {

  @get:Rule val projectRule = AndroidProjectRule.withAndroidModel()

  @Before
  fun setup() {
    RenderTestUtil.beforeRenderTestCase()
  }

  @After
  fun tearDown() {
    ApplicationManager.getApplication().invokeAndWait { RenderTestUtil.afterRenderTestCase() }
  }

  @Test
  fun testSmallTextField() {
    @Language("XML")
    val content =
      """<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="match_parent"
            android:layout_height="match_parent">

            <EditText
              android:id="@+id/text_field"
              android:layout_width="400dp"
              android:layout_height="wrap_content" />

         </FrameLayout>"""

    val file = projectRule.fixture.addFileToProject("res/layout/layout.xml", content).virtualFile
    val configuration = RenderTestUtil.getConfiguration(projectRule.module, file)
    val facet = AndroidFacet.getInstance(projectRule.module)!!
    val nlModel =
      SyncNlModel.create(
        projectRule.fixture.testRootDisposable,
        NlComponentRegistrar,
        AndroidBuildTargetReference.gradleOnly(facet),
        file,
      )

    RenderTestUtil.withRenderTask(facet, file, configuration) { task: RenderTask ->
      task.setDecorations(false)
      try {
        val result = task.render().get()
        val issues = TextFieldSizeAnalyzer.findIssues(result, nlModel)
        Assert.assertEquals(0, issues.size)
      } catch (ex: java.lang.Exception) {
        throw RuntimeException(ex)
      }
    }
  }

  @Test
  fun testLargeTextField() {
    @Language("XML")
    val content =
      """<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="match_parent"
            android:layout_height="match_parent">

            <EditText
              android:id="@+id/text_field"
              android:layout_width="500dp"
              android:layout_height="wrap_content" />

         </FrameLayout>"""

    val file = projectRule.fixture.addFileToProject("res/layout/layout.xml", content).virtualFile
    val configuration = RenderTestUtil.getConfiguration(projectRule.module, file)
    val facet = AndroidFacet.getInstance(projectRule.module)!!
    val nlModel =
      SyncNlModel.create(
        projectRule.fixture.testRootDisposable,
        NlComponentRegistrar,
        AndroidBuildTargetReference.gradleOnly(facet),
        file,
      )

    RenderTestUtil.withRenderTask(facet, file, configuration) { task: RenderTask ->
      task.setDecorations(false)
      try {
        task.runAsyncRenderActionWithSession({}, 0, TimeUnit.SECONDS)
        val result = task.render().get()
        val issues = TextFieldSizeAnalyzer.findIssues(result, nlModel)
        Assert.assertEquals(1, issues.size)
        Assert.assertEquals("The text field text_field <EditText> is too wide", issues[0].message)
      } catch (ex: java.lang.Exception) {
        throw RuntimeException(ex)
      }
    }
  }
}
