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
package com.android.tools.idea.testartifacts.instrumented.testsuite.view

import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Unit tests for [DetailsViewContentView].
 */
@RunWith(JUnit4::class)
@RunsInEdt
class DetailsViewContentViewTest {

  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()

  @get:Rule val rules: RuleChain = RuleChain
    .outerRule(projectRule)
    .around(EdtRule())
    .around(disposableRule)

  @Test
  fun setAndroidTestCaseResult() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project)
    view.setAndroidTestCaseResult(AndroidTestCaseResult.PASSED)
    assertThat(view.myTestResultLabel.text).isEqualTo("PASSED")
  }

  @Test
  fun setLogcat() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project)

    view.setLogcat("test logcat message")
    view.myLogcatView.waitAllRequests()
    assertThat(view.myLogcatView.text).isEqualTo("test logcat message")

    view.setLogcat("test logcat message 2")
    view.myLogcatView.waitAllRequests()
    assertThat(view.myLogcatView.text).isEqualTo("test logcat message 2")
  }
}