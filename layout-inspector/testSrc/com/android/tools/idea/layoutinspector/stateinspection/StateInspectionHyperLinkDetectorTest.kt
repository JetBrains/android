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
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import kotlin.io.path.readText
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private const val TEST_DATA_PATH = "tools/adt/idea/layout-inspector/testData/stateinspection"

@RunsInEdt
class StateInspectionHyperLinkDetectorTest {
  private val projectRule = AndroidProjectRule.inMemory()

  @get:Rule val rule = RuleChain(projectRule, EdtRule())

  @Before
  fun before() {
    projectRule.fixture.addFileToProject("src/com/example/recompositiontest/MainActivity.kt", "")
    projectRule.fixture.addFileToProject("src/androidx/compose/material3/Button.kt", "")
    projectRule.fixture.addFileToProject("src/androidx/compose/material3/Text.kt", "")
    projectRule.fixture.addFileToProject("src/androidx/compose/runtime/Composer.kt", "")
    projectRule.fixture.addFileToProject("src/androidx/compose/runtime/Composition.kt", "")
    projectRule.fixture.addFileToProject("src/androidx/compose/runtime/CompositionLocalMap.kt", "")
    projectRule.fixture.addFileToProject("src/androidx/compose/runtime/Recomposer.kt", "")
    projectRule.fixture.addFileToProject("src/androidx/compose/runtime/SnapshotState.kt", "")
    projectRule.fixture.addFileToProject("src/androidx/compose/runtime/ValueHolders.kt", "")
    projectRule.fixture.addFileToProject("src/androidx/compose/runtime/snapshots/Snapshot.kt", "")
    projectRule.fixture.addFileToProject(
      "src/androidx/compose/runtime/internal/ComposableLambda.kt",
      "",
    )
  }

  @Test
  fun testHyperLinks() = runTest {
    val file = "${TEST_DATA_PATH}/state_reads_1_2.txt"
    val text = TestUtils.resolveWorkspacePathUnchecked(file).readText()
    val editor = projectRule.createEditorWithContent(text) as EditorEx
    val project = projectRule.project
    val detector =
      StateInspectionHyperLinkDetector(project, editor, this, projectRule.testRootDisposable)
    detector.filterJob.join()
    runWriteAction {
      // The write action allows the AsyncFilterRunner used by EditorHyperlinkSupport to run all
      // the tasks on its Queue immediately.
      detector.detectHyperlinks()
    }
    validateMarkupModel(editor.markupModel) {
      region(13, "Composition.kt:1015")
      region(14, "Recomposer.kt:1519")
      region(17, "Snapshot.kt:2081")
      region(18, "SnapshotState.kt:142")
      region(19, "ValueHolders.kt:71")
      region(20, "CompositionLocalMap.kt:88")
      region(21, "Composer.kt:2473")
      region(22, "Text.kt:352")
      region(23, "MainActivity.kt:79")
      region(26, "ComposableLambda.kt:130")
      region(27, "ComposableLambda.kt:51")
      region(28, "Button.kt:1140")
      region(29, "Button.kt:139")
      region(30, "ComposableLambda.kt:121")
      region(31, "ComposableLambda.kt:122")
      region(32, "ComposableLambda.kt:122")
      region(34, "Composer.kt:2926")
      region(35, "Composer.kt:3320")
      region(37, "Button.kt:136")
      region(38, "Button.kt:135")
      region(39, "ComposableLambda.kt:121")
      region(40, "ComposableLambda.kt:51")
      region(43, "ComposableLambda.kt:121")
      region(44, "ComposableLambda.kt:51")
      region(47, "Button.kt:125")
      region(48, "MainActivity.kt:78")
      region(53, "Composer.kt:2926")
      region(54, "Composer.kt:3262")
      region(55, "Composer.kt:3893")
      region(56, "Composer.kt:3817")
      region(57, "Composition.kt:1076")
      region(58, "Recomposer.kt:1400")
      region(59, "Recomposer.kt:156")
      region(60, "Recomposer.kt:635")
      region(82, "Composition.kt:1015")
      region(83, "Recomposer.kt:1519")
      region(86, "Snapshot.kt:2081")
      region(91, "MainActivity.kt:60")
      region(96, "Composer.kt:2926")
      region(97, "Composer.kt:3262")
      region(98, "Composer.kt:3893")
      region(99, "Composer.kt:3817")
      region(100, "Composition.kt:1076")
      region(101, "Recomposer.kt:1400")
      region(102, "Recomposer.kt:156")
      region(103, "Recomposer.kt:635")
    }
  }
}
