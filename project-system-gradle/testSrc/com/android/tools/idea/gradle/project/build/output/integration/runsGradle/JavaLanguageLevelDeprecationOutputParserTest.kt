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
package com.android.tools.idea.gradle.project.build.output.integration.runsGradle

import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.project.build.output.DescribedOpenGradleJdkSettingsQuickfix
import com.android.tools.idea.gradle.project.build.quickFixes.OpenJavaLanguageSpecQuickFix
import com.android.tools.idea.gradle.project.build.quickFixes.OpenSourceCompatibilityLinkQuickFix
import com.android.tools.idea.gradle.project.build.quickFixes.OpenTargetCompatibilityLinkQuickFix
import com.android.tools.idea.gradle.project.build.quickFixes.PickLanguageLevelInPSDQuickFix
import com.android.tools.idea.gradle.project.sync.idea.issues.OpenLinkDescribedQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.SetJavaLanguageLevelAllQuickFix
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.JdkUtils.getEmbeddedJdkPathWithVersion
import com.android.tools.idea.testing.buildAndWait
import com.android.tools.idea.testing.findModule
import com.google.common.truth.Expect
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.BuildErrorMessage.ErrorType.UNKNOWN_ERROR_TYPE
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.events.FailureResult
import com.intellij.build.events.FinishBuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.MessageEvent.Kind.ERROR
import com.intellij.build.events.MessageEvent.Kind.WARNING
import com.intellij.build.events.impl.FinishBuildEventImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.containers.ContainerUtil
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch

@RunsInEdt
class JavaLanguageLevelDeprecationOutputParserTest {
  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()
  private val usageTracker = TestUsageTracker(VirtualTimeScheduler())

  @Before
  fun setUp() {
    UsageTracker.setWriterForTest(usageTracker)
  }

  @After
  fun cleanUp() {
    usageTracker.close()
    UsageTracker.cleanAfterTesting()
  }

