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

import com.android.tools.idea.gradle.model.IdeSyncIssue
import com.android.tools.idea.gradle.project.sync.listeners.SdkIndexIssuesSyncListener
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.PreparedTestProject.Companion.openTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.projectsystem.gradle.IdeGooglePlaySdkIndex
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.requestSyncAndWait
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SdkIndexIssuesSyncListenerTest {
  private val blockingDependency = "com.google.android.gms:play-services-ads-lite:9.8.0"

  @get:Rule
  val projectRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @Before
  fun initializeSdkIndex() {
    IdeGooglePlaySdkIndex.initialize(null)
  }

  @Test
  fun `Notification is not created when issues are not present`() {
    projectRule.openTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION) { project -> // Check no notifications were created
      val listener = SdkIndexIssuesSyncListener()
      assertThat(listener.notifyBlockingIssuesIfNeeded(project, IdeGooglePlaySdkIndex)).isNull()
    }
  }

  @Test
  fun `Notification is created when issues are present`() {
    val expectedContent = "There are 1 SDKs with warnings that will prevent app release in Google Play Console"
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    // Add a dependency that has issues
    val buildFile = preparedProject.root.resolve("app").resolve("build.gradle")
    buildFile.writeText(buildFile.readText() + """
      dependencies {
        implementation("$blockingDependency")
      }
    """)
    preparedProject.open(updateOptions = {it.copy(expectedSyncIssues = setOf(IdeSyncIssue.TYPE_UNRESOLVED_DEPENDENCY))}) {
      // Check a notification was created
      val listener = SdkIndexIssuesSyncListener()
      val notification = listener.notifyBlockingIssuesIfNeeded(project, IdeGooglePlaySdkIndex)
      assertThat(notification).isNotNull()
      assertThat(notification!!.content).isEqualTo(expectedContent)
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
        implementation("$blockingDependency")
      }
    """)
    preparedProject.open(updateOptions = {it.copy(expectedSyncIssues = setOf(IdeSyncIssue.TYPE_UNRESOLVED_DEPENDENCY))}) { project ->
      // Check a notification was created
      val listener = SdkIndexIssuesSyncListener()
      val notification = listener.notifyBlockingIssuesIfNeeded(project, IdeGooglePlaySdkIndex)
      assertThat(notification).isNotNull()
      assertThat(notification!!.content).isEqualTo(expectedContent)
      // Dismiss notification
      notification.expire()
      // Notification should not be generated again
      assertThat(listener.notifyBlockingIssuesIfNeeded(project, IdeGooglePlaySdkIndex)).isNull()
      // Remove dependency
      buildFile.writeText(originalBuildContent)
      project.requestSyncAndWait()
      // Verify notification was not shown
      assertThat(listener.notifyBlockingIssuesIfNeeded(project, IdeGooglePlaySdkIndex)).isNull()
      // Add dependency back
      buildFile.writeText(originalBuildContent + """
        dependencies {
          implementation("$blockingDependency")
        }
      """)
      project.requestSyncAndWait()
      // Verify notification was shown
      val secondNotification = listener.notifyBlockingIssuesIfNeeded(project, IdeGooglePlaySdkIndex)
      assertThat(secondNotification).isNotNull()
      assertThat(secondNotification!!.content).isEqualTo(expectedContent)
    }
  }
}