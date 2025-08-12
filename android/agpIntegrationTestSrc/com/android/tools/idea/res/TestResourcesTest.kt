/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.res

import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.moveCaret
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import org.jetbrains.android.dom.inspections.AndroidDomInspection
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class TestResourcesTest {
  @get:Rule
  val projectRule = AndroidGradleProjectRule().onEdt()
  val project by lazy { projectRule.project }
  val fixture by lazy { projectRule.fixture }

  @Test
  fun testResolveErrors() {
    projectRule.loadProject(TestProjectPaths.TEST_RESOURCES)
    fixture.enableInspections(AndroidDomInspection())

    fixture.openFileInEditor(fixture.project.baseDir.findFileByRelativePath("app/src/androidTest/AndroidManifest.xml")!!)
    val manifestHighlightErrors = fixture.doHighlighting(HighlightSeverity.ERROR)
    assertThat(manifestHighlightErrors).hasSize(1)
    assertThat(manifestHighlightErrors.single().description).isEqualTo("Cannot resolve symbol '@string/made_up'")

    fixture.openFileInEditor(fixture.project.baseDir.findFileByRelativePath("app/src/androidTest/res/values/strings.xml")!!)
    val stringsXmlHighlightErrors = fixture.doHighlighting(HighlightSeverity.ERROR)
    assertThat(stringsXmlHighlightErrors).hasSize(1)
    assertThat(stringsXmlHighlightErrors.single().description).isEqualTo("Cannot resolve symbol '@string/made_up'")
  }

  @Test
  fun testGoToDefinition_referenceInsideSameFile() {
    projectRule.loadProject(TestProjectPaths.TEST_RESOURCES)

    fixture.openFileInEditor(fixture.project.baseDir.findFileByRelativePath("app/src/androidTest/res/values/strings.xml")!!)
    fixture.moveCaret("@string/androidTest|AppString")

    CodeInsightTestUtil.gotoImplementation(fixture.editor, null)

    // Validate that the cursor moved to the correct location.
    assertThat(fixture.editor.document.text.substring(fixture.editor.caretModel.offset))
      .startsWith("androidTestAppString\">String defined in Application androidTest</string>")
  }
}
