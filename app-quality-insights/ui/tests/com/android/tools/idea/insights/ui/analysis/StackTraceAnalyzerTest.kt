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
package com.android.tools.idea.insights.ui.analysis

import com.android.testutils.TestUtils
import com.android.tools.idea.insights.Frame
import com.android.tools.idea.insights.analysis.Cause
import com.android.tools.idea.insights.analysis.Confidence
import com.android.tools.idea.insights.analysis.CrashFrame
import com.android.tools.idea.insights.analysis.StackTraceAnalyzer
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiThrowStatement
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.psi.KtCallExpression
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@RunsInEdt
class StackTraceAnalyzerTest {

  @get:Rule val androidProjectRule = AndroidProjectRule.onDisk()
  @get:Rule val edtRule = EdtRule()

  @Test
  fun `analyzer on javaKotlinApp project finds expected matches`() {
    androidProjectRule.fixture.testDataPath =
      TestUtils.resolveWorkspacePath("tools/adt/idea/app-quality-insights/ui/testData").toString()
    val mainActivityFile =
      androidProjectRule.fixture
        .copyFileToProject("src/com/google/firebase/assistant/test/MainActivity.kt")
        .toPsiFile(androidProjectRule.project)!!
    val runtimeInitFile =
      androidProjectRule.fixture
        .copyFileToProject("src/com/google/firebase/assistant/test/RuntimeInit.java")
        .toPsiFile(androidProjectRule.project)!!

    val analyzer = service<StackTraceAnalyzer>()
    runReadAction {
      val npeFrame =
        Frame(
          symbol = "com.google.firebase.assistant.test.RuntimeInit.foo",
          file = "RuntimeInit.java",
          line = 8
        )
      val first =
        analyzer.match(
          runtimeInitFile,
          CrashFrame(npeFrame, Cause.Throwable("java.lang.NullPointerException"))
        )
      val second =
        analyzer.match(
          mainActivityFile,
          CrashFrame(
            Frame(
              symbol = "com.google.firebase.assistant.test.MainActivity.onCreate\$lambda-1",
              file = "MainActivity.kt",
              line = 22
            ),
            Cause.Frame(npeFrame)
          )
        )
      val third =
        analyzer.match(
          mainActivityFile,
          CrashFrame(
            Frame(
              symbol = "com.google.firebase.assistant.test.MainActivity.onCreate",
              file = "MainActivity.kt",
              line = 20
            ),
            Cause.Throwable("java.lang.IllegalArgumentException")
          )
        )

      assertThat(first!!.confidence).isEqualTo(Confidence.HIGH)
      assertThat(first.element).isInstanceOf(PsiThrowStatement::class.java)
      val exception = (first.element as PsiThrowStatement).exception
      assertThat(exception).isInstanceOf(PsiMethodCallExpression::class.java)
      assertThat((exception as PsiMethodCallExpression).methodExpression.qualifiedName)
        .isEqualTo("createNpe")

      assertThat(second!!.element).isInstanceOf(KtCallExpression::class.java)
      assertThat(second.confidence).isEqualTo(Confidence.HIGH)
      /*
      TODO: reenable when we support lower-confidence matches again
      assertThat(third!!.element).isInstanceOf(KtThrowExpression::class.java)
      assertThat(third.confidence).isEqualTo(Confidence.MEDIUM)
      */
    }
  }

  @Test
  fun `analyzer is language agnostic, can find inner classes`() {
    androidProjectRule.fixture.testDataPath =
      TestUtils.resolveWorkspacePath("tools/adt/idea/app-quality-insights/ui/testData").toString()
    val psiFile =
      androidProjectRule.fixture
        .configureByFiles(
          "src/com/google/firebase/assistant/test/MainActivity.kt",
          "src/com/google/firebase/assistant/test/RuntimeInit.java"
        )[0]

    val analyzer = service<StackTraceAnalyzer>()
    runReadAction {
      val npeFrame =
        Frame(
          symbol = "com.google.firebase.assistant.test.RuntimeInit.foo",
          file = "RuntimeInit.java",
          line = 6
        )
      val crashFrames =
        listOf(
          CrashFrame(
            Frame(
              symbol = "com.google.firebase.assistant.test.MainActivity.randomMethod\$lambda-1",
              file = "MainActivity.java",
              line = 29
            ),
            Cause.Frame(npeFrame)
          ),
          CrashFrame(
            Frame(
              symbol = "com.google.firebase.assistant.test.MainActivity.randomMethod\$lambda-1",
              file = "MainActivity.java",
              line = 33
            ),
            Cause.Frame(npeFrame)
          )
        )

      val (first, second) = crashFrames.map { analyzer.match(psiFile, it) }

      assertThat(first!!.element).isInstanceOf(KtCallExpression::class.java)
      assertThat(first.confidence).isEqualTo(Confidence.HIGH)

      /*
      TODO: reenable when we support lower-confidence matches again
      assertThat(second!!.element).isInstanceOf(KtCallExpression::class.java)
      assertThat(second.confidence).isEqualTo(Confidence.MEDIUM)
      */
    }
  }
}
