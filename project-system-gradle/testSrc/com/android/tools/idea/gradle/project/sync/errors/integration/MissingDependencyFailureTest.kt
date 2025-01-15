/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.errors.integration

import com.android.builder.model.v2.ide.SyncIssue
import com.android.tools.idea.gradle.plugin.AgpVersions.latestKnown
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.issues.GradleExceptionAnalyticsSupport.GradleError
import com.android.tools.idea.gradle.project.sync.issues.GradleExceptionAnalyticsSupport.GradleException
import com.android.tools.idea.gradle.project.sync.issues.GradleExceptionAnalyticsSupport.GradleFailureDetails
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.replaceContent
import com.android.tools.idea.testing.AndroidGradleTests
import com.android.tools.idea.testing.buildAndWait
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.BuildErrorMessage
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.FinishBuildEventImpl
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.common.waitUntil
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.kotlin.utils.filterIsInstanceAnd
import org.jetbrains.plugins.gradle.issue.UnresolvedDependencyIssue
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit

class MissingDependencyFailureTest : AbstractIssueCheckerIntegrationTest() {

  @Test
  fun wantedDependencyExcludedFromAllRepos() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)

    val buildGradle = preparedProject.root.resolve("build.gradle")
    buildGradle.replaceContent {
      // excluding artifact from all repositories results in error without "Searched in the following locations:" block.
      it.replace("maven {", "maven {\ncontent { excludeModule(\"androidx.databinding\", \"databinding-common\") }")
    }

    runSyncAndCheckGeneralFailure(
      preparedProject = preparedProject,
      verifySyncViewEvents = { _, buildEvents ->
        // Expect single BuildIssueEvent generated in GradleBuildScriptErrorParser.checkUnresolvedDependencyError
        buildEvents.filterIsInstance<MessageEvent>().let { events ->
          expect.that(events).hasSize(1)
          events.firstOrNull()?.let {
            expect.that(it).isInstanceOf(BuildIssueEvent::class.java)
            expect.that(it.message).isEqualTo("Could not resolve androidx.databinding:databinding-common:$latestKnown")
            expect.that(it.description).isEqualTo("""
              |A problem occurred configuring root project 'project'.
              |> Could not resolve all artifacts for configuration 'classpath'.
              |   > Could not find androidx.databinding:databinding-common:$latestKnown.
              |     Required by:
              |         root project : > com.android.tools.build:gradle:$latestKnown
              |         root project : > com.android.tools.build:gradle:$latestKnown > androidx.databinding:databinding-compiler-common:$latestKnown
              |
              |Possible solution:
              | - Declare repository providing the artifact, see the documentation at https://docs.gradle.org/current/userguide/declaring_repositories.html
              |""".trimMargin())
            expect.that((it as? BuildIssueEvent)?.issue).isInstanceOf(UnresolvedDependencyIssue::class.java)
            // When not offline mode no quickfixes are expected.
            expect.that((it as? BuildIssueEvent)?.issue?.quickFixes).isEmpty()
          }
        }
        // Make sure no additional failures are generated
        expect.that(buildEvents.finishEventFailures()).isEmpty()
      },
      verifyFailureReported = {
        expect.that(it.gradleSyncFailure).isEqualTo(AndroidStudioEvent.GradleSyncFailure.MISSING_DEPENDENCY_OTHER)
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
              exception: org.gradle.api.internal.artifacts.ivyservice.TypedResolveException
                at: [0]org.gradle.api.internal.artifacts.ResolveExceptionMapper#mapFailure
              exception: org.gradle.internal.resolve.ModuleVersionNotFoundException
                at: no info
            }
          }
        """.trimIndent())
      }
    )
  }

  @Test
  fun wantedNonExistentDependency() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)

    val buildGradle = preparedProject.root.resolve("build.gradle")
    buildGradle.appendText("\nbuildscript { dependencies { classpath 'my.not.existing.dependency:gradle:1.2.3-dev' }}")

    runSyncAndCheckGeneralFailure(
      preparedProject = preparedProject,
      verifySyncViewEvents = { _, buildEvents ->
        // Expect single BuildIssueEvent generated in GradleBuildScriptErrorParser.checkUnresolvedDependencyError
        buildEvents.filterIsInstance<MessageEvent>().let { events ->
          expect.that(events).hasSize(1)
          events.firstOrNull()?.let {
            expect.that(it).isInstanceOf(BuildIssueEvent::class.java)
            expect.that(it.message).isEqualTo("Could not resolve my.not.existing.dependency:gradle:1.2.3-dev")
            expect.that(it.description).startsWith("""
              |A problem occurred configuring root project 'project'.
              |> Could not resolve all artifacts for configuration 'classpath'.
              |   > Could not find my.not.existing.dependency:gradle:1.2.3-dev.
              |     Searched in the following locations:
              """.trimMargin())
            expect.that(it.description).endsWith("""
              |     Required by:
              |         root project :
              |
              |Possible solution:
              | - Declare repository providing the artifact, see the documentation at https://docs.gradle.org/current/userguide/declaring_repositories.html
              |""".trimMargin())
            expect.that((it as? BuildIssueEvent)?.issue).isInstanceOf(UnresolvedDependencyIssue::class.java)
            // When not offline mode no quickfixes are expected.
            expect.that((it as? BuildIssueEvent)?.issue?.quickFixes).isEmpty()
          }
        }
        // Make sure no additional failures are generated
        expect.that(buildEvents.finishEventFailures()).isEmpty()
      },
      verifyFailureReported = {
        expect.that(it.gradleSyncFailure).isEqualTo(AndroidStudioEvent.GradleSyncFailure.MISSING_DEPENDENCY_OTHER)
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
              exception: org.gradle.api.internal.artifacts.ivyservice.TypedResolveException
                at: [0]org.gradle.api.internal.artifacts.ResolveExceptionMapper#mapFailure
              exception: org.gradle.internal.resolve.ModuleVersionNotFoundException
                at: no info
            }
          }
        """.trimIndent())
      }
    )
  }

  @Test
  fun nonExistentDependencyInOfflineMode_subsequentSync() {
    val buildEvents = ContainerUtil.createConcurrentList<BuildEvent>()
    // Two syncs are expected in this test and we need to wait for both.
    val firstSyncAllBuildEventsProcessedLatch = CountDownLatch(1)
    val allBuildEventsProcessedLatch = CountDownLatch(2)
    projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION).open(
      updateOptions = {
        it.copy(
          syncViewEventHandler = { buildEvent ->
            buildEvents.add(buildEvent)
            // Events are generated in a separate thread(s) and if we don't wait for the FinishBuildEvent
            // some might not reach here by the time we inspect them below resulting in flakiness (like b/318490086).
            if (buildEvent is FinishBuildEventImpl) {
              firstSyncAllBuildEventsProcessedLatch.countDown()
              allBuildEventsProcessedLatch.countDown()
            }
          })
      }
    ) {
      // Wait for seeing finish events for first sync.
      firstSyncAllBuildEventsProcessedLatch.await(10, TimeUnit.SECONDS)

      // Add new non-existent dependency and repository to the buildscript.
      // It is fine to add mavenCentral repository here because we will run sync in offline mode.
      val appBuildFile = VfsUtil.findFileByIoFile(this.projectRoot.resolve("app/build.gradle"), true)!!
      runWriteActionAndWait {
        with(appBuildFile) {
          val text = VfsUtil.loadText(this) + "\nbuildscript { " +
            "\nrepositories { mavenCentral() } " +
            "\ndependencies { classpath 'my.not.existing.dependency:gradle:1.2.3-dev' }" +
            "}"
          setBinaryContent(text.toByteArray())
        }
      }
      GradleSettings.getInstance(project).isOfflineWork = true

      buildEvents.clear()
      AndroidGradleTests.syncProject(project, GradleSyncInvoker.Request.testRequest()) {
        Truth.assertWithMessage("Sync should fail.").that(AndroidGradleTests.syncFailed(it)).isTrue()
      }

      // Wait for seeing finish events for both syncs.
      allBuildEventsProcessedLatch.await(10, TimeUnit.SECONDS)

      buildEvents.filterIsInstance<MessageEvent>().let { events ->
        expect.that(events).hasSize(1)
        events.firstOrNull()?.let {
          expect.that(it).isInstanceOf(BuildIssueEvent::class.java)
          expect.that(it.message).isEqualTo("Could not resolve my.not.existing.dependency:gradle:1.2.3-dev")
          expect.that(it.description).isEqualTo("""
            |A problem occurred configuring project ':app'.
            |> Could not resolve all artifacts for configuration 'classpath'.
            |   > Could not resolve my.not.existing.dependency:gradle:1.2.3-dev.
            |     Required by:
            |         project :app
            |      > No cached version of my.not.existing.dependency:gradle:1.2.3-dev available for offline mode.
            |
            |Possible solution:
            | - <a href="disable_offline_mode">Disable offline mode and rerun the build</a>
            |""".trimMargin())
          expect.that((it as? BuildIssueEvent)?.issue).isInstanceOf(UnresolvedDependencyIssue::class.java)
          // In offline mode toggle offline mode quickfix is expected.
          expect.that((it as? BuildIssueEvent)?.issue?.quickFixes).hasSize(1)
          expect.that((it as? BuildIssueEvent)?.issue?.quickFixes?.firstOrNull()?.javaClass?.name)
            .isEqualTo("org.jetbrains.plugins.gradle.issue.UnresolvedDependencyBuildIssue\$DisableOfflineAndRerun")
        }
      }
      // TODO (b/355417764): Currently we also generate issue from CachedDependencyNotFoundIssueChecker in this case.
      //  This is to be fixed in the following changes soon.
      expect.that(buildEvents.finishEventFailures()).hasSize(1)

    }

    val reportedFailureDetails = usageTracker.usages
      .filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE_DETAILS }
    expect.that(reportedFailureDetails).hasSize(1)
    reportedFailureDetails.map { it.studioEvent }.firstOrNull()?.let {
      expect.that(it.gradleSyncFailure).isEqualTo(AndroidStudioEvent.GradleSyncFailure.CACHED_DEPENDENCY_NOT_FOUND)
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
            exception: org.gradle.api.internal.artifacts.ivyservice.TypedResolveException
              at: [0]org.gradle.api.internal.artifacts.ResolveExceptionMapper#mapFailure
            exception: org.gradle.internal.resolve.ModuleVersionResolveException
              at: no info
            exception: org.gradle.internal.resolve.ModuleVersionResolveException
              at: no info
          }
        }
        """.trimIndent())
    }
  }
}