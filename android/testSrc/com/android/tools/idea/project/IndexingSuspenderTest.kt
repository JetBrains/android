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
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.gradle.util.BuildMode
import com.android.tools.idea.testing.IdeComponents
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.mock.MockDumbService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.impl.stores.BatchUpdateListener
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.DumbModeTask
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.testFramework.IdeaTestCase
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.messages.MessageBusConnection

import org.mockito.Mockito.mock
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class IndexingSuspenderTest : IdeaTestCase() {
  private lateinit var ideComponents: IdeComponents

  private lateinit var batchUpdateConnection: MessageBusConnection
  private var expectedBatchUpdateCount = 0
  private var actualBatchUpdateCount = 0
  private var currentBatchUpdateLevel = 0

  private var expectedDumbModeCount = 0

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

    val dumbService = ThreadingAwareDumbService(project)
    val indexingSuspenderService = IndexingSuspender(project, true)
    ideComponents = IdeComponents(project)
    ideComponents.replaceProjectDumbService(dumbService)
    ideComponents.replaceProjectService(IndexingSuspender::class.java, indexingSuspenderService)

    // Ensure the services are replaced globally as expected. If not, there is a bug in IdeComponents implementation,
    // and it doesn't make sense to execute this test further.
    assertSame(DumbService.getInstance(project), dumbService)
    assertSame(ServiceManager.getService(project, IndexingSuspender::class.java), indexingSuspenderService)

    expectedDumbModeCount = 0
    currentBatchUpdateLevel = 0
    actualBatchUpdateCount = 0
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
      try {
        ideComponents.restore()
      }
      finally {
        super.tearDown()
      }
    }
  }

  private fun verifyIndexingSpecificExpectations() {
    // verify dumb mode counters
    val dumbService = DumbService.getInstance(project) as ThreadingAwareDumbService

    // we allow some time for the "dumb mode" thread to join, but if it does not happen, then it's a risk of a deadlock
    // and there is probably a bug either in the event system or in the suspender implementation
    dumbService.waitForSmartMode()
    assertFalse("Dumb mode must have ended by this point.", dumbService.isDumb)
    assertEquals("Dumb mode was not entered as many times as expected",
        expectedDumbModeCount, dumbService.actualDumbModeCount)

    // verify batch update
    assertEquals("Did not unwind batch updates. Each batch update start must be paired with a corresponding finish call.",
        0, currentBatchUpdateLevel)
    assertEquals("Batch update sessions were not started as many times as expected.",
        expectedBatchUpdateCount, actualBatchUpdateCount)
  }

  private fun setUpIndexingSpecificExpectations(dumbModeCount: Int, batchUpdateCount: Int) {
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

    expectedDumbModeCount = dumbModeCount
    expectedBatchUpdateCount = batchUpdateCount
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

    setUpIndexingSpecificExpectations(dumbModeCount = 0, batchUpdateCount = 0)

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

    setUpIndexingSpecificExpectations(dumbModeCount = 0, batchUpdateCount = 0)

    val syncState = GradleSyncState.getInstance(project)
    syncState.syncSkipped(42)
    assertFalse(syncState.isSyncInProgress)
  }

  fun testGradleSyncAndBuild() {
    if (!canRun()) {
      return
    }

    // this tests the correct event handling when build is triggered during sync (e.g., source generation)
    setUpIndexingSpecificExpectations(dumbModeCount = 1, batchUpdateCount = 1)
    val syncState = GradleSyncState.getInstance(project)
    syncState.syncStarted(true, GradleSyncStats.Trigger.TRIGGER_USER_REQUEST)
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

    buildState.buildStarted(buildContext)

    // Verify that dumb mode ends once build starts. Builds are expected to be still under a batch update, but outside of
    // the sentinel dumb mode (see b/69455108).
    val dumbService = DumbService.getInstance(project) as ThreadingAwareDumbService
    dumbService.waitForSmartMode()
    assertFalse("Build is expected to be executed outside of dumb mode", dumbService.isDumb)
    // must be still within a batch update session - suspender is still active, just the means of indexing suspension changed
    // from dumbmode + batchupdate to just batchupdate
    assertEquals(1, currentBatchUpdateLevel)
    buildState.buildFinished(BuildStatus.SUCCESS)
    assertFalse(buildState.isBuildInProgress)
  }

  private fun doTestGradleSyncWhen(failed: Boolean) {
    setUpIndexingSpecificExpectations(dumbModeCount = 1, batchUpdateCount = 1)

    val syncState = GradleSyncState.getInstance(project)
    syncState.syncStarted(true, GradleSyncStats.Trigger.TRIGGER_USER_REQUEST)
    syncState.setupStarted()

    assertTrue(DumbService.getInstance(project).isDumb)
    assertEquals(1, actualBatchUpdateCount)

    if (failed) {
      syncState.syncFailed("Test")
    }
    else {
      syncState.syncEnded()
    }
  }

  private fun doTestGradleBuildWhen(buildStatus: BuildStatus) {
    setUpIndexingSpecificExpectations(dumbModeCount = 0, batchUpdateCount = 1)

    val buildContext = mock(BuildContext::class.java)
    val buildState = GradleBuildState.getInstance(project)
    buildState.buildStarted(buildContext)

    assertFalse(DumbService.getInstance(project).isDumb)
    assertEquals(1, actualBatchUpdateCount)

    buildState.buildFinished(buildStatus)
  }

  /**
   * This class serves as a mock for project's DumbService in the context of IndexingSuspender tests. We need a mock because
   * the default DumbServiceImpl executes the task being queued right away, directly on the event dispatch thread. This would
   * not work for IndexingSuspenderTask because it invokes wait() in an expectation to be notified later when the sentinel
   * dumb mode is supposed to finish (e.g., on gradle sync completion). Therefore, running these tests without a specially
   * adjusted DumbService mock would lead to a dead-lock, and this class solves this problem by asserting the type of task being
   * queued and executing it on a separate thread.
   *
   * @see IndexingSuspender.startSentinelDumbMode
   */
  private class ThreadingAwareDumbService(project: Project) : MockDumbService(project) {
    var actualDumbModeCount = 0
      private set

    private var dumbModeFuture: Future<*>? = null

    override fun isDumb() = dumbModeFuture != null && dumbModeFuture?.isDone != true && dumbModeFuture?.isCancelled != true

    override fun queueTask(dumbModeTask: DumbModeTask) {
      assertFalse("IndexingSuspender must not attempt to queue its dumb mode task while one is already running", isDumb)
      UsefulTestCase.assertInstanceOf(dumbModeTask, IndexingSuspender.IndexingSuspenderTask::class.java)

      dumbModeFuture = ApplicationManager.getApplication().executeOnPooledThread{
        actualDumbModeCount += 1
        dumbModeTask.performInDumbMode(EmptyProgressIndicator())
      }
    }

    override fun waitForSmartMode() {
      // The future is expected to return sooner than the entire timeout, since the IndexingSuspenderTask's wait condition must be updated
      // before calling this method.
      // Division by 2 ensures that the join timeout is significantly less than the wait timeout, and this, in turn,
      // helps to make sure that we actually quit the wait loop on notify() rather than by timeout
      dumbModeFuture?.get((IndexingSuspender.INDEXING_WAIT_TIMEOUT_MILLIS/2).toLong(), TimeUnit.MILLISECONDS)
    }
  }
}
