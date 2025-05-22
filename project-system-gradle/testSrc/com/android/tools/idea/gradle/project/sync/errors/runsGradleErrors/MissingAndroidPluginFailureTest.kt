/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.errors.runsGradleErrors

import com.android.tools.idea.gradle.plugin.AgpVersions.latestKnown
import com.android.tools.idea.gradle.project.sync.errors.AddGoogleMavenRepositoryQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenPluginBuildFileQuickFix
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.replaceContent
import com.android.tools.idea.util.toIoFile
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.BuildErrorMessage
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.events.FileMessageEvent
import com.intellij.build.events.MessageEvent
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.testFramework.PlatformTestUtil
import org.junit.Test

class MissingAndroidPluginFailureTest : AbstractIssueCheckerIntegrationTest() {

  @Test
  fun missingAgpArtifactWithOldPluginApplyDsl() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)

    val buildGradle = preparedProject.root.resolve("build.gradle")
    buildGradle.replaceContent {
      it.replace("maven {", "maven {\ncontent { excludeModule(\"com.android.tools.build\", \"gradle\") }")
    }

    runSyncAndCheckBuildIssueFailure(
      preparedProject = preparedProject,
      verifyBuildIssue = { project, buildIssue ->
        expect.that(buildIssue).isNotNull()
        expect.that(buildIssue.title).isEqualTo("Gradle Sync issues.")
        expect.that(buildIssue.description).startsWith("Could not find com.android.tools.build:gradle:")
        expect.that(buildIssue.quickFixes).hasSize(2)
        buildIssue.quickFixes[0].let {
          expect.that(buildIssue.description).contains("<a href=\"${it.id}\">Add google Maven repository and sync project</a>")
          expect.that(it).isInstanceOf(AddGoogleMavenRepositoryQuickFix::class.java)
        }
        buildIssue.quickFixes[1].let {
          expect.that(buildIssue.description).contains("<a href=\"${it.id}\">Open File</a>")
          expect.that(it).isInstanceOf(OpenPluginBuildFileQuickFix::class.java)
          PlatformTestUtil.waitForFuture(it.runQuickFix(project, SimpleDataContext.getProjectContext(project)))
          expect.that(FileEditorManagerEx.getInstanceEx(project).currentFile?.toIoFile()).isEqualTo(buildGradle)
        }
      },
      expectedFailureReported = AndroidStudioEvent.GradleSyncFailure.MISSING_DEPENDENCY_COM_ANDROID_TOOLS_BUILD_GRADLE,
      expectedPhasesReported = """
        FAILURE : SYNC_TOTAL/GRADLE_CONFIGURE_ROOT_BUILD
        FAILURE : SYNC_TOTAL
      """.trimIndent(),
      expectedFailureDetailsString = """
        failure {
          error {
            exception: org.gradle.tooling.BuildActionFailureException
              at: [0]org.gradle.tooling.internal.consumer.connection.PhasedActionAwareConsumerConnection#run
            exception: org.gradle.api.ProjectConfigurationException
              at: [0]org.gradle.configuration.project.LifecycleProjectEvaluator#wrapException
            exception: org.gradle.api.internal.artifacts.ivyservice.TypedResolveException
              at: [0]org.gradle.api.internal.artifacts.ResolveExceptionMapper#mapFailure
            exception: org.gradle.internal.resolve.ModuleVersionNotFoundException
              at: no info
          }
        }
      """.trimIndent()
    )
  }

  @Test
  fun missingAgpArtifactWithPluginDsl() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION_PLUGINS_DSL)

    val buildGradle = preparedProject.root.resolve("build.gradle")
    buildGradle.replaceContent {
      it.replace("maven {", "maven {\ncontent { excludeModule(\"com.android.tools.build\", \"gradle\") }")
    }
    val settingsGradle = preparedProject.root.resolve("settings.gradle")
    settingsGradle.replaceContent {
      it.replace("maven {", "maven {\ncontent { excludeModule(\"com.android.tools.build\", \"gradle\") }")
    }

    runSyncAndCheckBuildIssueFailure(
      preparedProject = preparedProject,
      verifyBuildIssue = { project, buildIssue ->
        expect.that(buildIssue).isNotNull()
        expect.that(buildIssue.title).isEqualTo("Gradle Sync issues.")
        expect.that(buildIssue.description).startsWith("Could not find com.android.tools.build:gradle:")
        expect.that(buildIssue.quickFixes).hasSize(2)
        buildIssue.quickFixes[0].let {
          expect.that(buildIssue.description).contains("<a href=\"${it.id}\">Add google Maven repository and sync project</a>")
          expect.that(it).isInstanceOf(AddGoogleMavenRepositoryQuickFix::class.java)
        }
        buildIssue.quickFixes[1].let {
          expect.that(buildIssue.description).contains("<a href=\"${it.id}\">Open File</a>")
          expect.that(it).isInstanceOf(OpenPluginBuildFileQuickFix::class.java)
          PlatformTestUtil.waitForFuture(it.runQuickFix(project, SimpleDataContext.getProjectContext(project)))
          expect.that(FileEditorManagerEx.getInstanceEx(project).currentFile?.toIoFile()).isEqualTo(buildGradle)
        }
      },
      expectedFailureReported = AndroidStudioEvent.GradleSyncFailure.MISSING_DEPENDENCY_COM_ANDROID_TOOLS_BUILD_GRADLE,
      expectedPhasesReported = """
        FAILURE : SYNC_TOTAL/GRADLE_CONFIGURE_ROOT_BUILD
        FAILURE : SYNC_TOTAL
      """.trimIndent(),
      expectedFailureDetailsString = """
        failure {
          error {
            exception: org.gradle.tooling.BuildActionFailureException
              at: [0]org.gradle.tooling.internal.consumer.connection.PhasedActionAwareConsumerConnection#run
            exception: org.gradle.api.ProjectConfigurationException
              at: [0]org.gradle.configuration.project.LifecycleProjectEvaluator#wrapException
            exception: org.gradle.api.internal.artifacts.ivyservice.TypedResolveException
              at: [0]org.gradle.api.internal.artifacts.ResolveExceptionMapper#mapFailure
            exception: org.gradle.internal.resolve.ModuleVersionNotFoundException
              at: no info
          }
        }
      """.trimIndent()
    )
  }

  @Test
  fun missingAgpPluginID() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION_PLUGINS_DSL)

    val buildGradle = preparedProject.root.resolve("build.gradle")
    val settingsGradle = preparedProject.root.resolve("settings.gradle")
    settingsGradle.replaceContent {
      it.replace("maven {", "maven {\ncontent { excludeModule(\"com.android.application\", \"com.android.application.gradle.plugin\") }")
    }

    runSyncAndCheckGeneralFailure(
      preparedProject = preparedProject,
      verifySyncViewEvents = { _, buildEvents ->
        // Expect single FileMessageEvent on Sync Output
        buildEvents.filterIsInstance<MessageEvent>().let { events ->
          expect.that(events).hasSize(1)
          events.firstOrNull()?.let {
            expect.that(it).isInstanceOf(FileMessageEvent::class.java)
            expect.that(it.message).contains("Plugin [id: 'com.android.application', version: '$latestKnown', apply: false] was not found in any of the following sources:")
            expect.that((it as? FileMessageEvent)?.filePosition?.file).isEqualTo(buildGradle)
          }
        }
        // Make sure no additional error events are generated
        expect.that(buildEvents.filterIsInstance<BuildIssueEvent>()).isEmpty()
        expect.that(buildEvents.finishEventFailures()).isEmpty()
      },
      verifyFailureReported = {
        expect.that(it.gradleSyncFailure).isEqualTo(AndroidStudioEvent.GradleSyncFailure.UNKNOWN_PLUGIN_COM_ANDROID)
        expect.that(it.buildOutputWindowStats.buildErrorMessagesList.map { it.errorShownType })
          .containsExactly(BuildErrorMessage.ErrorType.UNKNOWN_ERROR_TYPE)
        expect.that(it.gradleSyncStats.printPhases()).isEqualTo("""
          FAILURE : SYNC_TOTAL/GRADLE_CONFIGURE_ROOT_BUILD
          FAILURE : SYNC_TOTAL
        """.trimIndent())
        Truth.assertThat(it.gradleFailureDetails.toTestString()).isEqualTo("""
          failure {
            error {
              exception: org.gradle.tooling.BuildActionFailureException
                at: [0]org.gradle.tooling.internal.consumer.connection.PhasedActionAwareConsumerConnection#run
              exception: org.gradle.api.ProjectConfigurationException
                at: [0]org.gradle.configuration.project.LifecycleProjectEvaluator#wrapException
              exception: org.gradle.internal.exceptions.LocationAwareException
                at: [0]org.gradle.plugin.use.resolve.internal.PluginResolutionResult#getFound
              exception: org.gradle.api.plugins.UnknownPluginException
                at: [0]org.gradle.plugin.use.resolve.internal.PluginResolutionResult#getFound
            }
          }
        """.trimIndent())
      }
    )
  }
}