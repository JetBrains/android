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
package com.android.tools.idea.gradle.project.sync.errors

import com.android.mockito.kotlin.mockStatic
import com.android.mockito.kotlin.whenever
import com.android.tools.idea.gradle.project.build.output.TestMessageEventConsumer
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenPluginBuildFileQuickFix
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.BoundedTaskExecutor
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.MockedStatic
import java.util.concurrent.TimeUnit

@RunsInEdt
class MissingAndroidPluginIssueCheckerTest {
  private val missingAndroidPluginIssueChecker = MissingAndroidPluginIssueChecker()
  private lateinit var mockAppExecutorUtil: MockedStatic<AppExecutorUtil>
  private lateinit var executor: BoundedTaskExecutor

  val projectRule = AndroidGradleProjectRule()
  @get:Rule
  val rule: RuleChain = RuleChain.outerRule(EdtRule()).around(projectRule)
  val project by lazy { projectRule.project }

  @Before
  fun setup() {
    executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("TestService", 1) as BoundedTaskExecutor
    mockAppExecutorUtil = mockStatic()
    mockAppExecutorUtil.whenever<Any> {
      AppExecutorUtil.getAppExecutorService()
    }.thenReturn(executor)
  }

  @After
  fun teardown() {
    mockAppExecutorUtil.close()
  }

  @Test
  fun testCheckIssue() {
    val issueData = GradleIssueData(projectRule.project.basePath!!, Throwable("Could not find com.android.tools.build:gradle:"), null, null)
    val buildIssue = missingAndroidPluginIssueChecker.check(issueData)

    assertThat(buildIssue).isNotNull()
    assertThat(buildIssue!!.description).contains("Add google Maven repository and sync project")
    assertThat(buildIssue.description).contains("Open File")
    // Verify quickFixes
    assertThat(buildIssue.quickFixes).hasSize(2)
    assertThat(buildIssue.quickFixes[0]).isInstanceOf(AddGoogleMavenRepositoryQuickFix::class.java)
    assertThat(buildIssue.quickFixes[1]).isInstanceOf(OpenPluginBuildFileQuickFix::class.java)


    val future = buildIssue.quickFixes[0].runQuickFix(project, SimpleDataContext.getProjectContext(project))
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    executor.waitAllTasksExecuted(1, TimeUnit.SECONDS)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    assertThat(future.get(1, TimeUnit.SECONDS)).isNull()
  }

  @Test
  fun testCheckIssueHandled() {
    assertThat(
      missingAndroidPluginIssueChecker.consumeBuildOutputFailureMessage(
        "Build failed with Exception",
        "Could not find com.android.tools.build:gradle:",
        null,
        null,
        "",
        TestMessageEventConsumer()
      )).isTrue()
  }
}