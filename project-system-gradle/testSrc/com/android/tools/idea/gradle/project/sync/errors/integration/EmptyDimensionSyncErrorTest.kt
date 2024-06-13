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

import com.android.SdkConstants
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.testing.AndroidGradleTests
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.impl.FinishBuildEventImpl
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.vfs.VfsUtil
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class EmptyDimensionSyncErrorTest {

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

  @Test
  fun testSyncErrorOnEmptyFavorDimension_firstSync() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)

    val buildFile = preparedProject.root.resolve("app").resolve(SdkConstants.FN_BUILD_GRADLE)
    buildFile.appendText(
      """
        // Line needed to ensure line break
        android {
          flavorDimensions 'flv_dim1', 'flv_dim3'
          productFlavors {
            flv1 {
                dimension 'flv_dim1'
            }
          }
        }
      """.trimIndent()
    )

    var capturedException: Exception? = null
    val buildEvents = mutableListOf<BuildEvent>()
    val allBuildEventsProcessedLatch = CountDownLatch(1)

    preparedProject.open(
      updateOptions = {
        it.copy(
          verifyOpened = { project ->
            Truth.assertThat(project.getProjectSystem().getSyncManager().getLastSyncResult())
              .isEqualTo(ProjectSystemSyncManager.SyncResult.FAILURE)
          },
          syncExceptionHandler = { e: Exception ->
            capturedException = e
          },
          syncViewEventHandler = { buildEvent ->
            buildEvents.add(buildEvent)
            if (buildEvent is FinishBuildEventImpl) {
              allBuildEventsProcessedLatch.countDown()
            }
          }
        )
      }
    ) {
      allBuildEventsProcessedLatch.await(10, TimeUnit.SECONDS)
    }

    Truth.assertThat(capturedException?.message).startsWith("No variants found for ':app'. Check ${buildFile.absolutePath} to ensure at least one variant exists and address any sync warnings and errors.")

    val failureEvents = usageTracker.usages
      .filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE_DETAILS }
      .map { it.studioEvent.gradleSyncFailure }
    Truth.assertThat(failureEvents).containsExactly(AndroidStudioEvent.GradleSyncFailure.ANDROID_SYNC_NO_VARIANTS_FOUND)
    Truth.assertThat(usageTracker.usages.filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.GRADLE_SYNC_ISSUES }).isEmpty()
  }

  @Test
  fun testSyncErrorOnEmptyFavorDimension_subsequentSync() {
    println(">>> start _subsequentSync test " + Thread.currentThread())
    val allBuildEventsProcessedLatch = CountDownLatch(2) // Two syncs are expected in this test and we need to wait for both.
    projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION).open(
      updateOptions = {
        it.copy(
          syncViewEventHandler = { buildEvent ->
            // Events are generated in a separate thread(s) and if we don't wait for the FinishBuildEvent
            // some might not reach here by the time we inspect them below resulting in flakiness (like b/318490086).
            if (buildEvent is FinishBuildEventImpl) {
              allBuildEventsProcessedLatch.countDown()
            }
          })
      }
    ) {
      val buildFile = VfsUtil.findFileByIoFile(this.projectRoot.resolve("app/build.gradle"), true)!!
      runWriteActionAndWait {
        val buildFileText = VfsUtil.loadText(buildFile) + "\n" + """
          // Line needed to ensure line break
          android {
            flavorDimensions 'flv_dim1', 'flv_dim3'
            productFlavors {
              flv1 {
                  dimension 'flv_dim1'
              }
            }
          }
        """.trimIndent()
        buildFile.setBinaryContent(buildFileText.toByteArray())
      }

      AndroidGradleTests.syncProject(project, GradleSyncInvoker.Request.testRequest()) {
        Truth.assertWithMessage("Sync should fail.").that(AndroidGradleTests.syncFailed(it)).isTrue()
      }

      // Wait for seeing finish events for both syncs.
      allBuildEventsProcessedLatch.await(10, TimeUnit.SECONDS)
    }

    val failureEvents = usageTracker.usages
      .filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE_DETAILS }
      .map { it.studioEvent.gradleSyncFailure }
    Truth.assertThat(failureEvents).containsExactly(AndroidStudioEvent.GradleSyncFailure.ANDROID_SYNC_NO_VARIANTS_FOUND)

    val issuesEvents = usageTracker.usages
      .filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.GRADLE_SYNC_ISSUES }
    Truth.assertThat(issuesEvents.map { it.studioEvent.gradleSyncIssuesList.map { issue -> issue.type } })
      .containsExactly(listOf(AndroidStudioEvent.GradleSyncIssueType.TYPE_EMPTY_FLAVOR_DIMENSION))
  }
}