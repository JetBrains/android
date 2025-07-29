/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.stateinspection

import com.android.testutils.TestUtils
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.testFramework.runInEdtAndGet
import kotlin.io.path.readText
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

private const val TEST_DATA_PATH = "tools/adt/idea/layout-inspector/testData/stateinspection"

class StateInspectionFoldingDetectorTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun testFolding() = runTest {
    val file = "${TEST_DATA_PATH}/state_reads_1_2.txt"
    val text = TestUtils.resolveWorkspacePathUnchecked(file).readText()
    val editor = runInEdtAndGet { projectRule.createEditorWithContent(text) }
    val detector = StateInspectionFoldingDetector(editor, this)
    detector.detectFolding()?.join()
    validateFoldingModel(editor.foldingModel) {
      fold(startLine = 2, endLine = 12, "<detailed value...>")
      fold(startLine = 13, endLine = 19, "<7 more...>")
      fold(startLine = 26, endLine = 79, "<54 more...>")
      fold(startLine = 82, endLine = 90, "<9 more...>")
      fold(startLine = 95, endLine = 122, "<28 more...>")
    }
  }
}
