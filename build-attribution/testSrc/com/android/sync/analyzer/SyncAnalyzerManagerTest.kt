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
package com.android.sync.analyzer

import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.testing.AndroidGradleTests
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.requestSyncAndWait
import com.android.tools.idea.testing.saveAndDump
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.RunsInEdt
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class SyncAnalyzerManagerTest {
  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  private val tracker = TestUsageTracker(VirtualTimeScheduler())

  @Before
  fun setup() {
    UsageTracker.setWriterForTest(tracker)
  }

  @After
  fun cleanUp() {
    UsageTracker.cleanAfterTesting()
  }

  /**
   * With current initial implementation there is not much we can test about this service.
   * What we can test is that both after successful and failed syncs:
   *  - Metrics where sent with GradleSyncStats;
   *  - Data was cleared from the cache.
   * We don't care about the content of the gathered stats here, this is covered in detail in DownloadsAnalyzer tests.
   */
  @Test
  fun testDataClearedAfterSyncs() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)

    preparedProject.open { project ->
      // Empty after sync 1.
      Truth.assertThat(project.getService (SyncAnalyzerDataManager::class.java).idToData).isEmpty()

      project.requestSyncAndWait()

      // Empty after sync 2.
      Truth.assertThat(project.getService (SyncAnalyzerDataManager::class.java).idToData).isEmpty()

      val buildFile = VfsUtil.findFileByIoFile(preparedProject.root.resolve("app/build.gradle"), true)!!
      runWriteAction {
        buildFile.setBinaryContent("*bad*".toByteArray())
      }
      AndroidGradleTests.syncProject(project, GradleSyncInvoker.Request.testRequest()) {
        // Do not check status.
      }

      // Empty after sync 3.
      Truth.assertThat(project.getService (SyncAnalyzerDataManager::class.java).idToData).isEmpty()

      val syncEvents = tracker.usages
        .filter { it.studioEvent.category == AndroidStudioEvent.EventCategory.GRADLE_SYNC }
      // Print events for the reference
      syncEvents.forEach { println("==${it.studioEvent.kind}\n" + it.studioEvent.gradleSyncStats.toString()) }

      val syncSetupStartedEvents = tracker.usages
        .filter {
          it.studioEvent.kind == AndroidStudioEvent.EventKind.GRADLE_SYNC_ENDED ||
          it.studioEvent.kind  == AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE
        }
        .map { "${it.studioEvent.kind} downloadsDataEmpty=${it.studioEvent.gradleSyncStats.downloadsData?.repositoriesList?.isEmpty() ?: "null"}" }
        .joinToString(separator = "\n")
      Truth.assertThat(syncSetupStartedEvents).isEqualTo("""
        GRADLE_SYNC_ENDED downloadsDataEmpty=true
        GRADLE_SYNC_ENDED downloadsDataEmpty=true
        GRADLE_SYNC_FAILURE downloadsDataEmpty=true
      """.trimIndent())
    }
  }
}