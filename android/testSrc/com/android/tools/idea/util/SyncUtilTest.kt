/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.util

import com.android.tools.idea.concurrency.executeOnPooledThread
import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_SYNC_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResultListener
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.runInEdtAndWait
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Test [ProjectSystemSyncManager] that allows to manually change the [isSyncInProgress()] value
 */
private class TestSyncManager(project: Project): ProjectSystemSyncManager {
  var testIsSyncInProgress: Boolean = false

  // No sync has happened yet in the test.
  var testLastSyncResult: SyncResult? = null

  init {
    project.messageBus.connect(project).apply {
      subscribe(PROJECT_SYSTEM_SYNC_TOPIC, SyncResultListener { result ->
        disconnect()
        testLastSyncResult = result
      })
    }
  }

  override fun syncProject(reason: ProjectSystemSyncManager.SyncReason): ListenableFuture<SyncResult> {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun isSyncInProgress(): Boolean = testIsSyncInProgress

  override fun isSyncNeeded(): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getLastSyncResult(): SyncResult = testLastSyncResult ?: SyncResult.UNKNOWN
}

@RunWith(JUnit4::class)
class SyncUtilTest {
  @Rule
  @JvmField
  val projectRule = AndroidProjectRule.inMemory().initAndroid(false)
  private lateinit var project: Project
  private lateinit var listener: SyncResultListener

  private fun emulateSync(result: SyncResult) = project.messageBus.syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC).syncEnded(result)

  @Before
  fun setUp() {
    project = projectRule.project
    listener = Mockito.mock(SyncResultListener::class.java)
  }


  @Test
  fun listenOneSync() {
    project.listenUntilNextSync(listener = listener)
    Mockito.verifyNoMoreInteractions(listener)

    emulateSync(SyncResult.CANCELLED)
    Mockito.verify(listener).syncEnded(SyncResult.CANCELLED)

    emulateSync(SyncResult.FAILURE)
    Mockito.verifyNoMoreInteractions(listener)
  }

  @Test
  fun waitForSmartAndSyncedWhenSmartAndSynced() {
    val callCount = AtomicInteger(0)
    project.runWhenSmartAndSynced(callback = { callCount.incrementAndGet() })
    assertThat(callCount.get()).isEqualTo(1)
  }

  @Test
  fun waitForSmartAndSyncedWhenDumbAndSynced() {
    val latch = CountDownLatch(1)
    DumbModeTestUtils.runInDumbModeSynchronously(project) {
      project.runWhenSmartAndSynced(callback = { latch.countDown() })
      assertThat(latch.count).isEqualTo(1)
      emulateSync(SyncResult.SUCCESS)
      assertThat(latch.count).isEqualTo(1)
    }
    assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue()
  }

  @Test
  fun waitForSmartAndSyncedWhenSmartAndNotSynced() {
    val callCount = AtomicInteger(0)
    val syncManager = TestSyncManager(project)
    syncManager.testIsSyncInProgress = true

    project.runWhenSmartAndSynced(callback = { callCount.incrementAndGet() },
                                  syncManager = syncManager)
    assertThat(callCount.get()).isEqualTo(0)
    syncManager.testIsSyncInProgress = false
    emulateSync(SyncResult.SUCCESS)
    assertThat(callCount.get()).isEqualTo(1)
  }

  @Test
  fun waitForSmartAndSyncedWhenDumbAndNotSynced() {
    val callCount = AtomicInteger(0)
    val syncManager = TestSyncManager(project)
    syncManager.testIsSyncInProgress = true

    val semaphore = Semaphore(0)
    DumbModeTestUtils.runInDumbModeSynchronously(project) {
      project.runWhenSmartAndSynced(
        callback = {
          callCount.incrementAndGet()
          semaphore.release()
        },
        syncManager = syncManager)
      assertThat(callCount.get()).isEqualTo(0)
      syncManager.testIsSyncInProgress = false
      emulateSync(SyncResult.SUCCESS)
      // Now we are in dumb mode but synced
      assertThat(callCount.get()).isEqualTo(0)
    }

    semaphore.acquire()
    assertThat(callCount.get()).isEqualTo(1)

    // Once the callback has been called, new syncs or dumb mode changes won't call the method.
    emulateSync(SyncResult.SUCCESS)
    assertFalse("runWhenSmartAndSynced callback was called unexpectedly", semaphore.tryAcquire(5, TimeUnit.SECONDS))
    assertThat(callCount.get()).isEqualTo(1)
  }

  @Test
  fun waitForSmartAndSyncedAndCheckThread() {
    val callCount = AtomicInteger(0)
    val syncManager = TestSyncManager(project)

    // The next callback won't execute immediately, but it will be scheduled to run on the EDT later.
    val latch = CountDownLatch(1)
    val startThreadLatch = CountDownLatch(1)
    executeOnPooledThread {
      project.runWhenSmartAndSyncedOnEdt(
        callback = {
          assertThat(ApplicationManager.getApplication().isDispatchThread).isTrue()
          latch.await(1, TimeUnit.SECONDS)
          callCount.incrementAndGet()
        },
        syncManager = syncManager)
      startThreadLatch.countDown()
    }
    // Wait for the thread to start, no calls are yet called as the sync state is UNKNOWN.
    startThreadLatch.await(1, TimeUnit.SECONDS)
    assertThat(callCount.get()).isEqualTo(0)
    latch.countDown()

    // We need to emulate sync because in production all the listeners (for example [ProjectLightResourceClassService])
    // will trigger sync indirectly through methods like dropPsiCaches().
    emulateSync(SyncResult.SUCCESS)
    runInEdtAndWait { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
    assertThat(callCount.get()).isEqualTo(1)

    val latch2 = CountDownLatch(1)
    executeOnPooledThread {
      project.runWhenSmartAndSynced(
        callback = {
          assertThat(ApplicationManager.getApplication().isDispatchThread).isFalse()
          callCount.incrementAndGet()
          latch2.countDown()
        },
        syncManager = syncManager)
    }
    latch2.await(1, TimeUnit.SECONDS)
    assertThat(callCount.get()).isEqualTo(2)
  }

  @Test
  fun alreadyDisposed() {
    val callCount = AtomicInteger(0)
    val syncManager = TestSyncManager(project)


    assertThat(DumbService.isDumb(project)).isFalse() // In Smart mode so callbacks should be immediately called.
    project.runWhenSmartAndSynced(
      callback = {
        callCount.incrementAndGet()
      },
      syncManager = syncManager)

    // We need to emulate sync because in production all the listeners (for example [ProjectLightResourceClassService])
    // will trigger sync indirectly through methods like dropPsiCaches().
    emulateSync(SyncResult.SKIPPED)
    assertThat(callCount.get()).isEqualTo(1)

    val disposedDisposable = Disposer.newDisposable()
    Disposer.dispose(disposedDisposable)
    assertThat(Disposer.isDisposed(disposedDisposable)).isTrue()
    project.runWhenSmartAndSynced(
      parentDisposable = disposedDisposable,
      callback = {
        callCount.incrementAndGet()
      },
      syncManager = syncManager)
    assertThat(callCount.get()).isEqualTo(1)
  }
}
