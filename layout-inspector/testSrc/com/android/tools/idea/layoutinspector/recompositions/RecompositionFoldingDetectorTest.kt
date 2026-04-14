/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.recompositions

import com.android.testutils.TestUtils
import com.android.tools.idea.layoutinspector.TestScopeRule
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.runInEdtAndGet
import kotlin.io.path.readText
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

private const val TEST_DATA_PATH = "tools/adt/idea/layout-inspector/testData/stateinspection"

class RecompositionFoldingDetectorTest {
  private val projectRule = AndroidProjectRule.inMemory()

  @get:Rule val rule = RuleChain(TestScopeRule(), projectRule)

  @Test
  fun testFolding() = runTest {
    val file = "${TEST_DATA_PATH}/state_reads_1_2.txt"
    val text = TestUtils.resolveWorkspacePathUnchecked(file).readText()
    val editor = runInEdtAndGet { projectRule.createEditorWithContent(text) }
    val detector = RecompositionFoldingDetector(editor, this)
    detector.detectFolding()?.join()
    validateFoldingModel(editor.foldingModel) {
      fold(3, 12, "<detailed value...>")
      fold(16, 26, "<detailed value...>")
      fold(27, 33, "<7 more...>")
      fold(40, 93, "<54 more...>")
      fold(96, 104, "<9 more...>")
      fold(109, 136, "<28 more...>")
    }
  }

  @Test
  fun testFoldingOfUnrecognizedException() = runTest {
    val file = "${TEST_DATA_PATH}/state_reads_unrecognized_exception.txt"
    val text = TestUtils.resolveWorkspacePathUnchecked(file).readText()
    val editor = runInEdtAndGet { projectRule.createEditorWithContent(text) }
    val detector = RecompositionFoldingDetector(editor, this)
    detector.detectFolding()?.join()
    validateFoldingModel(editor.foldingModel) {
      fold(39, 47, "<9 more...>")
      fold(52, 79, "<28 more...>")
    }
  }

  @Test
  fun testFoldingOfExceptionWithDerivedSnapshotGetValue() = runTest {
    val file = "${TEST_DATA_PATH}/state_reads_derived_snapshot_state_read.txt"
    val text = TestUtils.resolveWorkspacePathUnchecked(file).readText()
    val editor = runInEdtAndGet { projectRule.createEditorWithContent(text) }
    val detector = RecompositionFoldingDetector(editor, this)
    detector.detectFolding()?.join()
    validateFoldingModel(editor.foldingModel) {
      fold(2, 5, "<4 more...>")
      fold(9, 45, "<37 more...>")
    }
  }
}
