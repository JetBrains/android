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
    val error = "Found data binding error(s):\n" +
                "\n" +
                "[databinding] {\"msg\":\"Could not find identifier \\u0027var1\\u0027\\n\\nCheck that the identifier is spelled correctly, and that no \\u003cimport\\u003e or \\u003cvariable\\u003e tags are missing.\",\"file\":\"/src/main/res/layout/activity_main1.xml\",\"pos\":[{\"line0\":36,\"col0\":28,\"line1\":36,\"col1\":32}]}\n"
    val realError = RuntimeException(error)
    val invocationException = InvocationTargetException(KaptError(KaptError.Kind.ERROR_RAISED, realError))
    val topException = RuntimeException(invocationException)

    val gradleIssueData = GradleIssueData("some/project/path", topException, null, null)

    val handler = DataBindingIssueChecker()

    val resultIssue = handler.check(gradleIssueData)!!

    Truth.assertThat(resultIssue.title).isEqualTo("Could not find identifier 'var1'")
    Truth.assertThat(resultIssue.description).contains("Check that the identifier is spelled correctly")
    Truth.assertThat(resultIssue.quickFixes).hasSize(1)
    val link = resultIssue.quickFixes[0]
    Truth.assertThat(link).isInstanceOf(OpenFileAtLocationQuickFix::class.java)
  }

  @Test
  fun handleMultipleExceptionBasedDataBindingError() {
    val error = "Found data binding error(s):\n" +
                "\n" +
                "[databinding] {\"msg\":\"Could not find identifier \\u0027var1\\u0027\\n\\nCheck that the identifier is spelled correctly, and that no \\u003cimport\\u003e or \\u003cvariable\\u003e tags are missing.\",\"file\":\"/src/main/res/layout/activity_main1.xml\",\"pos\":[{\"line0\":36,\"col0\":28,\"line1\":36,\"col1\":32}]}\n" +
                "[databinding] {\"msg\":\"Could not find identifier \\u0027var2\\u0027\\n\\nCheck that the identifier is spelled correctly, and that no \\u003cimport\\u003e or \\u003cvariable\\u003e tags are missing.\",\"file\":\"/src/main/res/layout/activity_main2.xml\",\"pos\":[{\"line0\":58,\"col0\":23,\"line1\":58,\"col1\":27}]}\n"
    val realError = RuntimeException(error)
    val invocationException = InvocationTargetException(KaptError(KaptError.Kind.ERROR_RAISED, realError))
    val topException = RuntimeException(invocationException)

    val gradleIssueData = GradleIssueData("some/project/path", topException, null, null)

    val handler = DataBindingIssueChecker()

    val resultIssue = handler.check(gradleIssueData)!!

    Truth.assertThat(resultIssue.title).isEqualTo("Data Binding compiler")
    Truth.assertThat(resultIssue.description).contains("Could not find identifier 'var1'\n\n" +
                                                       "Check that the identifier is spelled correctly, and that no <import> or <variable> tags are missing.\n" +
                                                       "<a href=\"open.file\">Open File</a>\n\n\n" +
                                                       "Could not find identifier 'var2'\n\n" +
                                                       "Check that the identifier is spelled correctly, and that no <import> or <variable> tags are missing.\n" +
                                                       "<a href=\"open.file\">Open File</a>")
    Truth.assertThat(resultIssue.quickFixes).hasSize(2)
    Truth.assertThat(resultIssue.quickFixes[0]).isInstanceOf(OpenFileAtLocationQuickFix::class.java)
    Truth.assertThat(resultIssue.quickFixes[1]).isInstanceOf(OpenFileAtLocationQuickFix::class.java)
  }
}