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

import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import org.jetbrains.android.dom.inspections.AndroidDomInspection

class TestResourcesTest : AndroidGradleTestCase() {
  fun testResolveErrors() {
    loadProject(TestProjectPaths.TEST_RESOURCES)
    myFixture.enableInspections(AndroidDomInspection())

    val projectBaseDir = PlatformTestUtil.getOrCreateProjectBaseDir(myFixture.project)
    myFixture.openFileInEditor(projectBaseDir.findFileByRelativePath("app/src/androidTest/AndroidManifest.xml")!!)
    val manifestHighlightErrors = myFixture.doHighlighting(HighlightSeverity.ERROR)
    assertThat(manifestHighlightErrors).hasSize(1)
    assertThat(manifestHighlightErrors.single().description).isEqualTo("Cannot resolve symbol '@string/made_up'")

    myFixture.openFileInEditor(myFixture.project.baseDir.findFileByRelativePath("app/src/androidTest/res/values/strings.xml")!!)
    val stringsXmlHighlightErrors = myFixture.doHighlighting(HighlightSeverity.ERROR)
    assertThat(stringsXmlHighlightErrors).hasSize(1)
    assertThat(stringsXmlHighlightErrors.single().description).isEqualTo("Cannot resolve symbol '@string/made_up'")
  }

  fun testGoToDefinition_referenceInsideSameFile() {
    loadProject(TestProjectPaths.TEST_RESOURCES)

    val projectBaseDir = PlatformTestUtil.getOrCreateProjectBaseDir(myFixture.project)
    myFixture.openFileInEditor(projectBaseDir.findFileByRelativePath("app/src/androidTest/res/values/strings.xml")!!)
    myFixture.moveCaret("@string/androidTest|AppString")

    CodeInsightTestUtil.gotoImplementation(myFixture.editor, null)

    // Validate that the cursor moved to the correct location.
    assertThat(myFixture.editor.document.text.substring(myFixture.editor.caretModel.offset))
      .startsWith("androidTestAppString\">String defined in Application androidTest</string>")
  }
}
