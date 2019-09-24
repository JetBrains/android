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

import com.android.tools.idea.concurrent.executeOnPooledThread
import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_SYNC_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResultListener
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.invokeAndWaitIfNeed
import com.intellij.openapi.project.DumbServiceImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.ThrowableRunnable
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import kotlin.test.assertEquals

/**
 * Test [ProjectSystemSyncManager] that allows to manually change the [isSyncInProgress()] value
 */
private class TestSyncManager(val project: Project) : ProjectSystemSyncManager {
  var testIsSyncInProgress: Boolean = false
  var testLastSyncResult: SyncResult = SyncResult.UNKNOWN

  override fun syncProject(reason: ProjectSystemSyncManager.SyncReason): ListenableFuture<SyncResult> {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun isSyncInProgress(): Boolean = testIsSyncInProgress

  override fun isSyncNeeded(): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getLastSyncResult(): SyncResult = testLastSyncResult
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
    Mockito.verifyZeroInteractions(listener)

    emulateSync(SyncResult.CANCELLED)
    Mockito.verify(listener).syncEnded(SyncResult.CANCELLED)

    emulateSync(SyncResult.FAILURE)
    Mockito.verifyZeroInteractions(listener)
  }

  private fun startDumbMode() {
    WriteAction.runAndWait(ThrowableRunnable<Exception> {
      DumbServiceImpl.getInstance(project).isDumb = true
    })
  }

  private fun stopDumbMode() {
    WriteAction.runAndWait(ThrowableRunnable<Exception> {
      DumbServiceImpl.getInstance(project).isDumb = false
    })
  }

  @Test
  fun waitForSmartAndSyncedWhenSmartAndSynced() {
    val callCount = AtomicInteger(0)
    project.runWhenSmartAndSynced(callback = Consumer { callCount.incrementAndGet() })
    assertEquals(1, callCount.get())
  }

  @Test
  fun waitForSmartAndSyncedWhenDumbAndSynced() {
    val callCount = AtomicInteger(0)
    // Start dumb mode
    startDumbMode()

    project.runWhenSmartAndSynced(callback = Consumer { callCount.incrementAndGet() })
    assertEquals(0, callCount.get())
    emulateSync(SyncResult.SUCCESS)
    assertEquals(0, callCount.get())
    stopDumbMode()
    assertEquals(1, callCount.get())
  }

  @Test
  fun waitForSmartAndSyncedWhenSmartAndNotSynced() {
    val callCount = AtomicInteger(0)
    val syncManager = TestSyncManager(project)
    syncManager.testIsSyncInProgress = true
    project.runWhenSmartAndSynced(callback = Consumer { callCount.incrementAndGet() },
                                  syncManager = syncManager)
    assertEquals(0, callCount.get())
    syncManager.testIsSyncInProgress = false
    emulateSync(SyncResult.SUCCESS)
    assertEquals(1, callCount.get())
  }

  @Test
  fun waitForSmartAndSyncedWhenDumbAndNotSynced() {
    val callCount = AtomicInteger(0)
    val syncManager = TestSyncManager(project)
    syncManager.testIsSyncInProgress = true
    startDumbMode()
    project.runWhenSmartAndSynced(
      callback = Consumer { callCount.incrementAndGet() },
      syncManager = syncManager)
    assertEquals(0, callCount.get())
    syncManager.testIsSyncInProgress = false
    emulateSync(SyncResult.SUCCESS)
    // Now we are in dumb mode but synced
    assertEquals(0, callCount.get())

    stopDumbMode()
    assertEquals(1, callCount.get())

    // Once the callback has been called, new syncs or dumb mode changes won't call the method
    emulateSync(SyncResult.SUCCESS)
    assertEquals(1, callCount.get())
  }

  @Test
  fun waitForSmartAndSyncedAndCheckThread() {
    val callCount = AtomicInteger(0)
    val syncManager = TestSyncManager(project)

    // The next callback won't execute immediately but it will be scheduled to run on the EDT later
    val latch = CountDownLatch(1)
    val startThreadLatch = CountDownLatch(1)
    executeOnPooledThread {
      project.runWhenSmartAndSyncedOnEdt(
        callback = Consumer {
          assertTrue(ApplicationManager.getApplication().isDispatchThread)
          latch.await(1, TimeUnit.SECONDS)
          callCount.incrementAndGet()
        },
        syncManager = syncManager)
      startThreadLatch.countDown()
    }
    // Wait for the thread to start
    startThreadLatch.await(1, TimeUnit.SECONDS)
    assertEquals(0, callCount.get())
    latch.countDown()
    invokeAndWaitIfNeed { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
    assertEquals(1, callCount.get())

    val latch2 = CountDownLatch(1)
    executeOnPooledThread {
      project.runWhenSmartAndSynced(
        callback = Consumer {
          assertFalse(ApplicationManager.getApplication().isDispatchThread)
          callCount.incrementAndGet()
          latch2.countDown()
        },
        syncManager = syncManager)
    }
    latch2.await(1, TimeUnit.SECONDS)
    assertEquals(2, callCount.get())
  }

  @Test
  fun alreadyDisposed() {
    val callCount = AtomicInteger(0)
    val syncManager = TestSyncManager(project)

    stopDumbMode() // Not in dumb mode so callbacks should be immediately called
    project.runWhenSmartAndSynced(
      callback = Consumer {
        callCount.incrementAndGet()
      },
      syncManager = syncManager)
    assertEquals(callCount.get(), 1)

    val disposedDisposable = Disposer.newDisposable()
    Disposer.dispose(disposedDisposable)
    assertTrue(Disposer.isDisposed(disposedDisposable))
    project.runWhenSmartAndSynced(
      parentDisposable = disposedDisposable,
      callback = Consumer {
        callCount.incrementAndGet()
      },
      syncManager = syncManager)
    assertEquals(callCount.get(), 1)
  }
}
