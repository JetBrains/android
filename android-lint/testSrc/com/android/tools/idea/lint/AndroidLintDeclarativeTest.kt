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
package com.android.tools.idea.lint

import com.android.tools.idea.lint.common.AndroidLintInspectionBase
import com.android.tools.idea.lint.common.AndroidLintUseTomlInsteadInspection
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.withDeclarative
import com.google.common.truth.Truth.assertThat
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.writeText
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.VfsTestUtil
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class AndroidLintDeclarativeTest {

  @get:Rule val projectRule = AndroidGradleProjectRule().withDeclarative()
  private val project
    get() = projectRule.project

  private val fixture
    get() = projectRule.fixture

  @Test
  fun testDeclarativeTomlSkip() {
    projectRule.loadProject(TestProjectPaths.SIMPLE_APPLICATION_DECLARATIVE)

    val buildFile = project.baseDir.findFileByRelativePath("app/build.gradle.dcl")!!
    WriteCommandAction.runWriteCommandAction(project) {
      fixture.saveText(
        buildFile,
        """
      androidApp {
           deviceTest {
               dependencies {
                   implementation("junit:junit:4.3.12")
               }
           }
       }
    """
          .trimIndent(),
      )
      val catalog = VfsTestUtil.createFile(project.baseDir, "gradle/libs.versions.toml")
      catalog.writeText("[libraries]")
      fixture.openFileInEditor(buildFile)
    }

    val psiFile = runReadAction { PsiManager.getInstance(project).findFile(buildFile)!! }

    checkLint(
      psiFile,
      AndroidLintUseTomlInsteadInspection(),
      "implementation(\"junit:ju|nit:4.3.12\")\n",
      """
        No warnings.
      """
        .trimIndent(),
    )
  }

  private fun checkLint(
    psiFile: PsiFile,
    inspection: AndroidLintInspectionBase,
    caret: String,
    expected: String,
  ) {
    AndroidLintInspectionBase.setRegisterDynamicToolsFromTests(false)
    fixture.enableInspections(inspection)
    val fileText = psiFile.text
    val sb = StringBuilder()
    val target = psiFile.findCaretOffset(caret)
    WriteCommandAction.runWriteCommandAction(project) {
      fixture.editor.caretModel.moveToOffset(target)
    }
    val highlights =
      fixture.doHighlighting(HighlightSeverity.WARNING).asSequence().sortedBy { it.startOffset }
    for (highlight in highlights) {
      val startIndex = highlight.startOffset
      val endOffset = highlight.endOffset
      if (target < startIndex || target > endOffset) {
        continue
      }
      val description = highlight.description
      val severity = highlight.severity
      sb.append(severity.name.lowercase(Locale.ROOT).capitalize()).append(": ")
      sb.append(description).append("\n")

      val lineStart = fileText.lastIndexOf("\n", startIndex).let { if (it == -1) 0 else it + 1 }
      val lineEnd = fileText.indexOf("\n", startIndex).let { if (it == -1) fileText.length else it }
      sb.append(fileText.substring(lineStart, lineEnd)).append("\n")
      val rangeEnd = if (lineEnd < endOffset) lineEnd else endOffset
      for (i in lineStart until startIndex) sb.append(" ")
      for (i in startIndex until rangeEnd) sb.append("~")
      sb.append("\n")

      runReadAction {
        highlight.findRegisteredQuickFix { desc, range ->
          val action = desc.action
          sb.append("    ")
          if (action.isAvailable(project, fixture.editor, psiFile)) {
            sb.append("Fix: ")
            sb.append(action.text)
          } else {
            sb.append("Disabled Fix: ")
            sb.append(action.text)
          }
          sb.append("\n")
          null
        }
      }
    }

    if (sb.isEmpty()) {
      sb.append("No warnings.")
    }

    assertThat(expected.trimIndent().trim()).isEqualTo(sb.toString().trimIndent().trim())
  }
}
