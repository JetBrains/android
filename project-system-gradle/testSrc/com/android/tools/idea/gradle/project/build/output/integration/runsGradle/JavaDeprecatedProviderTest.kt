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
import com.android.tools.idea.gradle.project.build.quickFixes.DescribedOpenGradleJdkSettingsQuickfix
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
import com.android.tools.idea.testing.buildAndWait
import com.android.tools.idea.testing.findModule
import com.google.common.truth.Truth.assertThat
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.MessageEvent.Kind.ERROR
import com.intellij.build.events.MessageEvent.Kind.WARNING
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.pom.java.LanguageLevel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class JavaDeprecatedProviderTest {
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
    val events = getBuildIssues(LanguageLevel.JDK_1_6, expectSuccess = false)
    verifyEventWasCreated(events, sourceMessage, ERROR, OpenSourceCompatibilityLinkQuickFix::class.java)
    verifyEventWasCreated(events, targetMessage, ERROR, OpenTargetCompatibilityLinkQuickFix::class.java)
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
    val events = getBuildIssues(LanguageLevel.JDK_1_7, expectSuccess = true)
    verifyEventWasCreated(events, sourceMessage, WARNING, OpenSourceCompatibilityLinkQuickFix::class.java)
    verifyEventWasCreated(events, targetMessage, WARNING, OpenTargetCompatibilityLinkQuickFix::class.java)
  }

  /**
   * Verify that using Java 8 language level does not cause any build issues
   */
  @Test
  fun testJava8NoBuildIssues() {
    val events = getBuildIssues(LanguageLevel.JDK_1_8, expectSuccess = true)
    assertThat(events).isEmpty()
  }

  private fun verifyEventWasCreated(events: List<BuildIssueEvent>,
                                    expectedMessage: String,
                                    expectedKind: MessageEvent.Kind,
                                    compatibilityFix: Class<out OpenLinkDescribedQuickFix>) {
    assertThat(events.map { it.message }).contains(expectedMessage)
    val event = events.find { it.message == expectedMessage }
    assertThat(event).isNotNull()
    assertThat(event!!.kind).isEqualTo(expectedKind)
    val issue = event.issue
    assertThat(issue).isNotNull()
    val fixes = issue!!.quickFixes
    assertThat(fixes.map { it.javaClass }).containsExactly(
      SetJavaLanguageLevelAllQuickFix::class.java,
      DescribedOpenGradleJdkSettingsQuickfix::class.java,
      PickLanguageLevelInPSDQuickFix::class.java,
      compatibilityFix,
      OpenJavaLanguageSpecQuickFix::class.java
    ).inOrder()
    assertThat((fixes[0] as SetJavaLanguageLevelAllQuickFix).level).isEqualTo(LanguageLevel.JDK_1_8)
  }

  private fun getBuildIssues(languageLevel: LanguageLevel, expectSuccess: Boolean): MutableList<BuildIssueEvent> {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    val events: MutableList<BuildIssueEvent> = mutableListOf()
    preparedProject.open { project ->
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
        if (event is BuildIssueEvent) events.add(event)
      }) { buildInvoker ->
        buildInvoker.rebuild()
      }
      assertThat(result.isBuildSuccessful).isEqualTo(expectSuccess)
    }
    return events
  }
}