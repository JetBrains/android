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
package org.jetbrains.android.intentions

import com.android.AndroidProjectTypes
import com.android.testutils.TestUtils
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.androidFacet
import com.intellij.codeInsight.generation.surroundWith.JavaWithDoWhileSurrounder
import com.intellij.codeInsight.generation.surroundWith.JavaWithIfSurrounder
import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.EditorTestUtil
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

private const val BASE_PATH = "intentions"

@RunWith(JUnit4::class)
class AndroidCodeGenerationTest {
  @get:Rule
  val projectRule = AndroidProjectRule.withSdk()

  private val fixture by lazy {
    projectRule.fixture.apply {
      testDataPath = TestUtils.resolveWorkspacePath("tools/adt/idea/android/testData").toString()
    }
  }
  private val facet by lazy { requireNotNull(fixture.module.androidFacet) }

  @Test
  fun surroundingWithIfAndDoWhile() {
    facet.configuration.projectType = AndroidProjectTypes.PROJECT_TYPE_APP

    val file = fixture.copyFileToProject("$BASE_PATH/CodeGeneration.java", "src/p1/p2/Class.java")
    fixture.configureFromExistingVirtualFile(file)

    WriteCommandAction.runWriteCommandAction(fixture.project) { SurroundWithHandler.invoke(fixture.project, fixture.editor, fixture.file, JavaWithIfSurrounder()) }
    fixture.checkResultByFile("$BASE_PATH/CodeGeneration_afterSurroundWithIf.java")
    WriteCommandAction.runWriteCommandAction(fixture.project) { EditorTestUtil.executeAction(fixture.editor, "Unwrap") }
    fixture.checkResultByFile("$BASE_PATH/CodeGeneration_afterUnwrap.java")
    WriteCommandAction.runWriteCommandAction(fixture.project) { SurroundWithHandler.invoke(fixture.project, fixture.editor, fixture.file, JavaWithDoWhileSurrounder()) }
    fixture.checkResultByFile("$BASE_PATH/CodeGeneration_afterSurroundWithDoWhile.java")
  }
}