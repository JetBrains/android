/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.rendering

import com.android.ide.common.rendering.api.Result
import com.android.tools.idea.configurations.Configuration
import com.android.tools.res.FrameworkResourceRepositoryManager.Companion.getInstance
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.VirtualFile
import junit.framework.TestCase
import org.intellij.lang.annotations.Language
import org.jetbrains.android.AndroidTestCase
import org.junit.Assert

/**
 * Asserts that the given result matches the [.SIMPLE_LAYOUT] structure
 */
fun checkSimpleLayoutResult(result: RenderResult) {
  TestCase.assertEquals(Result.Status.SUCCESS,
                        result.renderResult.status)
  val views = result.rootViews[0].children
  TestCase.assertEquals(3, views.size)
  var previousCoordinates: String? = ""
  for (i in 0..2) {
    val view = views[i]
    TestCase.assertEquals("android.widget.LinearLayout", view.className)
    // Check the coordinates are different for each box
    val currentCoordinates = String.format("%dx%d - %dx%d", view.top, view.left, view.bottom,
                                           view.right)
    Assert.assertNotEquals(previousCoordinates, currentCoordinates)
    previousCoordinates = currentCoordinates
  }
}

@Language("XML")
val SIMPLE_LAYOUT = """
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
  android:layout_height="match_parent"
  android:layout_width="match_parent"
  android:orientation="vertical">
    <LinearLayout
        android:layout_width="50dp"
        android:layout_height="50dp"
        android:background="#F00"/>
    <LinearLayout
        android:layout_width="50dp"
        android:layout_height="50dp"
        android:background="#0F0"/>
    <LinearLayout
        android:layout_width="50dp"
        android:layout_height="50dp"
        android:background="#00F"/>
  </LinearLayout>"""

class PerfgateBaseRenderTest : AndroidTestCase() {
  private lateinit var layoutFile: VirtualFile
  private lateinit var layoutConfiguration: Configuration

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    RenderTestUtil.beforeRenderTestCase()

    layoutFile = myFixture.addFileToProject("res/layout/layout.xml", SIMPLE_LAYOUT).virtualFile
    layoutConfiguration = RenderTestUtil.getConfiguration(myModule, layoutFile)
  }

  @Throws(Exception::class)
  override fun tearDown() {
    try {
      RenderTestUtil.afterRenderTestCase()
    }
    finally {
      getInstance().clearCache()
      super.tearDown()
    }
  }

  @Throws(Exception::class)
  fun testBaseInflate() {
    val computable = ThrowableComputable<PerfgateRenderMetric, Exception> {
      var metric: PerfgateRenderMetric? = null
      RenderTestUtil.withRenderTask(myFacet, layoutFile, layoutConfiguration) {
        metric = getInflateMetric(it) { result: RenderResult ->
          checkSimpleLayoutResult(result)
        }
      }
      metric!!
    }
    computeAndRecordMetric("inflate_time_base", "inflate_memory_base", computable)
  }

  @Throws(Exception::class)
  fun testBaseRender() {
    val computable = ThrowableComputable<PerfgateRenderMetric, Exception> {
      var metric: PerfgateRenderMetric? = null
      RenderTestUtil.withRenderTask(myFacet, layoutFile, layoutConfiguration) {
        metric = getRenderMetric(it, ::checkSimpleLayoutResult)
      }
      metric!!
    }
    computeAndRecordMetric("render_time_base", "render_memory_base", computable)
  }
}