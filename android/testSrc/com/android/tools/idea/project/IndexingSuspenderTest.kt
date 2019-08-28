/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.project

import com.android.tools.idea.IdeInfo
import com.android.tools.idea.gradle.project.build.BuildContext
import com.android.tools.idea.gradle.project.build.BuildStatus
import com.android.tools.idea.gradle.project.build.GradleBuildState
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.gradle.util.BuildMode
import com.android.tools.idea.model.TestAndroidModel
import com.android.tools.idea.npw.model.MultiTemplateRenderer
import com.android.tools.idea.util.androidFacet
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.ide.file.BatchFileChangeListener
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.impl.stores.BatchUpdateListener
import com.intellij.openapi.project.Project
import com.intellij.testFramework.JavaProjectTestCase
import com.intellij.testFramework.replaceService
import com.intellij.util.messages.MessageBusConnection
import org.jetbrains.android.AndroidTestCase
import org.mockito.Mockito.mock

class IndexingSuspenderTest : JavaProjectTestCase() {
  private lateinit var batchUpdateConnection: MessageBusConnection
  private var expectedBatchUpdateCount = 0
  private var actualBatchUpdateCount = 0
  private var currentBatchUpdateLevel = 0
  private lateinit var batchFileUpdateConnection: MessageBusConnection
  private var expectedBatchFileUpdateCount = 0
  private var actualBatchFileUpdateCount = 0
  private var currentBatchFileUpdateLevel = 0

  /**
   * We don't intend to run IndexingSuspender and its tests outside of Android Studio context as of yet.
   *
   * @see IndexingSuspender.canActivate
   */
  private fun canRun() = IdeInfo.getInstance().isAndroidStudio

  override fun setUp() {
    super.setUp()

    if (!canRun()) {
      return
    }

    AndroidTestCase.addAndroidFacet(module)

    val indexingSuspenderService = IndexingSuspender(project, true)
    project.replaceService(IndexingSuspender::class.java, indexingSuspenderService, batchFileUpdateConnection)

    // Ensure the services are replaced globally as expected. If not, there is a bug in IdeComponents implementation,
    // and it doesn't make sense to execute this test further.
    assertSame(ServiceManager.getService(project, IndexingSuspender::class.java), indexingSuspenderService)

    currentBatchUpdateLevel = 0
    actualBatchUpdateCount = 0
    currentBatchFileUpdateLevel = 0
    actualBatchFileUpdateCount = 0
  }

  override fun tearDown() {
    if (!canRun()) {
      super.tearDown()
      return
    }

    try {
      verifyIndexingSpecificExpectations()
    }
    finally {
      super.tearDown()
    }
  }

  private fun verifyIndexingSpecificExpectations() {
    // verify batch update
    assertEquals("Did not unwind batch updates. Each batch update start must be paired with a corresponding finish call.",
                 0, currentBatchUpdateLevel)
    assertEquals("Batch update sessions were not started as many times as expected.",
                 expectedBatchUpdateCount, actualBatchUpdateCount)
    // verify batch file update
    assertEquals("Did not unwind batch file updates. Each batch update start must be paired with a corresponding finish call.",
                 0, currentBatchFileUpdateLevel)
    assertEquals("Batch file update sessions were not started as many times as expected.",
                 expectedBatchFileUpdateCount, actualBatchFileUpdateCount)
  }

  private fun setUpIndexingSpecificExpectations(batchUpdateCount: Int, batchFileUpdateCount: Int) {
    batchUpdateConnection = project.messageBus.connect(project)
    batchUpdateConnection.subscribe<BatchUpdateListener>(BatchUpdateListener.TOPIC, object : BatchUpdateListener {
      override fun onBatchUpdateStarted() {
        actualBatchUpdateCount += 1
        currentBatchUpdateLevel += 1
      }

      override fun onBatchUpdateFinished() {
        currentBatchUpdateLevel -= 1
      }
    })

    batchFileUpdateConnection = project.messageBus.connect(project)
    batchFileUpdateConnection.subscribe<BatchFileChangeListener>(BatchFileChangeListener.TOPIC, object : BatchFileChangeListener {
      override fun batchChangeStarted(project: Project, activityName: String?) {
        actualBatchFileUpdateCount += 1
        currentBatchFileUpdateLevel += 1
      }

      override fun batchChangeCompleted(project: Project) {
        currentBatchFileUpdateLevel -= 1
      }
    })

    expectedBatchUpdateCount = batchUpdateCount
    expectedBatchFileUpdateCount = batchFileUpdateCount
  }

  fun testGradleBuildSucceeded() {
    if (!canRun()) {
      return
    }

    doTestGradleBuildWhen(BuildStatus.SUCCESS)
  }

