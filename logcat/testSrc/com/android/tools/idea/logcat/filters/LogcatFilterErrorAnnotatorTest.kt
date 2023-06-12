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
package com.android.tools.idea.logcat.filters

import com.android.flags.junit.FlagRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.logcat.filters.parser.LogcatFilterFileType
import com.android.tools.idea.logcat.util.LogcatFilterLanguageRule
import com.google.common.truth.Truth.assertThat
import com.intellij.lang.annotation.Annotation
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.annotation.HighlightSeverity.ERROR
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import org.junit.Rule
import org.junit.Test

/**
 * Tests for [LogcatFilterErrorAnnotator]
 */
@RunsInEdt
class LogcatFilterErrorAnnotatorTest {
  private val projectRule = ProjectRule()

  @get:Rule
  val rule = RuleChain(projectRule, LogcatFilterLanguageRule(), EdtRule(), FlagRule(StudioFlags.LOGCAT_IS_FILTER))

  private val annotator = LogcatFilterErrorAnnotator()

  @Test
  fun logLevel() {
    val psi = parse("level:foo level:INFO level:bar")

    val annotations = CodeInsightTestUtil.testAnnotator(annotator, *psi.children)

    assertThat(annotations.map(Annotation::toAnnotationInfo)).containsExactly(
      AnnotationInfo(6, 9, "Invalid log level: foo", ERROR),
      AnnotationInfo(27, 30, "Invalid log level: bar", ERROR),
    )
  }

  @Test
  fun age() {
    val psi = parse("age:2 age:2m age:20M")

    val annotations = CodeInsightTestUtil.testAnnotator(annotator, *psi.children)

    assertThat(annotations.map(Annotation::toAnnotationInfo)).containsExactly(
      AnnotationInfo(4, 5, "Invalid duration: 2", ERROR),
      AnnotationInfo(17, 20, "Invalid duration: 20M", ERROR),
    )
  }

  @Test
  fun is_filter() {
    StudioFlags.LOGCAT_IS_FILTER.override(true)
    val psi = parse("is:crash is:firebase is:stacktrace is:foo")

    val annotations = CodeInsightTestUtil.testAnnotator(annotator, *psi.children)

    assertThat(annotations.map(Annotation::toAnnotationInfo)).containsExactly(
      AnnotationInfo(38, 41, "Invalid qualifier: foo", ERROR),
    )
  }

  private fun parse(text: String): PsiElement =
    PsiFileFactory.getInstance(projectRule.project).createFileFromText("temp.lcf", LogcatFilterFileType, text)
}

private data class AnnotationInfo(val startOffset: Int, val endOffset: Int, val message: String, val severity: HighlightSeverity)

private fun Annotation.toAnnotationInfo() = AnnotationInfo(startOffset, endOffset, message, severity)