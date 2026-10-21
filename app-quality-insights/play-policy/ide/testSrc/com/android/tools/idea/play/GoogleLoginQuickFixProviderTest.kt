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
package com.android.tools.idea.play

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.disposable
import com.android.tools.idea.testing.flags.overrideForTest
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.google.common.truth.Truth.assertThat
import com.google.gct.login2.LoginFeatureRule
import com.google.gct.login2.LoginUsersRule
import com.intellij.testFramework.ProjectRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.kotlin.mock

class GoogleLoginQuickFixProviderTest {

  private val projectRule = ProjectRule()
  private val loginUsersRule = LoginUsersRule()
  private val loginFeatureRule = LoginFeatureRule()

  @get:Rule val ruleChain: RuleChain = RuleChain.outerRule(projectRule).around(loginFeatureRule).around(loginUsersRule)

  private val playPolicyIssue =
    Issue.create(
      id = "PlayPolicyTestIssue",
      briefDescription = "Play Policy brief description",
      explanation = "Play Policy explanation",
      category = Category.create("Play Policy", 10),
      priority = 5,
      severity = Severity.WARNING,
      implementation = Implementation(Detector::class.java, Scope.EMPTY),
    )

  private val otherIssue =
    Issue.create(
      id = "OtherTestIssue",
      briefDescription = "Other brief description",
      explanation = "Other explanation",
      category = Category.CORRECTNESS,
      priority = 5,
      severity = Severity.WARNING,
      implementation = Implementation(Detector::class.java, Scope.EMPTY),
    )

  @Test
  fun testGetQuickFixes_flagDisabled() {
    StudioFlags.PLAY_POLICY_METADATA_EXPORT_ENABLED.overrideForTest(false, projectRule.disposable)
    val provider = GoogleLoginQuickFixProvider()
    val fixes = provider.getQuickFixes(playPolicyIssue, mock(), mock(), "message", null)
    assertThat(fixes).isEmpty()
  }

  @Test
  fun testGetQuickFixes_notPlayPolicyIssue() {
    StudioFlags.PLAY_POLICY_METADATA_EXPORT_ENABLED.overrideForTest(true, projectRule.disposable)
    val provider = GoogleLoginQuickFixProvider()
    val fixes = provider.getQuickFixes(otherIssue, mock(), mock(), "message", null)
    assertThat(fixes).isEmpty()
  }

  @Test
  fun testGetQuickFixes_alreadyLoggedIn() {
    StudioFlags.PLAY_POLICY_METADATA_EXPORT_ENABLED.overrideForTest(true, projectRule.disposable)
    StudioFlags.ENABLE_FSTS.overrideForTest(true, projectRule.disposable)
    loginUsersRule.setActiveUser("test@google.com")
    val provider = GoogleLoginQuickFixProvider()
    val fixes = provider.getQuickFixes(playPolicyIssue, mock(), mock(), "message", null)
    assertThat(fixes).isEmpty()
  }

  @Test
  fun testGetQuickFixes_returnsQuickFixWhenLoggedOut() {
    StudioFlags.PLAY_POLICY_METADATA_EXPORT_ENABLED.overrideForTest(true, projectRule.disposable)
    StudioFlags.ENABLE_FSTS.overrideForTest(true, projectRule.disposable)
    val provider = GoogleLoginQuickFixProvider()
    val fixes = provider.getQuickFixes(playPolicyIssue, mock(), mock(), "message", null)
    assertThat(fixes).hasLength(1)
    assertThat(fixes[0]).isInstanceOf(GoogleLoginQuickFixProvider.GoogleLoginIdeQuickFix::class.java)
    assertThat(fixes[0].name).isEqualTo("Sign in to Google Account")
  }

  @Test
  fun testGoogleLoginIdeQuickFix_startInWriteActionIsFalse() {
    val fix = GoogleLoginQuickFixProvider.GoogleLoginIdeQuickFix()
    assertThat(fix.startInWriteAction()).isFalse()
  }
}
