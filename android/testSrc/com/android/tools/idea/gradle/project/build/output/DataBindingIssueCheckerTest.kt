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
package com.android.tools.idea.gradle.project.build.output

import com.android.tools.idea.gradle.project.sync.quickFixes.OpenFileAtLocationQuickFix
import com.google.common.truth.Truth
import org.jetbrains.kotlin.kapt3.diagnostic.KaptError
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.junit.Test
import java.io.File
import java.lang.reflect.InvocationTargetException

class DataBindingIssueCheckerTest {

  @Test
  fun handleNonDataBindingKaptError() {
    val error = "Some really bad kapt error text"
    val realError = RuntimeException(error)
    val invocationException = InvocationTargetException(KaptError(KaptError.Kind.ERROR_RAISED, realError))
    val topException = RuntimeException(invocationException)

    val gradleIssueData = GradleIssueData("some/project/path", topException, null, null)

    val handler = DataBindingIssueChecker()

    val resultIssue = handler.check(gradleIssueData)
    Truth.assertThat(resultIssue).isNull()
  }

  @Test
  fun handleSingleExceptionBasedDataBindingError() {
    val relativePath = "src/main/res/layout/activity_main1.xml"
    val projectPath = "/some/project/path"
    val expectedPath = File(projectPath, relativePath).absolutePath
    val error = "Found data binding error(s):\n" +
                "\n" +
                "[databinding] {\"msg\":\"Could not find identifier \\u0027var1\\u0027\\n\\nCheck that the identifier is spelled correctly, and that no \\u003cimport\\u003e or \\u003cvariable\\u003e tags are missing.\",\"file\":\"/$relativePath\",\"pos\":[{\"line0\":36,\"col0\":28,\"line1\":36,\"col1\":32}]}\n"
    val realError = RuntimeException(error)
    val invocationException = InvocationTargetException(KaptError(KaptError.Kind.ERROR_RAISED, realError))
    val topException = RuntimeException(invocationException)

    val gradleIssueData = GradleIssueData(projectPath, topException, null, null)

    val handler = DataBindingIssueChecker()

    val resultIssue = handler.check(gradleIssueData)!!

    Truth.assertThat(resultIssue.title).isEqualTo("Could not find identifier 'var1'")
    Truth.assertThat(resultIssue.description).contains("Check that the identifier is spelled correctly")
    Truth.assertThat(resultIssue.quickFixes).hasSize(1)
    val link = resultIssue.quickFixes[0]
    Truth.assertThat(link).isInstanceOf(OpenFileWithLocationQuickFix::class.java)
    Truth.assertThat((link as OpenFileWithLocationQuickFix).myFilePosition.file.absolutePath).isEqualTo(expectedPath)
  }

  @Test
  fun handleMultipleExceptionBasedDataBindingError() {
    val relativePath1 = "src/main/res/layout/activity_main1.xml"
    val relativePath2 = "src/main/res/layout/activity_main2.xml"
    val projectPath = "/some/project/path"
    val expectedPath1 = File(projectPath, relativePath1).absolutePath
    val expectedPath2 = File(projectPath, relativePath2).absolutePath
    val error = "Found data binding error(s):\n" +
                "\n" +
                "[databinding] {\"msg\":\"Could not find identifier \\u0027var1\\u0027\\n\\nCheck that the identifier is spelled correctly, and that no \\u003cimport\\u003e or \\u003cvariable\\u003e tags are missing.\",\"file\":\"/$relativePath1\",\"pos\":[{\"line0\":36,\"col0\":28,\"line1\":36,\"col1\":32}]}\n" +
                "[databinding] {\"msg\":\"Could not find identifier \\u0027var2\\u0027\\n\\nCheck that the identifier is spelled correctly, and that no \\u003cimport\\u003e or \\u003cvariable\\u003e tags are missing.\",\"file\":\"/$relativePath2\",\"pos\":[{\"line0\":58,\"col0\":23,\"line1\":58,\"col1\":27}]}\n"
    val realError = RuntimeException(error)
    val invocationException = InvocationTargetException(KaptError(KaptError.Kind.ERROR_RAISED, realError))
    val topException = RuntimeException(invocationException)

    val gradleIssueData = GradleIssueData(projectPath, topException, null, null)

    val handler = DataBindingIssueChecker()

    val resultIssue = handler.check(gradleIssueData)!!

    Truth.assertThat(resultIssue.title).isEqualTo("Found 2 data binding error(s)")
    Truth.assertThat(resultIssue.description).contains("""
     Found 2 data binding error(s)
     ==================================================
     Could not find identifier 'var1'
     
     Check that the identifier is spelled correctly, and that no <import> or <variable> tags are missing.
     <a href="open.file.0">Open File</a>
     ==================================================
     Could not find identifier 'var2'
     
     Check that the identifier is spelled correctly, and that no <import> or <variable> tags are missing.
     <a href="open.file.1">Open File</a>
     """.trimIndent())
    Truth.assertThat(resultIssue.quickFixes).hasSize(2)
    Truth.assertThat(resultIssue.quickFixes[0]).isInstanceOf(OpenFileWithLocationQuickFix::class.java)
    Truth.assertThat((resultIssue.quickFixes[0] as OpenFileWithLocationQuickFix).myFilePosition.file.absolutePath).isEqualTo(expectedPath1)
    Truth.assertThat(resultIssue.quickFixes[1]).isInstanceOf(OpenFileWithLocationQuickFix::class.java)
    Truth.assertThat((resultIssue.quickFixes[1] as OpenFileWithLocationQuickFix).myFilePosition.file.absolutePath).isEqualTo(expectedPath2)
  }
}