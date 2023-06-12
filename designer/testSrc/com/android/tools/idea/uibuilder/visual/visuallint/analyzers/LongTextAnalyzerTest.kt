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
import com.android.tools.idea.rendering.RenderTask
import com.android.tools.idea.rendering.RenderTestUtil
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.model.NlComponentRegistrar
import com.intellij.openapi.application.ApplicationManager
import junit.framework.Assert
import org.intellij.lang.annotations.Language
import org.jetbrains.android.facet.AndroidFacet
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@Language("XML") private const val LAYOUT_WITH_LONG_TEXT =
  """<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
          android:layout_width="match_parent"
          android:layout_height="match_parent">

          <TextView
            android:id="@+id/textview1"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="This is a very very very very very very very very very very very long line of text than contains more than 120 characters" />
       </FrameLayout>"""

class LongTextAnalyzerTest {

  @get:Rule
  val projectRule = AndroidProjectRule.withSdk()

  @Before
  fun setup() {
    RenderTestUtil.beforeRenderTestCase()
  }

  @After
  fun tearDown() {
    ApplicationManager.getApplication().invokeAndWait {
      RenderTestUtil.afterRenderTestCase()
    }
  }

  @Test
  fun testTextSplitOnSeveralLines() {
    val file = projectRule.fixture.addFileToProject("res/layout/layout.xml", LAYOUT_WITH_LONG_TEXT).virtualFile
    val configuration = RenderTestUtil.getConfiguration(projectRule.module, file, "_device_class_phone")
    val facet = AndroidFacet.getInstance(projectRule.module)!!
    val nlModel = SyncNlModel.create(projectRule.project, NlComponentRegistrar, null, facet, file, configuration)

    RenderTestUtil.withRenderTask(facet, file, configuration) { task: RenderTask ->
      task.setDecorations(false)
      try {
        val result = task.render().get()
        val issues = LongTextAnalyzer.findIssues(result, nlModel)
        Assert.assertEquals(0, issues.size)
      }
      catch (ex: java.lang.Exception) {
        throw RuntimeException(ex)
      }
    }
  }

  @Test
  fun testTextOnOneLongLine() {
    val file = projectRule.fixture.addFileToProject("res/layout/layout.xml", LAYOUT_WITH_LONG_TEXT).virtualFile
    val configuration = RenderTestUtil.getConfiguration(projectRule.module, file, "_device_class_tablet")
    val facet = AndroidFacet.getInstance(projectRule.module)!!
    val nlModel = SyncNlModel.create(projectRule.project, NlComponentRegistrar, null, facet, file, configuration)

    RenderTestUtil.withRenderTask(facet, file, configuration) { task: RenderTask ->
      task.setDecorations(false)
      try {
        val result = task.render().get()
        val issues = LongTextAnalyzer.findIssues(result, nlModel)
        Assert.assertEquals(1, issues.size)
        Assert.assertEquals("textview1 <TextView> has lines containing more than 120 characters", issues[0].message)
      }
      catch (ex: java.lang.Exception) {
        throw RuntimeException(ex)
      }
    }
  }
}