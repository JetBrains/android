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
import com.android.tools.idea.uibuilder.scene.NlModelHierarchyUpdater
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintIssueProvider
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.xml.XmlFile
import org.intellij.lang.annotations.Language
import org.jetbrains.android.facet.AndroidFacet
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class AtfAnalyzerTest {
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
  fun testAnalyzeModelWithError() {
    @Language("XML") val content = """
  <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
              android:layout_width="match_parent"
              android:layout_height="match_parent"
              android:orientation="vertical">

    <LinearLayout
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:id="@+id/clickable_wrapper_for_button"
        android:orientation="horizontal"
        android:clickable="true">

        <Button
            android:text="Button in clickable parent"
            android:id="@+id/button_in_clickable_parent"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:minHeight="48dp"/>
    </LinearLayout>

    <Button
        android:text="Button on its own"
        android:id="@+id/button_on_its_own"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:minHeight="48dp"/>

  </LinearLayout>
"""

    val psiFile = projectRule.fixture.addFileToProject("res/layout/layout.xml", content) as XmlFile
    val file = psiFile.virtualFile
    val configuration = RenderTestUtil.getConfiguration(projectRule.module, file)
    val facet = AndroidFacet.getInstance(projectRule.module)!!
    val nlModel = SyncNlModel.create(projectRule.project, NlComponentRegistrar, null, facet, file, configuration)

    RenderTestUtil.withRenderTask(facet, file, configuration, true) { task: RenderTask ->
      task.setDecorations(false)
      try {
        val result = task.render().get()
        NlModelHierarchyUpdater.updateHierarchy(result, nlModel)
        val issues = AtfAnalyzer.findIssues(result, nlModel)
        assertEquals(1, issues.size)
        issues.forEach {
          assertEquals("Duplicated clickable Views", it.message)
          assertEquals("This clickable item has the same on-screen location ([0,0][358,96]) as 1 other item(s) with those " +
                       "properties.<br><br>Learn more at <a href=\"https://support.google.com/accessibility/android/answer/6378943\">" +
                       "https://support.google.com/accessibility/android/answer/6378943</a>", it.descriptionProvider.invoke(1).html)
        }
      }
      catch (ex: java.lang.Exception) {
        throw RuntimeException(ex)
      }
    }
  }
}