  fun testGradleBuildCancelled() {
    if (!canRun()) {
      return
    }

    doTestGradleBuildWhen(BuildStatus.CANCELED)
  }


  fun testGradleBuildFailed() {
    if (!canRun()) {
      return
    }

    doTestGradleBuildWhen(BuildStatus.FAILED)
  }

  fun testGradleBuildSkipped() {
    if (!canRun()) {
      return
    }

    setUpIndexingSpecificExpectations(batchUpdateCount = 0, batchFileUpdateCount = 0)

    val buildState = GradleBuildState.getInstance(project)
    buildState.buildFinished(BuildStatus.SKIPPED)
    assertFalse(buildState.isBuildInProgress)
  }

  fun testGradleSyncSucceeded() {
    if (!canRun()) {
      return
    }

    doTestGradleSyncWhen(failed = false)
  }

  fun testGradleSyncFailed() {
    if (!canRun()) {
      return
    }

    doTestGradleSyncWhen(failed = true)
  }

  fun testGradleSyncSkipped() {
    if (!canRun()) {
      return
    }

    setUpIndexingSpecificExpectations(batchUpdateCount = 0, batchFileUpdateCount = 0)

    val syncState = GradleSyncState.getInstance(project)
    syncState.syncSkipped(42)
    assertFalse(syncState.isSyncInProgress)
  }

  fun testGradleSyncAndBuild() {
    if (!canRun()) {
      return
    }

    // this tests the correct event handling when build is triggered during sync (e.g., source generation)
    setUpIndexingSpecificExpectations(batchUpdateCount = 1, batchFileUpdateCount = 1)
    val syncState = GradleSyncState.getInstance(project)
    syncState.syncStarted(true, GradleSyncInvoker.Request(GradleSyncStats.Trigger.TRIGGER_USER_REQUEST))
    syncState.setupStarted()

    val buildContext = BuildContext(project, listOf(":app:something"), BuildMode.DEFAULT_BUILD_MODE)
    val buildState = GradleBuildState.getInstance(project)
    val buildRequest = mock(GradleBuildInvoker.Request::class.java)
    // build is scheduled while sync is still in progress - IndexingSuspender should amend its deactivation condition
    buildState.buildExecutorCreated(buildRequest)
    syncState.syncEnded()

    assertFalse(syncState.isSyncInProgress)
    // must be still within a batch update session - sync has finished, but it should have not deactivated the suspender
    assertEquals(1, currentBatchUpdateLevel)
    assertEquals(1, currentBatchFileUpdateLevel)

    buildState.buildStarted(buildContext)

    // must be still within a batch update session - suspender is still active
    assertEquals(1, currentBatchUpdateLevel)
    assertEquals(1, currentBatchFileUpdateLevel)
    buildState.buildFinished(BuildStatus.SUCCESS)
    assertFalse(buildState.isBuildInProgress)
    assertEquals(0, currentBatchUpdateLevel)
    assertEquals(0, currentBatchFileUpdateLevel)
  }

  /**
   * Same as [testGradleSyncAndBuild] but checks that if there are namespaced modules after sync, Studio stays in dumb mode until the
   * build is finished.
   */
  fun testGradleSyncAndBuildNamespaced() {
    if (!canRun()) {
      return
    }

    setUpIndexingSpecificExpectations(batchUpdateCount = 1, batchFileUpdateCount = 1)
    val syncState = GradleSyncState.getInstance(project)
    syncState.syncStarted(true, GradleSyncInvoker.Request(GradleSyncStats.Trigger.TRIGGER_USER_REQUEST))

    // Perform "setup" by marking the module as namespaced:
    syncState.setupStarted()
    module.androidFacet!!.configuration.model = TestAndroidModel.namespaced(module.androidFacet!!)

    val buildContext = BuildContext(project, listOf(":app:something"), BuildMode.DEFAULT_BUILD_MODE)
    val buildState = GradleBuildState.getInstance(project)
    val buildRequest = mock(GradleBuildInvoker.Request::class.java)
    buildState.buildExecutorCreated(buildRequest)
    syncState.syncEnded()

    assertEquals(1, currentBatchUpdateLevel)
    assertEquals(1, currentBatchFileUpdateLevel)

    buildState.buildStarted(buildContext)
    assertEquals(1, currentBatchUpdateLevel)
    assertEquals(1, currentBatchFileUpdateLevel)

    buildState.buildFinished(BuildStatus.SUCCESS)
    assertFalse(buildState.isBuildInProgress)
    assertEquals(0, currentBatchUpdateLevel)
    assertEquals(0, currentBatchFileUpdateLevel)
  }

