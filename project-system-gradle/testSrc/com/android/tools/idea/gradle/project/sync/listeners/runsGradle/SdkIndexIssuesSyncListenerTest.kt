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
package com.android.tools.idea.gradle.project.sync.listeners.runsGradle

import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker.cleanAfterTesting
import com.android.tools.analytics.UsageTracker.setWriterForTest
import com.android.tools.idea.gradle.model.IdeSyncIssue
import com.android.tools.idea.gradle.project.sync.listeners.SdkIndexIssuesSyncListener
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.PreparedTestProject.Companion.openTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.projectsystem.gradle.IdeGooglePlaySdkIndex
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.requestSyncAndWait
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.SDK_INDEX_PROJECT_STATS
import com.intellij.notification.Notification
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.spy
import org.mockito.Mockito.`when`
import kotlin.time.Duration.Companion.seconds

class SdkIndexIssuesSyncListenerTest {
  private val blockingOutdated = "com.google.android.gms:play-services-ads-lite:9.8.0"
  private val blockingCritical = "com.google.android.play:app-update:2.0.0"
  private val blockingPolicyOutdated = "com.flurry.android:analytics:13.0.0"
  private val blockingOutdatedVulnerability = "com.adobe.marketing.mobile:core:1.5.1"
  private val nonBlockingCritical = "com.google.android.gms:play-services-safetynet:10.0.0"
  private val blockingCriticalDeprecated = "com.google.android.play:core:1.10.3"

  @get:Rule
  val projectRule = AndroidProjectRule.withIntegrationTestEnvironment()

  private val scheduler = VirtualTimeScheduler()
  private val testUsageTracker = TestUsageTracker(scheduler)

  @Before
  fun initializeSdkIndexAndTracker() {
    IdeGooglePlaySdkIndex.initialize(null)
    IdeGooglePlaySdkIndex.showDeprecationIssues = true
    setWriterForTest(testUsageTracker)
  }

  @After
  fun cleanTracker() {
    cleanAfterTesting()
  }

  private fun consumeLatestNotification(project: Project): Notification? {
    return runBlocking {
      withTimeout(80.seconds) {
        project.service<SdkIndexIssuesSyncListener.EventStreamForTesting>().notifications.receive()
      }
    }
  }

