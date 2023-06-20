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

@Language("XML") private const val LAYOUT =
  """<AbsoluteLayout xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="match_parent"
            android:layout_height="match_parent">

            <TextView
                android:id="@+id/textview1"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Text"
                android:layout_x="0px" />

            <TextView
                android:id="@+id/textview2"
                android:layout_width="255px"
                android:layout_height="wrap_content"
                android:text="Text"
                android:layout_x="100px" />

            <TextView
                android:id="@+id/textview3"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Text"
                android:layout_x="10px" />

            <TextView
                android:id="@+id/textview4"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Text"
                android:layout_x="20px" />

            <AbsoluteLayout
                android:id="@+id/absolute_layout"
                android:layout_width="match_parent"
                android:layout_height="300dp"
                android:layout_x="5px">

                <ImageView
                    android:id="@+id/image_view"
                    android:layout_x="5px"
                    android:layout_width="50dp"
                    android:layout_height="50dp" />

            </AbsoluteLayout>
        </AbsoluteLayout>"""

class WearMarginAnalyzerTest {

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
  fun testAnalyzeModelWithSmallRound() {
    val file = projectRule.fixture.addFileToProject("res/layout/layout.xml", LAYOUT).virtualFile
    val configuration = RenderTestUtil.getConfiguration(projectRule.module, file, "wearos_small_round")
    val facet = AndroidFacet.getInstance(projectRule.module)!!
    val nlModel = SyncNlModel.create(projectRule.project, NlComponentRegistrar, null, facet, file, configuration)

    RenderTestUtil.withRenderTask(facet, file, configuration) { task: RenderTask ->
      task.setDecorations(false)
      try {
        val result = task.render().get()
        val issues = WearMarginAnalyzer.findIssues(result, nlModel)
        Assert.assertEquals(3, issues.size)
        Assert.assertEquals("The view image_view <ImageView> is too close to the side of the device", issues[0].message)
        Assert.assertEquals("The view textview3 <TextView> is too close to the side of the device", issues[1].message)
        Assert.assertEquals("The view textview1 <TextView> is too close to the side of the device", issues[2].message)
      }
      catch (ex: java.lang.Exception) {
        throw RuntimeException(ex)
      }
    }
  }

  @Test
  fun testAnalyzeModelWithLargeRound() {
    val file = projectRule.fixture.addFileToProject("res/layout/layout.xml", LAYOUT).virtualFile
    val configuration = RenderTestUtil.getConfiguration(projectRule.module, file, "wearos_large_round")
    val facet = AndroidFacet.getInstance(projectRule.module)!!
    val nlModel = SyncNlModel.create(projectRule.project, NlComponentRegistrar, null, facet, file, configuration)

    RenderTestUtil.withRenderTask(facet, file, configuration) { task: RenderTask ->
      task.setDecorations(false)
      try {
        val result = task.render().get()
        val issues = WearMarginAnalyzer.findIssues(result, nlModel)
        Assert.assertEquals(4, issues.size)
        Assert.assertEquals("The view image_view <ImageView> is too close to the side of the device", issues[0].message)
        Assert.assertEquals("The view textview4 <TextView> is too close to the side of the device", issues[1].message)
        Assert.assertEquals("The view textview3 <TextView> is too close to the side of the device", issues[2].message)
        Assert.assertEquals("The view textview1 <TextView> is too close to the side of the device", issues[3].message)
      }
      catch (ex: java.lang.Exception) {
        throw RuntimeException(ex)
      }
    }
  }

  @Test
  fun testAnalyzeModelWithRect() {
    val file = projectRule.fixture.addFileToProject("res/layout/layout.xml", LAYOUT).virtualFile
    val configuration = RenderTestUtil.getConfiguration(projectRule.module, file, "wearos_rect")
    val facet = AndroidFacet.getInstance(projectRule.module)!!
    val nlModel = SyncNlModel.create(projectRule.project, NlComponentRegistrar, null, facet, file, configuration)

    RenderTestUtil.withRenderTask(facet, file, configuration) { task: RenderTask ->
      task.setDecorations(false)
      try {
        val result = task.render().get()
        val issues = WearMarginAnalyzer.findIssues(result, nlModel)
        Assert.assertEquals(3, issues.size)
        Assert.assertEquals("The view image_view <ImageView> is too close to the side of the device", issues[0].message)
        Assert.assertEquals("The view textview3 <TextView> is too close to the side of the device", issues[1].message)
        Assert.assertEquals("The view textview1 <TextView> is too close to the side of the device", issues[2].message)
      }
      catch (ex: java.lang.Exception) {
        throw RuntimeException(ex)
      }
    }
  }

  @Test
  fun testAnalyzeModelWithSquare() {
    val file = projectRule.fixture.addFileToProject("res/layout/layout.xml", LAYOUT).virtualFile
    val configuration = RenderTestUtil.getConfiguration(projectRule.module, file, "wearos_square")
    val facet = AndroidFacet.getInstance(projectRule.module)!!
    val nlModel = SyncNlModel.create(projectRule.project, NlComponentRegistrar, null, facet, file, configuration)

    RenderTestUtil.withRenderTask(facet, file, configuration) { task: RenderTask ->
      task.setDecorations(false)
      try {
        val result = task.render().get()
        val issues = WearMarginAnalyzer.findIssues(result, nlModel)
        Assert.assertEquals(2, issues.size)
        Assert.assertEquals("The view textview2 <TextView> is too close to the side of the device", issues[0].message)
        Assert.assertEquals("The view textview1 <TextView> is too close to the side of the device", issues[1].message)
      }
      catch (ex: java.lang.Exception) {
        throw RuntimeException(ex)
      }
    }
  }
}