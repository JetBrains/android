/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.inspections

import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths.TEST_ARTIFACTS_ILLEGAL_IDENTIFIERS
import com.google.common.truth.Truth.assertThat
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.SystemInfoRt
import org.jetbrains.kotlin.android.inspection.IllegalIdentifierInspection

class IllegalIdentifierInspectionTest : AndroidGradleTestCase() {
  override fun setUp() {
    super.setUp()
    loadProject(TEST_ARTIFACTS_ILLEGAL_IDENTIFIERS)
    myFixture.enableInspections(IllegalIdentifierInspection::class.java)
  }

  fun testInspectionInRegularFile() {
    val file = project.guessProjectDir()!!
      .findFileByRelativePath("app/src/main/java/com/google/studio/android/test/Test.kt")!!
    myFixture.openFileInEditor(file)
    val highlightInfo = myFixture.doHighlighting(HighlightSeverity.ERROR)
    assertThat(highlightInfo).isNotEmpty()
    assertThat(highlightInfo.any { it.description == "Identifier not allowed in Android projects" }).isTrue()

    // 208842981: Allow spaces in method names via backticks
    // The lib module in the project sets minSdkVersion to 30, where the inspection no longer applies.
    val file2 = project.guessProjectDir()!!
      .findFileByRelativePath("lib/src/main/java/com/google/studio/android/test/Test2.kt")!!
    myFixture.openFileInEditor(file2)
    val highlightInfo2 = myFixture.doHighlighting(HighlightSeverity.ERROR)
    assertThat(highlightInfo2).isEmpty()
  }

  fun testInspectionInBuildKtsFile() {
    val file = project.guessProjectDir()!!
      .findFileByRelativePath("app/build.gradle.kts")!!
    myFixture.openFileInEditor(file)
    val highlightInfo = myFixture.doHighlighting(HighlightSeverity.ERROR)
    assertThat(highlightInfo).isEmpty()
  }
}