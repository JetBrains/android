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

import com.android.ide.common.resources.Locale
import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.rendering.RenderTask
import com.android.tools.idea.rendering.RenderTestUtil
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.model.NlComponentRegistrar
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintBaseConfigIssues
import com.intellij.openapi.application.ApplicationManager
import junit.framework.Assert
import org.intellij.lang.annotations.Language
import org.jetbrains.android.facet.AndroidFacet
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LocaleAnalyzerTest {

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
  fun testLocaleTooBig() {
    @Language("XML") val layout =
      """<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
          android:layout_width="match_parent"
          android:layout_height="match_parent">

          <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:maxLines="1"
            android:text="@string/text" />
       </FrameLayout>"""

    @Language("XML") val defaultStrings =
      """<resources>
          <string name="text">This is a short string</string>
        </resources>"""

    @Language("XML") val localeStrings =
      """<resources>
          <string name="text">This is a very very very very very very very very very very very very very very very very very very very long string</string>
        </resources>"""

    val layoutFile = projectRule.fixture.addFileToProject("res/layout/layout.xml", layout).virtualFile
    projectRule.fixture.addFileToProject("res/values/strings.xml", defaultStrings)
    projectRule.fixture.addFileToProject("res/values-fr/strings.xml", localeStrings)

    val configuration = RenderTestUtil.getConfiguration(projectRule.module, layoutFile)
    val facet = AndroidFacet.getInstance(projectRule.module)!!
    val nlModel = SyncNlModel.create(projectRule.project, NlComponentRegistrar, null, facet, layoutFile)

    val analyzer = LocaleAnalyzer(VisualLintBaseConfigIssues())
    RenderTestUtil.withRenderTask(facet, layoutFile, configuration) { task: RenderTask ->
      task.setDecorations(false)
      try {
        val result = task.render().get()
        val issues = analyzer.findIssues(result, nlModel)
        Assert.assertEquals(0, issues.size)
      }
      catch (ex: java.lang.Exception) {
        throw RuntimeException(ex)
      }
    }

    configuration.locale = Locale.create("fr")
    RenderTestUtil.withRenderTask(facet, layoutFile, configuration) { task: RenderTask ->
      task.setDecorations(false)
      try {
        val result = task.render().get()
        val issues = analyzer.findIssues(result, nlModel)
        Assert.assertEquals(1, issues.size)
        Assert.assertEquals("The text might be cut off.", issues[0].message)
      }
      catch (ex: java.lang.Exception) {
        throw RuntimeException(ex)
      }
    }
  }

  @Test
  fun testLocaleEllipsis() {
    @Language("XML") val layout =
      """<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
          android:layout_width="match_parent"
          android:layout_height="match_parent">

          <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:maxLines="1"
            android:ellipsize="end"
            android:text="@string/text" />
       </FrameLayout>"""

    @Language("XML") val defaultStrings =
      """<resources>
          <string name="text">This is a short string</string>
        </resources>"""

    @Language("XML") val localeStrings =
      """<resources>
          <string name="text">This is a very very very very very very very very very very very very very very very very very very very long string</string>
        </resources>"""

    val layoutFile = projectRule.fixture.addFileToProject("res/layout/layout.xml", layout).virtualFile
    projectRule.fixture.addFileToProject("res/values/strings.xml", defaultStrings)
    projectRule.fixture.addFileToProject("res/values-fr/strings.xml", localeStrings)

    val configuration = RenderTestUtil.getConfiguration(projectRule.module, layoutFile)
    val facet = AndroidFacet.getInstance(projectRule.module)!!
    val nlModel = SyncNlModel.create(projectRule.project, NlComponentRegistrar, null, facet, layoutFile)

    val analyzer = LocaleAnalyzer(VisualLintBaseConfigIssues())
    RenderTestUtil.withRenderTask(facet, layoutFile, configuration) { task: RenderTask ->
      task.setDecorations(false)
      try {
        val result = task.render().get()
        val issues = analyzer.findIssues(result, nlModel)
        Assert.assertEquals(0, issues.size)
      }
      catch (ex: java.lang.Exception) {
        throw RuntimeException(ex)
      }
    }

    configuration.locale = Locale.create("fr")
    RenderTestUtil.withRenderTask(facet, layoutFile, configuration) { task: RenderTask ->
      task.setDecorations(false)
      try {
        val result = task.render().get()
        val issues = analyzer.findIssues(result, nlModel)
        Assert.assertEquals(1, issues.size)
        Assert.assertEquals("The text is ellipsized in locale \"fr\".", issues[0].message)
      }
      catch (ex: java.lang.Exception) {
        throw RuntimeException(ex)
      }
    }
  }

  @Test
  fun testBothTooBig() {
    @Language("XML") val layout =
      """<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
          android:layout_width="match_parent"
          android:layout_height="match_parent">

          <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:maxLines="1"
            android:text="@string/text" />
       </FrameLayout>"""

    @Language("XML") val defaultStrings =
      """<resources>
          <string name="text">This is a very very very very very very very very very very very very very very very very very very very long string</string>
        </resources>"""

    @Language("XML") val localeStrings =
      """<resources>
          <string name="text">This is also a very very very very very very very very very very very very very very long string</string>
        </resources>"""

    val layoutFile = projectRule.fixture.addFileToProject("res/layout/layout.xml", layout).virtualFile
    projectRule.fixture.addFileToProject("res/values/strings.xml", defaultStrings)
    projectRule.fixture.addFileToProject("res/values-fr/strings.xml", localeStrings)

    val configuration = RenderTestUtil.getConfiguration(projectRule.module, layoutFile)
    val facet = AndroidFacet.getInstance(projectRule.module)!!
    val nlModel = SyncNlModel.create(projectRule.project, NlComponentRegistrar, null, facet, layoutFile)

    val analyzer = LocaleAnalyzer(VisualLintBaseConfigIssues())
    RenderTestUtil.withRenderTask(facet, layoutFile, configuration) { task: RenderTask ->
      task.setDecorations(false)
      try {
        val result = task.render().get()
        val issues = analyzer.findIssues(result, nlModel)
        Assert.assertEquals(0, issues.size)
      }
      catch (ex: java.lang.Exception) {
        throw RuntimeException(ex)
      }
    }

    configuration.locale = Locale.create("fr")
    RenderTestUtil.withRenderTask(facet, layoutFile, configuration) { task: RenderTask ->
      task.setDecorations(false)
      try {
        val result = task.render().get()
        val issues = analyzer.findIssues(result, nlModel)
        Assert.assertEquals(0, issues.size)
      }
      catch (ex: java.lang.Exception) {
        throw RuntimeException(ex)
      }
    }
  }
}