  fun testTemplateRenderingRegularEventsWorkflow() {
    setUpIndexingSpecificExpectations(batchUpdateCount = 1, batchFileUpdateCount = 1)

    MultiTemplateRenderer.multiRenderingStarted(project)
    // We should start a batch update no matter what.
    assertEquals(1, currentBatchUpdateLevel)
    assertEquals(1, currentBatchFileUpdateLevel)

    val syncState = GradleSyncState.getInstance(project)
    syncState.syncTaskCreated(mock(GradleSyncInvoker.Request::class.java))
    MultiTemplateRenderer.multiRenderingFinished(project)
    // Yes, rendering finished but we were notified that sync is imminent, so suspension should continue.
    assertEquals(1, currentBatchUpdateLevel)
    assertEquals(1, currentBatchFileUpdateLevel)

    syncState.syncStarted(true, GradleSyncInvoker.Request(GradleSyncStats.Trigger.TRIGGER_USER_REQUEST))
    // No change
    assertEquals(1, currentBatchUpdateLevel)
    assertEquals(1, currentBatchFileUpdateLevel)

    syncState.setupStarted()
    // No change
    assertEquals(1, currentBatchUpdateLevel)
    assertEquals(1, currentBatchFileUpdateLevel)

    val buildContext = mock(BuildContext::class.java)
    val buildState = GradleBuildState.getInstance(project)
    buildState.buildExecutorCreated(mock(GradleBuildInvoker.Request::class.java))
    // No change
    assertEquals(1, currentBatchUpdateLevel)
    assertEquals(1, currentBatchFileUpdateLevel)

    syncState.syncEnded()
    // No change
    assertEquals(1, currentBatchUpdateLevel)
    assertEquals(1, currentBatchFileUpdateLevel)

    buildState.buildStarted(buildContext)
    // No change even during build (for template-rendering initiated suspension only).
    assertEquals(1, currentBatchUpdateLevel)
    assertEquals(1, currentBatchFileUpdateLevel)

    buildState.buildFinished(BuildStatus.SUCCESS)
    assertEquals(0, currentBatchUpdateLevel)
    assertEquals(0, currentBatchFileUpdateLevel)
  }

  fun testTemplateRenderingWhenSyncFailed() {
    setUpIndexingSpecificExpectations(batchUpdateCount = 1, batchFileUpdateCount = 1)

    MultiTemplateRenderer.multiRenderingStarted(project)
    // We should start a batch update no matter what.
    assertEquals(1, currentBatchUpdateLevel)
    assertEquals(1, currentBatchFileUpdateLevel)

    val syncState = GradleSyncState.getInstance(project)
    syncState.syncTaskCreated(mock(GradleSyncInvoker.Request::class.java))
    MultiTemplateRenderer.multiRenderingFinished(project)
    // Yes, rendering finished but we were notified that sync is imminent, so suspension should continue.
    assertEquals(1, currentBatchUpdateLevel)
    assertEquals(1, currentBatchFileUpdateLevel)

    syncState.syncStarted(true, GradleSyncInvoker.Request(GradleSyncStats.Trigger.TRIGGER_USER_REQUEST))
    // No change
    assertEquals(1, currentBatchUpdateLevel)
    assertEquals(1, currentBatchFileUpdateLevel)

    syncState.setupStarted()
    // No change
    assertEquals(1, currentBatchUpdateLevel)
    assertEquals(1, currentBatchFileUpdateLevel)

    syncState.syncFailed("Test!")
    assertEquals(0, currentBatchUpdateLevel)
    assertEquals(0, currentBatchFileUpdateLevel)
  }

  private fun doTestGradleSyncWhen(failed: Boolean) {
    setUpIndexingSpecificExpectations(batchUpdateCount = 1, batchFileUpdateCount = 1)

    val syncState = GradleSyncState.getInstance(project)
    syncState.syncStarted(true, GradleSyncInvoker.Request(GradleSyncStats.Trigger.TRIGGER_USER_REQUEST))
    syncState.setupStarted()

    assertEquals(1, currentBatchUpdateLevel)
    assertEquals(1, actualBatchFileUpdateCount)

    if (failed) {
      syncState.syncFailed("Test")
    }
    else {
      syncState.syncEnded()
    }
    assertEquals(0, currentBatchUpdateLevel)
    assertEquals(0, currentBatchFileUpdateLevel)
  }

  private fun doTestGradleBuildWhen(buildStatus: BuildStatus) {
    setUpIndexingSpecificExpectations(batchUpdateCount = 1, batchFileUpdateCount = 1)

    val buildContext = mock(BuildContext::class.java)
    val buildState = GradleBuildState.getInstance(project)
    buildState.buildStarted(buildContext)

    assertEquals(1, currentBatchUpdateLevel)
    assertEquals(1, actualBatchFileUpdateCount)

    buildState.buildFinished(buildStatus)
    assertEquals(0, currentBatchUpdateLevel)
    assertEquals(0, currentBatchFileUpdateLevel)
  }
}
