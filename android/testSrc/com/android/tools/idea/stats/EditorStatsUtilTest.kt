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
package com.android.tools.idea.stats

import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.EditorFileType
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class EditorStatsUtilTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun simpleFileTypes() = runBlocking {
    // EditorFileType.NATIVE is not tested, because there is no file extension registered for it. It's unclear whether it's needed at all,
    // but since this test is being added to document existing behavior I'm leaving it in the source code for now.

    val simpleFileTypes = mapOf(
      "java" to EditorFileType.JAVA,
      "groovy" to EditorFileType.GROOVY,
      "properties" to EditorFileType.PROPERTIES,
      "json" to EditorFileType.JSON,
      "foo" to EditorFileType.UNKNOWN,
    )

    for ((fileExtension, editorFileType) in simpleFileTypes) {
      val fakeResourceFile = projectRule.fixture.addFileToProject("Fake.$fileExtension", "").virtualFile
      assertThat(getEditorFileTypeForAnalytics(fakeResourceFile, null)).isEqualTo(editorFileType)
    }
  }

  @Test
  fun xmlFileTypes() = runBlocking {
    val simpleXmlFileTypes = mapOf(
      "anim" to EditorFileType.XML_RES_ANIM,
      "animator" to EditorFileType.XML_RES_ANIMATOR,
      "color" to EditorFileType.XML_RES_COLOR,
      "drawable" to EditorFileType.XML_RES_DRAWABLE,
      "font" to EditorFileType.XML_RES_FONT,
      "interpolator" to EditorFileType.XML_RES_INTERPOLATOR,
      "layout" to EditorFileType.XML_RES_LAYOUT,
      "menu" to EditorFileType.XML_RES_MENU,
      "mipmap" to EditorFileType.XML_RES_MIPMAP,
      "navigation" to EditorFileType.XML_RES_NAVIGATION,
      "raw" to EditorFileType.XML_RES_RAW,
      "transition" to EditorFileType.XML_RES_TRANSITION,
      "values" to EditorFileType.XML_RES_VALUES,
      "xml" to EditorFileType.XML_RES_XML,
    )

    for ((fileTypeName, editorFileType) in simpleXmlFileTypes) {
      val fakeResourceFile = projectRule.fixture.addFileToProject("$fileTypeName/Fake.xml", "").virtualFile
      assertThat(getEditorFileTypeForAnalytics(fakeResourceFile, null)).isEqualTo(editorFileType)
    }

    val fakeManifestFile = projectRule.fixture.addFileToProject("AndroidManifest.xml", "").virtualFile
    assertThat(getEditorFileTypeForAnalytics(fakeManifestFile, null)).isEqualTo(EditorFileType.XML_MANIFEST)

    val fakeOtherXmlFile = projectRule.fixture.addFileToProject("not_a_resource_folder/OtherFile.xml", "").virtualFile
    assertThat(getEditorFileTypeForAnalytics(fakeOtherXmlFile, null)).isEqualTo(EditorFileType.XML)
  }

  @Test
  fun kotlinFileTypes() = runBlocking {
    val fakeKotlinScriptFile = projectRule.fixture.addFileToProject("Fake.kts", "").virtualFile
    val fakeKotlinFile = projectRule.fixture.addFileToProject("Fake.kt", "").virtualFile

    // Test the different Kotlin variants
    (projectRule.module.getModuleSystem() as DefaultModuleSystem).usesCompose = false
    assertThat(getEditorFileTypeForAnalytics(fakeKotlinScriptFile, projectRule.project)).isEqualTo(EditorFileType.KOTLIN_SCRIPT)

    // KOTLIN_SCRIPT should be returned even if compose is enabled.
    (projectRule.module.getModuleSystem() as DefaultModuleSystem).usesCompose = true
    assertThat(getEditorFileTypeForAnalytics(fakeKotlinScriptFile, projectRule.project)).isEqualTo(EditorFileType.KOTLIN_SCRIPT)

    (projectRule.module.getModuleSystem() as DefaultModuleSystem).usesCompose = false
    assertThat(getEditorFileTypeForAnalytics(fakeKotlinFile, projectRule.project)).isEqualTo(EditorFileType.KOTLIN)

    (projectRule.module.getModuleSystem() as DefaultModuleSystem).usesCompose = true
    assertThat(getEditorFileTypeForAnalytics(fakeKotlinFile, projectRule.project)).isEqualTo(EditorFileType.KOTLIN_COMPOSE)
  }
}
