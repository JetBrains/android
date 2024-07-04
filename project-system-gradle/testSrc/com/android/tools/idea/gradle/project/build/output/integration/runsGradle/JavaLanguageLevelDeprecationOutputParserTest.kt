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

import com.android.SdkConstants
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.project.build.output.DescribedOpenGradleJdkSettingsQuickfix
import com.android.tools.idea.gradle.project.build.quickFixes.OpenBuildJdkInfoLinkQuickFix
import com.android.tools.idea.gradle.project.build.quickFixes.OpenJavaLanguageSpecQuickFix
import com.android.tools.idea.gradle.project.build.quickFixes.OpenSourceCompatibilityLinkQuickFix
import com.android.tools.idea.gradle.project.build.quickFixes.OpenTargetCompatibilityLinkQuickFix
import com.android.tools.idea.gradle.project.build.quickFixes.PickLanguageLevelInPSDQuickFix
import com.android.tools.idea.gradle.project.sync.idea.issues.OpenLinkDescribedQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.SetJavaLanguageLevelAllQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.SetJavaToolchainQuickFix
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.PreparedTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.JdkUtils.getEmbeddedJdkPathWithVersion
import com.android.tools.idea.testing.buildAndWait
import com.android.tools.idea.testing.findModule
import com.google.common.truth.Expect
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.BuildErrorMessage
import com.google.wireless.android.sdk.stats.BuildErrorMessage.ErrorType.JAVA_NOT_SUPPORTED_LANGUAGE_LEVEL
import com.google.wireless.android.sdk.stats.BuildErrorMessage.ErrorType.UNKNOWN_ERROR_TYPE
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.events.FailureResult
import com.intellij.build.events.FinishBuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.MessageEvent.Kind.ERROR
import com.intellij.build.events.MessageEvent.Kind.WARNING
import com.intellij.build.events.impl.FinishBuildEventImpl
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.containers.ContainerUtil
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.fail

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
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    val buildEvents = preparedProject.getBuildIssues(JavaSdkVersion.JDK_17, LanguageLevel.JDK_1_6, expectSuccess = false)

    assertThat(buildEvents.printEvents()).isEqualTo("""
root > [Task :app:compileDebugJavaWithJavac] > ERROR:'Java compiler has removed support for compiling with source/target compatibility version 6.'
root > 'failed'
""".trimIndent())

    sequenceOf(
      "root > [Task :app:compileDebugJavaWithJavac] > ERROR:'Java compiler has removed support for compiling with source/target compatibility version 6.'",
    ).map { buildEvents.findBuildEvent(it) }.forEach { event ->
      assertThat(event.description).isEqualTo("""
        Java compiler has removed support for compiling with source/target compatibility version 6.
        <a href="set.java.toolchain.8">Set Java Toolchain to 8</a>
        <a href="set.java.level.JDK_1_8.all">Change Java language level and jvmTarget to 8 in all modules if using a lower level.</a>
        <a href="PickLanguageLevelInPSD">Pick a different compatibility level...</a>
        <a href="open.gradle.jdk.settings">Pick a different JDK to run Gradle...</a>
        <a href="OpenBuildJdkInfoLinkQuickFix">More information...</a>
      """.trimIndent())
      event.verifyQuickfix("set.java.toolchain.8") {
        with(it as SetJavaToolchainQuickFix) {
          expect.that(versionToSet).isEqualTo(8)
          expect.that(gradleModules).containsExactly(":app")
        }
      }
      event.verifyQuickfix("set.java.level.JDK_1_8.all") {
        expect.that((it as SetJavaLanguageLevelAllQuickFix).level).isEqualTo(LanguageLevel.JDK_1_8)
      }
    }

    verifyStats(JAVA_NOT_SUPPORTED_LANGUAGE_LEVEL)
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
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    val buildEvents = preparedProject.getBuildIssues(JavaSdkVersion.JDK_17, LanguageLevel.JDK_1_7, expectSuccess = true)

    assertThat(buildEvents.printEvents()).isEqualTo("""
root > [Task :app:compileDebugJavaWithJavac] > WARNING:'Java compiler has deprecated support for compiling with source/target compatibility version 7.'
root > [Task :app:compileDebugUnitTestJavaWithJavac] > WARNING:'Java compiler has deprecated support for compiling with source/target compatibility version 7.'
root > [Task :app:compileDebugAndroidTestJavaWithJavac] > WARNING:'Java compiler has deprecated support for compiling with source/target compatibility version 7.'
root > 'finished'
""".trimIndent())

    sequenceOf(
      "root > [Task :app:compileDebugJavaWithJavac] > WARNING:'Java compiler has deprecated support for compiling with source/target compatibility version 7.'",
      "root > [Task :app:compileDebugUnitTestJavaWithJavac] > WARNING:'Java compiler has deprecated support for compiling with source/target compatibility version 7.'",
      "root > [Task :app:compileDebugAndroidTestJavaWithJavac] > WARNING:'Java compiler has deprecated support for compiling with source/target compatibility version 7.'"
    ).map { buildEvents.findBuildEvent(it) }.forEach { event ->
      assertThat(event.description).isEqualTo("""
        Java compiler has deprecated support for compiling with source/target compatibility version 7.
        <a href="set.java.toolchain.11">Set Java Toolchain to 11</a>
        <a href="set.java.level.JDK_1_8.all">Change Java language level and jvmTarget to 8 in all modules if using a lower level.</a>
        <a href="PickLanguageLevelInPSD">Pick a different compatibility level...</a>
        <a href="open.gradle.jdk.settings">Pick a different JDK to run Gradle...</a>
        <a href="OpenBuildJdkInfoLinkQuickFix">More information...</a>
      """.trimIndent())
      event.verifyQuickfix("set.java.toolchain.11") {
        with(it as SetJavaToolchainQuickFix) {
          expect.that(versionToSet).isEqualTo(11)
          expect.that(gradleModules).containsExactly(":app")
        }
      }
      event.verifyQuickfix("set.java.level.JDK_1_8.all") {
        expect.that((it as SetJavaLanguageLevelAllQuickFix).level).isEqualTo(LanguageLevel.JDK_1_8)
      }
    }

    assertThat(buildEvents.finishEventFailures()).isEmpty()
    verifyNoStats()
  }

  /**
   * Verify that using Java 8 language level does not cause any build issues
   */
  @Test
  fun testJava8NoBuildIssues() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    val buildEvents = preparedProject.getBuildIssues(JavaSdkVersion.JDK_17, LanguageLevel.JDK_1_8, expectSuccess = true)
    // Make sure no error or warning events are generated
    assertThat(buildEvents.filterIsInstance<MessageEvent>()).isEmpty()
    assertThat(buildEvents.finishEventFailures()).isEmpty()
    verifyNoStats()
  }

  @Test
  fun testJava7OnJDK21() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    val buildEvents = preparedProject.getBuildIssues(JavaSdkVersion.JDK_21, LanguageLevel.JDK_1_7, expectSuccess = false)
    assertThat(buildEvents.printEvents()).isEqualTo("""
root > [Task :app:compileDebugJavaWithJavac] > ERROR:'Java compiler version 21 has removed support for compiling with source/target version 7'
root > 'failed'
""".trimIndent())

    buildEvents.findBuildEvent(
      "root > [Task :app:compileDebugJavaWithJavac] > ERROR:'Java compiler version 21 has removed support for compiling with source/target version 7'"
    ).let { event ->
      assertThat(event.description).isEqualTo("""
Execution failed for task ':app:compileDebugJavaWithJavac'.
> Java compiler version 21 has removed support for compiling with source/target version 7.
  Try one of the following options:
      1. [Recommended] Use Java toolchain with a lower language version
      2. Set a higher source/target version
      3. Use a lower version of the JDK running the build (if you're not using Java toolchain)
  For more details on how to configure these settings, see https://developer.android.com/build/jdks.

<a href="set.java.toolchain.11">Set Java Toolchain to 11</a>
<a href="set.java.level.JDK_11.all">Change Java language level and jvmTarget to 11 in all modules if using a lower level.</a>
<a href="PickLanguageLevelInPSD">Pick a different compatibility level...</a>
<a href="open.gradle.jdk.settings">Pick a different JDK to run Gradle...</a>
<a href="OpenBuildJdkInfoLinkQuickFix">More information...</a>
      """.trimIndent())
      event.verifyQuickfix("set.java.toolchain.11") {
        with(it as SetJavaToolchainQuickFix) {
          expect.that(versionToSet).isEqualTo(11)
          expect.that(gradleModules).containsExactly(":app")
        }
      }
      event.verifyQuickfix("set.java.level.JDK_11.all") {
        expect.that((it as SetJavaLanguageLevelAllQuickFix).level).isEqualTo(LanguageLevel.JDK_11)
      }
    }
    assertThat(buildEvents.finishEventFailures()).isEmpty()
    verifyStats(JAVA_NOT_SUPPORTED_LANGUAGE_LEVEL)
  }

  @Test
  fun testJava7OnJDK21WithJavaLib() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    preparedProject.root.resolve("lib").let { lib ->
      lib.mkdirs()
      lib.resolve(SdkConstants.FN_BUILD_GRADLE).writeText("""
        plugins {
            id 'java-library'
        }
        
        java {
            sourceCompatibility = JavaVersion.VERSION_1_7
            targetCompatibility = JavaVersion.VERSION_1_7
        }
      """.trimIndent())
      lib.resolve("src/main/java/org/example/").mkdirs()
      lib.resolve("src/main/java/org/example/LibClass.java").writeText("""
        package org.example;

        public class LibClass {}
      """.trimIndent())
    }
    preparedProject.root.resolve(SdkConstants.FN_SETTINGS_GRADLE).appendText("\ninclude ':lib'")

    val buildEvents = preparedProject.getBuildIssues(JavaSdkVersion.JDK_21, LanguageLevel.JDK_1_7, expectSuccess = false)

    assertThat(buildEvents.printEvents()).isEqualTo("""
root > [Task :lib:compileJava] > ERROR:'Java compiler has removed support for compiling with source/target compatibility version 7.'
root > 'failed'
""".trimIndent())
    sequenceOf(
      "root > [Task :lib:compileJava] > ERROR:'Java compiler has removed support for compiling with source/target compatibility version 7.'",
    ).map { buildEvents.findBuildEvent(it) }.forEach { event ->
      assertThat(event.description).isEqualTo("""
        Java compiler has removed support for compiling with source/target compatibility version 7.
        <a href="set.java.toolchain.11">Set Java Toolchain to 11</a>
        <a href="set.java.level.JDK_1_8.all">Change Java language level and jvmTarget to 8 in all modules if using a lower level.</a>
        <a href="PickLanguageLevelInPSD">Pick a different compatibility level...</a>
        <a href="open.gradle.jdk.settings">Pick a different JDK to run Gradle...</a>
        <a href="OpenBuildJdkInfoLinkQuickFix">More information...</a>
      """.trimIndent())
      event.verifyQuickfix("set.java.toolchain.11") {
        with(it as SetJavaToolchainQuickFix) {
          expect.that(versionToSet).isEqualTo(11)
          expect.that(gradleModules).containsExactly(":lib")
        }
      }
      event.verifyQuickfix("set.java.level.JDK_1_8.all") {
        expect.that((it as SetJavaLanguageLevelAllQuickFix).level).isEqualTo(LanguageLevel.JDK_1_8)
      }
    }
    expect.that(buildEvents.finishEventFailures()).isEmpty()

    verifyStats(JAVA_NOT_SUPPORTED_LANGUAGE_LEVEL)
  }

  @Test
  fun testJava8OnJDK21() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    val buildEvents = preparedProject.getBuildIssues(JavaSdkVersion.JDK_21, LanguageLevel.JDK_1_8, expectSuccess = true)
    assertThat(buildEvents.printEvents()).isEqualTo("""
root > [Task :app:compileDebugJavaWithJavac] > WARNING:'Java compiler has deprecated support for compiling with source/target compatibility version 8.'
root > [Task :app:compileDebugUnitTestJavaWithJavac] > WARNING:'Java compiler has deprecated support for compiling with source/target compatibility version 8.'
root > [Task :app:compileDebugAndroidTestJavaWithJavac] > WARNING:'Java compiler has deprecated support for compiling with source/target compatibility version 8.'
root > 'finished'
""".trimIndent())
    sequenceOf(
      "root > [Task :app:compileDebugJavaWithJavac] > WARNING:'Java compiler has deprecated support for compiling with source/target compatibility version 8.'",
      "root > [Task :app:compileDebugUnitTestJavaWithJavac] > WARNING:'Java compiler has deprecated support for compiling with source/target compatibility version 8.'",
      "root > [Task :app:compileDebugAndroidTestJavaWithJavac] > WARNING:'Java compiler has deprecated support for compiling with source/target compatibility version 8.'"
    ).map { buildEvents.findBuildEvent(it) }.forEach { event ->
      assertThat(event.description).isEqualTo("""
        Java compiler has deprecated support for compiling with source/target compatibility version 8.
        <a href="set.java.toolchain.17">Set Java Toolchain to 17</a>
        <a href="set.java.level.JDK_11.all">Change Java language level and jvmTarget to 11 in all modules if using a lower level.</a>
        <a href="PickLanguageLevelInPSD">Pick a different compatibility level...</a>
        <a href="open.gradle.jdk.settings">Pick a different JDK to run Gradle...</a>
        <a href="OpenBuildJdkInfoLinkQuickFix">More information...</a>
      """.trimIndent())
      event.verifyQuickfix("set.java.toolchain.17") {
        with(it as SetJavaToolchainQuickFix) {
          expect.that(versionToSet).isEqualTo(17)
          expect.that(gradleModules).containsExactly(":app")
        }
      }
      event.verifyQuickfix("set.java.level.JDK_11.all") {
        expect.that((it as SetJavaLanguageLevelAllQuickFix).level).isEqualTo(LanguageLevel.JDK_11)
      }
    }

    assertThat(buildEvents.finishEventFailures()).isEmpty()
    verifyNoStats()
  }

  private fun verifyNoStats() {
    val reportedFailureDetails = usageTracker.usages
      .filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_OUTPUT_WINDOW_STATS }
    assertThat(reportedFailureDetails).hasSize(0)
  }
  private fun verifyStats(vararg expectedMessages: BuildErrorMessage.ErrorType) {
    usageTracker.usages.map { it.studioEvent }.firstOrNull {
      it.kind == AndroidStudioEvent.EventKind.BUILD_OUTPUT_WINDOW_STATS
    }?.also {
      assertThat(it.buildOutputWindowStats.buildErrorMessagesList.map { it.errorShownType })
        .containsExactly(*expectedMessages)
    } ?: fail("No BUILD_OUTPUT_WINDOW_STATS event reported.")
  }

  private fun BuildEvent.verifyQuickfix(quickFixId: String, verify: (BuildIssueQuickFix) -> Unit) {
    findQuickfix(quickFixId)?.let {verify(it) } ?: expect.fail("Quickfix with id '$quickFixId' not found")
  }
  private fun List<BuildEvent>.printEvents(): String {
    return joinToString(separator = "\n") { it.toFullPathWithMessage() }
  }
  private fun BuildEvent.toFullPathWithMessage(): String {
    val parentPath = when (val parentId = parentId) {
      null -> "root"
      else -> "root > ${parentId.toString().substringAfter(" > ")}"
    }
    val kind = if (this is MessageEvent) "$kind:" else ""
    return "$parentPath > $kind'${message}'"
  }

  private fun List<BuildEvent>.findBuildEvent(eventPath: String): BuildEvent {
    return single { it.toFullPathWithMessage() == eventPath }
  }

  private fun BuildEvent.findQuickfix(quickfixId: String): BuildIssueQuickFix? {
    return (this as? BuildIssueEvent)?.issue?.quickFixes?.firstOrNull { it.id == quickfixId }
  }

  private fun PreparedTestProject.getBuildIssues(javaSdkVersion: JavaSdkVersion,
                                                 languageLevel: LanguageLevel,
                                                 expectSuccess: Boolean): List<BuildEvent> {
    return open(
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

      project.buildCollectingEvents(expectSuccess)
    }
  }

  private fun Project.buildCollectingEvents(expectSuccess: Boolean): List<BuildEvent> {
    val buildEvents = ContainerUtil.createConcurrentList<BuildEvent>()
    val allBuildEventsProcessedLatch = CountDownLatch(1)
    // Build
    val result = buildAndWait(eventHandler = { event ->
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
    allBuildEventsProcessedLatch.await(10, TimeUnit.SECONDS)
    return buildEvents
  }

  private fun List<BuildEvent>.finishEventFailures() = (filterIsInstance<FinishBuildEvent>().single().result as? FailureResult)?.failures
                                                       ?: emptyList()
}