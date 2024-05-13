/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.wear.preview.lint

import com.android.flags.junit.FlagRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.wear.preview.WearTileProjectRule
import com.intellij.ide.highlighter.HtmlFileType
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.ide.highlighter.XmlFileType
import org.jetbrains.kotlin.idea.KotlinFileType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class WearTileInspectionBaseTest(private val isUnitTestInspection: Boolean) {

  companion object {
    @JvmStatic @Parameterized.Parameters fun data() = listOf(false, true)
  }

  @get:Rule val projectRule = WearTileProjectRule(AndroidProjectRule.withAndroidModel())

  @get:Rule val wearTilePreviewFlagRule = FlagRule(StudioFlags.WEAR_TILE_PREVIEW, true)

  private val fixture
    get() = projectRule.fixture

  private val inspection =
    object : WearTilePreviewInspectionBase(isUnitTestInspection = isUnitTestInspection) {}

  @Before
  fun setUp() {
    fixture.addUnitTestSourceRoot()
  }

  @Test
  fun isAvailableForKotlinAndJavaFiles() {
    val kotlinFile = fixture.configureByText(KotlinFileType.INSTANCE, "")
    val javaFile = fixture.configureByText(JavaFileType.INSTANCE, "")

    assertEquals(!isUnitTestInspection, inspection.isAvailableForFile(kotlinFile))
    assertEquals(!isUnitTestInspection, inspection.isAvailableForFile(javaFile))
  }

  @Test
  fun isAvailableForUnitTestFiles() {
    val kotlinUnitTestFile = fixture.addFileToProject("src/test/test.kt", "")
    val javaUnitTestFile = fixture.addFileToProject("src/test/Test.java", "")

    assertEquals(isUnitTestInspection, inspection.isAvailableForFile(kotlinUnitTestFile))
    assertEquals(isUnitTestInspection, inspection.isAvailableForFile(javaUnitTestFile))
  }

  @Test
  fun isUnavailableForUnSupportedTypes() {
    val xmlFile = fixture.configureByText(XmlFileType.INSTANCE, "")
    val xmlUnitTestFile = fixture.addFileToProject("src/test/Test.xml", "")
    val htmlFile = fixture.configureByText(HtmlFileType.INSTANCE, "")
    val htmlUnitTestFile = fixture.addFileToProject("src/test/Test.html", "")

    assertFalse(inspection.isAvailableForFile(xmlFile))
    assertFalse(inspection.isAvailableForFile(xmlUnitTestFile))
    assertFalse(inspection.isAvailableForFile(htmlFile))
    assertFalse(inspection.isAvailableForFile(htmlUnitTestFile))
  }

  @Test
  fun canBeDisabled() {
    val kotlinFile = fixture.configureByText(KotlinFileType.INSTANCE, "")
    val javaFile = fixture.configureByText(JavaFileType.INSTANCE, "")

    StudioFlags.WEAR_TILE_PREVIEW.override(false)

    assertFalse(inspection.isAvailableForFile(kotlinFile))
    assertFalse(inspection.isAvailableForFile(javaFile))
  }
}