  /**
   * Verify that using Java 6 language level causes a build issue (error) that has the following quick fixes:
   * -SetJavaLanguageLevelAllQuickFix
   * -PickLanguageLevelInPSDQuickFix
   * -Open<Source|Target>CompatibilityLinkQuickFix
   * -OpenJavaLanguageSpecQuickFix
   */
  @Test
  fun testJava6CausesError() {
    val sourceMessage = "Source option 6 is no longer supported. Use 7 or later."
    val targetMessage = "Target option 6 is no longer supported. Use 7 or later."

    val buildEvents = getBuildIssues(JavaSdkVersion.JDK_17, LanguageLevel.JDK_1_6, expectSuccess = false)

    buildEvents.filterIsInstance<MessageEvent>().let { events ->
      expect.that(events).hasSize(2)
      verifyBuildIssue(events[0] as BuildIssueEvent, sourceMessage, ERROR, OpenSourceCompatibilityLinkQuickFix::class.java)
      verifyBuildIssue(events[1] as BuildIssueEvent, targetMessage, ERROR, OpenTargetCompatibilityLinkQuickFix::class.java)
    }
    expect.that(buildEvents.finishEventFailures()).isEmpty()

    val reportedFailureDetails = usageTracker.usages
      .filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_OUTPUT_WINDOW_STATS }
    expect.that(reportedFailureDetails).hasSize(1)
    reportedFailureDetails.map { it.studioEvent }.firstOrNull()?.let {
      // TODO add proper error type for this.
      expect.that(it.buildOutputWindowStats.buildErrorMessagesList.map { it.errorShownType })
        .containsExactly(UNKNOWN_ERROR_TYPE, UNKNOWN_ERROR_TYPE)
    }
  }

  /**
   * Verify that using Java 7 language level causes a build issue (warning) that has the following quick fixes:
   * -SetJavaLanguageLevelAllQuickFix
   * -PickLanguageLevelInPSDQuickFix
   * -Open<Source|Target>CompatibilityLinkQuickFix
   * -OpenJavaLanguageSpecQuickFix
   */
  @Test
  fun testJava7CausesWarning() {
    val sourceMessage = "[options] source value 7 is obsolete and will be removed in a future release"
    val targetMessage = "[options] target value 7 is obsolete and will be removed in a future release"
    val suppressMessage = "[options] To suppress warnings about obsolete options, use -Xlint:-options."
    val buildEvents = getBuildIssues(JavaSdkVersion.JDK_17, LanguageLevel.JDK_1_7, expectSuccess = true)

    buildEvents.filterIsInstance<MessageEvent>().let { events ->
      expect.that(events).hasSize(9)
      verifyBuildIssue(events[0] as BuildIssueEvent, sourceMessage, WARNING, OpenSourceCompatibilityLinkQuickFix::class.java)
      verifyBuildIssue(events[1] as BuildIssueEvent, targetMessage, WARNING, OpenTargetCompatibilityLinkQuickFix::class.java)
      verifyMessage(events[2], suppressMessage, WARNING)
      verifyBuildIssue(events[3] as BuildIssueEvent, sourceMessage, WARNING, OpenSourceCompatibilityLinkQuickFix::class.java)
      verifyBuildIssue(events[4] as BuildIssueEvent, targetMessage, WARNING, OpenTargetCompatibilityLinkQuickFix::class.java)
      verifyMessage(events[5], suppressMessage, WARNING)
      verifyBuildIssue(events[6] as BuildIssueEvent, sourceMessage, WARNING, OpenSourceCompatibilityLinkQuickFix::class.java)
      verifyBuildIssue(events[7] as BuildIssueEvent, targetMessage, WARNING, OpenTargetCompatibilityLinkQuickFix::class.java)
      verifyMessage(events[8], suppressMessage, WARNING)
    }
    expect.that(buildEvents.finishEventFailures()).isEmpty()
  }

  /**
   * Verify that using Java 8 language level does not cause any build issues
   */
  @Test
  fun testJava8NoBuildIssues() {
    val buildEvents = getBuildIssues(JavaSdkVersion.JDK_17, LanguageLevel.JDK_1_8, expectSuccess = true)
    // Make sure no error or warning events are generated
    expect.that(buildEvents.filterIsInstance<MessageEvent>()).isEmpty()
    expect.that(buildEvents.finishEventFailures()).isEmpty()
  }

  private fun verifyBuildIssue(event: BuildIssueEvent,
                               expectedMessage: String,
                               expectedKind: MessageEvent.Kind,
                               compatibilityFix: Class<out OpenLinkDescribedQuickFix>) {
    expect.that(event.kind).isEqualTo(expectedKind)
    expect.that(event.message).isEqualTo(expectedMessage)
    expect.that(event.issue).isNotNull()
    event.issue?.let { issue ->
      val fixes = issue.quickFixes
      expect.that(fixes.map { it.javaClass }).containsExactly(
        SetJavaLanguageLevelAllQuickFix::class.java,
        DescribedOpenGradleJdkSettingsQuickfix::class.java,
        PickLanguageLevelInPSDQuickFix::class.java,
        compatibilityFix,
        OpenJavaLanguageSpecQuickFix::class.java
      ).inOrder()
      expect.that((fixes[0] as SetJavaLanguageLevelAllQuickFix).level).isEqualTo(LanguageLevel.JDK_1_8)
    }
  }

  private fun verifyMessage(event: MessageEvent,
                            expectedMessage: String,
                            expectedKind: MessageEvent.Kind) {
    expect.that(event.kind).isEqualTo(expectedKind)
    expect.that(event.message).isEqualTo(expectedMessage)
  }

  private fun getBuildIssues(javaSdkVersion: JavaSdkVersion,
                             languageLevel: LanguageLevel,
                             expectSuccess: Boolean): MutableList<BuildEvent> {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)

    val buildEvents = ContainerUtil.createConcurrentList<BuildEvent>()
    val allBuildEventsProcessedLatch = CountDownLatch(1)
    preparedProject.open(
      updateOptions = {
        it.copy(
          overrideProjectGradleJdkPath = getEmbeddedJdkPathWithVersion(javaSdkVersion)
        )
      }
    ) { project ->
      // Prepare app module to use LanguageLevel 6
      val projectBuildModel = ProjectBuildModel.get(project)
      val appModule = project.findModule("app")
      val moduleModel = projectBuildModel.getModuleBuildModel(appModule)
      assertThat(moduleModel).isNotNull()
      val android = moduleModel!!.android()
      val compileOptions = android.compileOptions()
      compileOptions.sourceCompatibility().setLanguageLevel(languageLevel)
      compileOptions.targetCompatibility().setLanguageLevel(languageLevel)
      ApplicationManager.getApplication().invokeAndWait {
        WriteCommandAction.runWriteCommandAction(project) {
          projectBuildModel.applyChanges()
        }
      }
      // Build
      val result = project.buildAndWait(eventHandler = { event ->
        if (event !is BuildIssueEvent && event !is MessageEvent && event !is FinishBuildEvent) return@buildAndWait
        buildEvents.add(event)
        // Events are generated in a separate thread(s) and if we don't wait for the FinishBuildEvent
        // some might not reach here by the time we inspect them below resulting in flakiness (like b/318490086).
        if (event is FinishBuildEventImpl) {
          allBuildEventsProcessedLatch.countDown()
        }
      }) { buildInvoker ->
        buildInvoker.rebuild()
      }
      assertThat(result.isBuildSuccessful).isEqualTo(expectSuccess)
    }
    return buildEvents
  }

  private fun List<BuildEvent>.finishEventFailures() = (filterIsInstance<FinishBuildEvent>().single().result as? FailureResult)?.failures
                                                       ?: emptyList()
}