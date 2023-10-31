/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.sdk

import com.android.sdklib.AndroidVersion
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.daemon.ProblemHighlightFilter
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

/** Test for [SdkHighlightFilter]. */
class SdkHighlightFilterTest {

  // TODO(b/291755082): Update to 34 once 34 sources are published
  @get:Rule
  val projectRule = AndroidProjectRule.withSdk(AndroidVersion(33))

  @get:Rule
  val edtRule = EdtRule()

  @Test
  @RunsInEdt
  fun testSdkHighlighting() {
    val fixture = projectRule.fixture
    val sdkFile = fixture.findClass("android.view.View").navigationElement.containingFile
    assertThat(sdkFile.fileType).isEqualTo(JavaFileType.INSTANCE) // Should be a source file, not a .class file.
    assertThat(ProblemHighlightFilter.shouldHighlightFile(sdkFile)).isTrue()

    // SDK files should have no errors highlighted.
    fixture.openFileInEditor(sdkFile.virtualFile)
    assertThat(fixture.doHighlighting(HighlightSeverity.ERROR)).isEmpty()
  }

  @Test
  @RunsInEdt
  fun testNonSdkHighlighting() {
    // Non-SDK files should be highlighted with errors as usual.
    val fixture = projectRule.fixture
    val nonSdkFile = fixture.addFileToProject("src/Test.kt", "fun main() = Nonexistent.foo()")
    fixture.openFileInEditor(nonSdkFile.virtualFile)
    assertThat(fixture.doHighlighting(HighlightSeverity.ERROR)).isNotEmpty()
  }
}