  @Test
  fun `Notification is not created when issues are not present`() {
    projectRule.openTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION) { project -> // Check no notifications were created
      assertThat(consumeLatestNotification(project)).isNull()
      checkProjectStats(0, 0, 0, 0, 0, 0, 0)
    }
  }

  @Test
  fun `Notification is created when critical issues are present`() {
    verifyIssues(listOf(blockingCritical), 1, 1, 0, 1, 0, 0, 0)
  }

  @Test
  fun `Notification is created when outdated issues are present`() {
    verifyIssues(listOf(blockingOutdated), 1, 1, 0, 0, 0, 1, 0)
  }

  @Test
  fun `Notification is created when policy and outdated issues are present in a single dependency`() {
    verifyIssues(listOf(blockingPolicyOutdated), 1, 1, 1, 0, 0, 1, 0)
  }

  @Test
  fun `Notification is created when outdated and vulnerability issues are present in a single dependency`() {
    verifyIssues(listOf(blockingOutdatedVulnerability), 1, 1, 0, 0, 1, 1, 0)
  }

  @Test
  fun `Notification is created when multiple dependencies have issues`() {
    verifyIssues(listOf(blockingPolicyOutdated, blockingCritical, blockingOutdated, blockingOutdatedVulnerability, nonBlockingCritical), 4, 4, 1, 2, 1, 3, 0)
  }

  @Test
  fun `Notification is created with blocking deprecations`() {
    verifyIssues(listOf(blockingCriticalDeprecated), 1, 1, 0, 1, 0, 0, 1)
  }

  @Test
  fun `Notification is not shown for non blocking issues`() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    val buildFile = preparedProject.root.resolve("app").resolve("build.gradle")
    val originalBuildContent = buildFile.readText()
    buildFile.writeText(originalBuildContent + """
      dependencies {
        implementation("$nonBlockingCritical")
      }
    """)
    preparedProject.open(updateOptions = {it.copy(expectedSyncIssues = setOf(IdeSyncIssue.TYPE_UNRESOLVED_DEPENDENCY))}) { project ->
      assertThat(consumeLatestNotification(project)).isNull()
      checkProjectStats(0, 0, 0, 1, 0, 0, 0)
    }
  }

  @Test
  fun `Verify notification is shown once`() {
    val expectedContent = "There are 1 SDKs with warnings that will prevent app release in Google Play Console"
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    // Add a dependency that has issues
    val buildFile = preparedProject.root.resolve("app").resolve("build.gradle")
    val originalBuildContent = buildFile.readText()
    buildFile.writeText(originalBuildContent + """
      dependencies {
        implementation("$blockingOutdated")
      }
    """)
    preparedProject.open(updateOptions = {it.copy(expectedSyncIssues = setOf(IdeSyncIssue.TYPE_UNRESOLVED_DEPENDENCY))}) { project ->
      // Check a notification was created
      val notification = consumeLatestNotification(project)
      assertThat(notification).isNotNull()
      assertThat(notification!!.content).isEqualTo(expectedContent)
      checkProjectStats(1, 1, 0, 0, 0, 1, 0)
      // Dismiss notification
      notification.expire()
      // Notification should not be generated again
      project.requestSyncAndWait()
      checkProjectStats(1, 1, 0, 0, 0, 1, 0)
      assertThat(consumeLatestNotification(project)).isNull()
      // Remove dependency
      buildFile.writeText(originalBuildContent)
      project.requestSyncAndWait()
      // Verify notification was not shown
      assertThat(consumeLatestNotification(project)).isNull()
      checkProjectStats(0, 0, 0, 0, 0, 0, 0)
      // Add dependency back
      buildFile.writeText(originalBuildContent + """
        dependencies {
          implementation("$blockingOutdated")
        }
      """)
      project.requestSyncAndWait()
      // Verify notification was shown
      val secondNotification = consumeLatestNotification(project)
      assertThat(secondNotification).isNotNull()
      assertThat(secondNotification!!.content).isEqualTo(expectedContent)
      checkProjectStats(1, 1, 0, 0, 0, 1, 0)
    }
  }

  @Test
  fun `Disposed project does not show notification`() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    val buildFile = preparedProject.root.resolve("app").resolve("build.gradle")
    val originalBuildContent = buildFile.readText()
    buildFile.writeText(originalBuildContent + """
      dependencies {
        implementation("$blockingCritical")
      }
    """)
    preparedProject.open(updateOptions = {it.copy(expectedSyncIssues = setOf(IdeSyncIssue.TYPE_UNRESOLVED_DEPENDENCY))}) { project ->
      val notification = consumeLatestNotification(project)
      assertThat(notification).isNotNull()
      checkProjectStats(1, 1, 0, 1, 0, 0, 0)
      // Dismiss notification
      notification!!.expire()

      val testListener = SdkIndexIssuesSyncListener(MainScope())
      val spyProject = spy(project)
      testListener.wasNotificationShown = false

      // Disposed while waiting for SDK index to be ready
      `when`(spyProject.isDisposed).thenReturn(false, true, true)
      testListener.syncSucceeded(spyProject, project.projectFilePath!!)
      assertThat(consumeLatestNotification(spyProject)).isNull()

      // Disposed already when calling syncSucceeded
      `when`(spyProject.isDisposed).thenReturn(true)
      testListener.syncSucceeded(spyProject, project.projectFilePath!!)
      assertThat(consumeLatestNotification(spyProject)).isNull()
    }
  }

  private fun verifyIssues(dependencies: List<String>, numErrorsAndWarnings: Int, numBlockingIssues: Int, numPolicyIssues: Int, numCriticalIssues: Int, numVulnerabilities: Int, numOutdatedIssues: Int, numDeprecatedIssues: Int) {
    val expectedContent = "There are $numBlockingIssues SDKs with warnings that will prevent app release in Google Play Console"
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    // Add a dependencies
    val buildFile = preparedProject.root.resolve("app").resolve("build.gradle")
    val originalBuildContent = buildFile.readText()
    val dependencyBlock = StringBuilder()
    dependencyBlock.appendLine("dependencies {")
    for (dependency in dependencies) {
      dependencyBlock.appendLine("  implementation(\"$dependency\")")
    }
    dependencyBlock.appendLine("}")
    buildFile.writeText("$originalBuildContent\n$dependencyBlock")
    preparedProject.open(updateOptions = {it.copy(expectedSyncIssues = setOf(IdeSyncIssue.TYPE_UNRESOLVED_DEPENDENCY))}) {
      // Check a notification was created
      val notification = consumeLatestNotification(project)
      assertThat(notification).isNotNull()
      assertThat(notification!!.content).isEqualTo(expectedContent)
      checkProjectStats(numErrorsAndWarnings, numBlockingIssues, numPolicyIssues, numCriticalIssues, numVulnerabilities, numOutdatedIssues, numDeprecatedIssues)
      notification.expire()
    }
  }

  private fun checkProjectStats(numErrorsAndWarnings: Int, numBlockingIssues: Int, numPolicyIssues: Int, numCriticalIssues: Int, numVulnerabilities: Int, numOutdatedIssues: Int, numDeprecatedIssues: Int) {
    val statsEvents = testUsageTracker.usages.map {it.studioEvent }.filter { it.kind == SDK_INDEX_PROJECT_STATS }
    assertThat(statsEvents).hasSize(1)
    val projectStats = statsEvents[0].sdkIndexProjectStats
    assertThat(projectStats).isNotNull()
    assertThat(projectStats.numErrorsAndWarnings).isEqualTo(numErrorsAndWarnings)
    assertThat(projectStats.numBlockingIssues).isEqualTo(numBlockingIssues)
    assertThat(projectStats.numPolicyIssues).isEqualTo(numPolicyIssues)
    assertThat(projectStats.numCriticalIssues).isEqualTo(numCriticalIssues)
    assertThat(projectStats.numVulnerabilityIssues).isEqualTo(numVulnerabilities)
    assertThat(projectStats.numOutdatedIssues).isEqualTo(numOutdatedIssues)
    assertThat(projectStats.numDeprecatedIssues).isEqualTo(numDeprecatedIssues)
    testUsageTracker.usages.clear()
  }
}