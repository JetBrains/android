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
package com.android.tools.idea.insights.vcs

import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ExtensionTestUtil.maskExtensions
import com.intellij.testFramework.ProjectRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class VcsUtilsTest {
  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()

  @get:Rule val rules: RuleChain = RuleChain.outerRule(projectRule).around(disposableRule)

  private lateinit var fakeVcsForAppInsights: FakeVcsForAppInsights

  @Before
  fun setUp() {
    fakeVcsForAppInsights = FakeVcsForAppInsights()
    maskExtensions(
      VcsForAppInsights.EP_NAME,
      listOf(fakeVcsForAppInsights),
      disposableRule.disposable,
      false,
    )
  }

  @Test
  fun `create revision number`() {
    val revisionNumber = createRevisionNumber(fakeVcsForAppInsights.key, "123")
    assertThat(revisionNumber).isEqualTo(FakeVcsRevisionNumber("123"))
  }

  @Test
  fun `create short revision number`() {
    val revisionNumber = createShortRevisionString(fakeVcsForAppInsights.key, "123456789abcdefg")
    assertThat(revisionNumber).isEqualTo("12345678")
  }
}
