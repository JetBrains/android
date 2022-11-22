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
package com.android.tools.idea.projectsystem.gradle

import com.android.testutils.TestUtils
import com.android.tools.asdriver.tests.AndroidProject
import com.android.tools.asdriver.tests.AndroidStudio
import com.android.tools.asdriver.tests.AndroidSystem
import com.android.tools.asdriver.tests.MavenRepo
import com.android.tools.asdriver.tests.MemoryDashboardNameProviderWatcher
import com.android.tools.lint.checks.GooglePlaySdkIndex
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.intellij.util.io.exists
import org.junit.Rule
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.concurrent.thread

open class SdkIndexTestBase {
  private val snapshotPath = "system/sdk_index/snapshot.gz"
  private val localSnapshotFolder = "tools/adt/idea/project-system-integration-tests/testData/snapshot/"
  private val testProjectPath = "tools/adt/idea/project-system-integration-tests/testData/sdkindexapp"
  private val testRepoManifest = "tools/adt/idea/project-system-integration-tests/sdkindexproject_deps.manifest"
  private val snapshotTimeoutSeconds: Long = 60
  private val timeoutPsdShowSeconds: Long = 60
  // TODO(b/243691427): Change the way we confirm no more issues were created, on test runs it is around 10 ms between issues
  private val timeoutBetweenIssuesSeconds: Long = 2

  @JvmField
  @Rule
  val system: AndroidSystem = AndroidSystem.standard()

  @JvmField
  @Rule
  var watcher = MemoryDashboardNameProviderWatcher()

  fun verifySdkIndexIsInitializedAndUsedWhen(showFunction: ((studio: AndroidStudio, project: AndroidProject) -> Unit)?,
                                             beforeClose: (() -> Unit)?,
                                             expectedIssues: Set<String>) {
    val project = AndroidProject(testProjectPath)
    project.setDistribution("tools/external/gradle/gradle-7.3.3-bin.zip")
    // Create a maven repo and set it up in the installation and environment
    system.installRepo(MavenRepo(testRepoManifest))

    // Change URL for snapshot to a local file
    val snapshotFolder = TestUtils.resolveWorkspacePath(localSnapshotFolder).toUri().toURL()
    system.setEnv(GooglePlaySdkIndex.SDK_INDEX_SNAPSHOT_TEST_BASE_URL_ENV_VAR, snapshotFolder.toString())

    system.runStudio(project, watcher.dashboardName) { studio ->
      studio.waitForSync()
      studio.waitForIndex()
      // Check that the snapshot is not yet present
      val indexPath = system.installation.workDir.normalize().resolve(snapshotPath)
      assertWithMessage("SDK index snapshot ($indexPath) should not exist before opening build file").that(indexPath.exists()).isFalse()

      showFunction?.invoke(studio, project)
      system.installation.ideaLog.waitForMatchingLine(".*SDK Index data loaded correctly.*", snapshotTimeoutSeconds, TimeUnit.SECONDS)

      // Check that the snapshot now exists
      assertWithMessage("SDK index snapshot ($indexPath) should exist after opening build file").that(indexPath.exists()).isTrue()
      // Check lint caused SDK Index to look for issues
      for (issue in expectedIssues) {
        val escapedIssue = ".*${Pattern.quote(issue)}.*"
        system.installation.ideaLog.waitForMatchingLine(escapedIssue, null, true, snapshotTimeoutSeconds, TimeUnit.SECONDS)
      }
      // Now check that only expected issues were present
      val foundIssues: MutableSet<String> = mutableSetOf()
      while (true) {
        try {
          // TODO(b/243691427): Change the way we confirm no more issues were created
          val matcher = system.installation.ideaLog.waitForMatchingLine(".*IdeGooglePlaySdkIndex - (.*)$", timeoutBetweenIssuesSeconds,
                                                                        TimeUnit.SECONDS)
          foundIssues.add(matcher.group(1))
        }
        catch (expected: InterruptedException) {
          // This means that no more matches were found
          break
        }
      }
      beforeClose?.invoke()
      assertThat(foundIssues).isEqualTo(expectedIssues)
    }
  }

  protected fun verifyPsdIssues(numErrors: Int) {
    val summaryRegex = ".*PsAnalyzerDaemon - Issues recreated: (.*)$"
    // The test project uses the following libraries:
    //  - com.startapp:inapp-sdk: 3.9.1 error (blocking critical)
    //  - com.stripe:stripe-android:9.3.2 error (if policy issues are enabled)
    //  - com.mopub:mopub-sdk:4.16.0 warning (outdated)
    //  - com.snowplowanalytics:snowplow-android-tracker:1.4.1 info (critical, but not blocking)
    val expectedSummary = "$numErrors errors, 1 warnings, 1 information, 0 updates, 0 other"
    val foundSummary: String
    try {
      val matcher = system.installation.ideaLog.waitForMatchingLine(summaryRegex, timeoutBetweenIssuesSeconds, TimeUnit.SECONDS)
      foundSummary = matcher.group(1)
    }
    catch (exc: InterruptedException) {
      print("Test failed because no summary message was found")
      throw exc
    }
    assertThat(foundSummary).isEqualTo(expectedSummary)
  }

  protected fun openAndClosePSD(studio: AndroidStudio) {
    thread(start = true) {
      try {
        // Wait for summary to appear
        val summaryRegex = ".*PsAnalyzerDaemon - Issues recreated: (.*)$"
        system.installation.ideaLog.waitForMatchingLine(summaryRegex, null,true, timeoutPsdShowSeconds, TimeUnit.SECONDS)
      }
      finally {
        // Close PSD even if summary could not be found so executeAction("ShowProjectStructureSettings") is finished
        studio.invokeComponent("OK")
      }
    }
    studio.executeAction("ShowProjectStructureSettings")
  }
}
