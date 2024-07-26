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
package com.android.tools.idea.res

import com.android.testutils.waitForCondition
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Disposer
import com.intellij.util.application
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ResourceFolderRepositoryBackgroundActionsTest {
  @get:Rule val fixture = AndroidProjectRule.inMemory()

  @Before
  fun setUp() {
    // Enabling tracing ensures all the log lines show up in the test log, and that there aren't any
    // exceptions there.
    ResourceUpdateTracer.getInstance().startTracing()
  }

  @After
  fun tearDown() {
    val resourceUpdateTracer = ResourceUpdateTracer.getInstance()
    resourceUpdateTracer.dumpTrace("ResourceFolderRepositoryBackgroundActionsTest")
    resourceUpdateTracer.stopTracing()
  }

  @Test
  fun updateQueue_actionRunWithinReadLock() {
    val backgroundActions = ResourceFolderRepositoryBackgroundActions.getInstance(fixture.module)

    val hasReadLock = AtomicBoolean(false)
    backgroundActions.scheduleUpdate(Any()) { hasReadLock.set(application.isReadAccessAllowed) }

    backgroundActions.waitForUpdateQueue()
    assertThat(hasReadLock.get()).isTrue()
  }

  @Test
  fun updateQueue_actionsRunInOrder() {
    val backgroundActions = ResourceFolderRepositoryBackgroundActions.getInstance(fixture.module)

    val semaphore1 = Semaphore(0)
    val semaphore2 = Semaphore(0)
    val semaphore3 = Semaphore(0)

    val lock = Any()
    val actionsCompleted: MutableList<Int> = mutableListOf()
    backgroundActions.scheduleUpdate(Any()) {
      semaphore1.acquire()
      synchronized(lock) { actionsCompleted.add(1) }
    }

    backgroundActions.scheduleUpdate(Any()) {
      semaphore2.acquire()
      synchronized(lock) { actionsCompleted.add(2) }
    }

    backgroundActions.scheduleUpdate(Any()) {
      semaphore3.acquire()
      synchronized(lock) { actionsCompleted.add(3) }
    }

    synchronized(lock) { assertThat(actionsCompleted).isEmpty() }

    semaphore3.release()
    semaphore2.release()
    semaphore1.release()
    backgroundActions.waitForUpdateQueue()

    synchronized(lock) { assertThat(actionsCompleted).containsExactly(1, 2, 3).inOrder() }
  }

  @Test
  fun updateQueue_throwingActionDoesNotPreventFutureActions() {
    val backgroundActions = ResourceFolderRepositoryBackgroundActions.getInstance(fixture.module)

    val lock = Any()
    val actionsCompleted: MutableList<Int> = mutableListOf()
    backgroundActions.scheduleUpdate(Any()) { synchronized(lock) { actionsCompleted.add(1) } }
    backgroundActions.scheduleUpdate(Any()) { throw IllegalStateException() }
    backgroundActions.scheduleUpdate(Any()) { synchronized(lock) { actionsCompleted.add(3) } }

    backgroundActions.waitForUpdateQueue()

    synchronized(lock) { assertThat(actionsCompleted).containsExactly(1, 3).inOrder() }
  }

  @Test
  fun updateQueue_writeActionCanPreempt() {
    val backgroundActions = ResourceFolderRepositoryBackgroundActions.getInstance(fixture.module)

    val actionStarted = AtomicInteger()
    val actionFinished = AtomicInteger()

    val continueBackgroundAction = AtomicBoolean()

    backgroundActions.scheduleUpdate(Any()) {
      actionStarted.incrementAndGet()

      waitForCondition(10.seconds) {
        ProgressManager.checkCanceled()
        continueBackgroundAction.get()
      }

      actionFinished.incrementAndGet()
    }

    // Wait until the background action has started.
    waitForCondition(10.seconds) { actionStarted.get() == 1 }

    // Now that we know the read action has begun, try to get the write lock.
    val writeActionStarted = Semaphore(0)
    application.invokeLater { runWriteAction { writeActionStarted.release() } }

    // Once the write lock has been acquired, allow the background action to continue.
    writeActionStarted.acquire()
    continueBackgroundAction.set(true)

    // The background action, after all is said and done, should have been started twice and
    // finished once.
    backgroundActions.waitForUpdateQueue()
    assertThat(actionStarted.get()).isEqualTo(2)
    assertThat(actionFinished.get()).isEqualTo(1)
  }

  @Test
  fun updateQueue_disposedDuringExecution() {
    val backgroundActions = ResourceFolderRepositoryBackgroundActions.getInstance(fixture.module)

    val actionStarted = Semaphore(0)
    val actionFinished = AtomicBoolean()

    backgroundActions.scheduleUpdate(Any()) {
      actionStarted.release()

      waitForCondition(10.seconds) {
        ProgressManager.checkCanceled()
        false // Never continue past here
      }

      actionFinished.set(true)
    }

    // Wait until the background action has started.
    actionStarted.acquire()

    // Now that we know the read action has begun, dispose the containing object.
    Disposer.dispose(backgroundActions)

    // Run a write action, to ensure that the read action from the update queue has been released.
    val writeActionStarted = Semaphore(0)
    application.invokeLater { runWriteAction { writeActionStarted.release() } }
    writeActionStarted.acquire()

    // The action should never finish.
    Thread.sleep(1000)
    assertThat(actionFinished.get()).isFalse()
  }

  @Test
  fun updateQueue_queuingAfterDisposal() {
    val backgroundActions = ResourceFolderRepositoryBackgroundActions.getInstance(fixture.module)
    Disposer.dispose(backgroundActions)

    // Should be able to call [scheduleUpdate] without an exception.
    val updateHasRun = AtomicBoolean()
    backgroundActions.scheduleUpdate(Any()) { updateHasRun.set(true) }

    Thread.sleep(1000)
    assertThat(updateHasRun.get()).isFalse()
  }

  @Test
  fun wolfQueue_actionNotRunWithinReadLock() {
    val backgroundActions = ResourceFolderRepositoryBackgroundActions.getInstance(fixture.module)

    val hasReadLock = AtomicBoolean(true)
    backgroundActions.submitToWolfQueue { hasReadLock.set(application.isReadAccessAllowed) }

    backgroundActions.waitForWolfQueue()
    assertThat(hasReadLock.get()).isFalse()
  }

  @Test
  fun wolfQueue_actionsRunInOrder() {
    val backgroundActions = ResourceFolderRepositoryBackgroundActions.getInstance(fixture.module)

    val semaphore1 = Semaphore(0)
    val semaphore2 = Semaphore(0)
    val semaphore3 = Semaphore(0)

    val lock = Any()
    val actionsCompleted: MutableList<Int> = mutableListOf()

    backgroundActions.submitToWolfQueue {
      semaphore1.acquire()
      synchronized(lock) { actionsCompleted.add(1) }
    }

    backgroundActions.submitToWolfQueue {
      semaphore2.acquire()
      synchronized(lock) { actionsCompleted.add(2) }
    }

    backgroundActions.submitToWolfQueue {
      semaphore3.acquire()
      synchronized(lock) { actionsCompleted.add(3) }
    }

    synchronized(lock) { assertThat(actionsCompleted).isEmpty() }

    semaphore3.release()
    semaphore2.release()
    semaphore1.release()
    backgroundActions.waitForWolfQueue()

    synchronized(lock) { assertThat(actionsCompleted).containsExactly(1, 2, 3).inOrder() }
  }

  @Test
  fun wolfQueue_throwingActionDoesNotPreventFutureActions() {
    val backgroundActions = ResourceFolderRepositoryBackgroundActions.getInstance(fixture.module)

    val lock = Any()
    val actionsCompleted: MutableList<Int> = mutableListOf()
    backgroundActions.submitToWolfQueue { synchronized(lock) { actionsCompleted.add(1) } }
    backgroundActions.submitToWolfQueue { throw IllegalStateException() }
    backgroundActions.submitToWolfQueue { synchronized(lock) { actionsCompleted.add(3) } }

    backgroundActions.waitForWolfQueue()

    synchronized(lock) { assertThat(actionsCompleted).containsExactly(1, 3).inOrder() }
  }

  @Test
  fun wolfQueue_queuingAfterDisposal() {
    val backgroundActions = ResourceFolderRepositoryBackgroundActions.getInstance(fixture.module)
    Disposer.dispose(backgroundActions)

    // It's unclear if this is desired behavior or not, but the test is documenting what the current
    // behavior actually is.
    assertFailsWith<RejectedExecutionException> { backgroundActions.submitToWolfQueue {} }
  }

  @Test
  fun generalBackgroundThread() {
    application.invokeAndWait {
      val semaphore1 = Semaphore(0)
      val semaphore2 = Semaphore(0)
      val semaphore3 = Semaphore(0)

      val lock = Any()
      val actionsCompleted: MutableList<Int> = mutableListOf()
      ResourceFolderRepositoryBackgroundActions.executeOnPooledThread {
        semaphore1.acquire()
        synchronized(lock) { actionsCompleted.add(1) }
      }

      ResourceFolderRepositoryBackgroundActions.executeOnPooledThread {
        semaphore2.acquire()
        synchronized(lock) { actionsCompleted.add(2) }
      }

      ResourceFolderRepositoryBackgroundActions.executeOnPooledThread {
        semaphore3.acquire()
        synchronized(lock) { actionsCompleted.add(3) }
      }

      assertThat(actionsCompleted).isEmpty()

      // The actions can be run in any order and in parallel, so force a specific ordering.
      semaphore3.release()
      waitForCondition(10.seconds) { synchronized(lock) { actionsCompleted.size == 1 } }
      semaphore2.release()
      waitForCondition(10.seconds) { synchronized(lock) { actionsCompleted.size == 2 } }
      semaphore1.release()
      waitForCondition(10.seconds) { synchronized(lock) { actionsCompleted.size == 3 } }

      synchronized(lock) { assertThat(actionsCompleted).containsExactly(3, 2, 1).inOrder() }
    }
  }

  @Test
  fun generalBackgroundThread_throwingActionDoesNotPreventOtherActions() {
    application.invokeAndWait {
      val exceptionThrown = Semaphore(0)

      val actionCompletedCount = AtomicInteger()
      ResourceFolderRepositoryBackgroundActions.executeOnPooledThread {
        try {
          throw IllegalStateException()
        } finally {
          exceptionThrown.release()
        }
      }

      exceptionThrown.acquire()
      Thread.sleep(1000)
      ResourceFolderRepositoryBackgroundActions.executeOnPooledThread {
        actionCompletedCount.incrementAndGet()
      }
      ResourceFolderRepositoryBackgroundActions.executeOnPooledThread {
        actionCompletedCount.incrementAndGet()
      }
      ResourceFolderRepositoryBackgroundActions.executeOnPooledThread {
        actionCompletedCount.incrementAndGet()
      }

      waitForCondition(10.seconds) { actionCompletedCount.get() == 3 }
    }
  }

  private fun ResourceFolderRepositoryBackgroundActions.waitForUpdateQueue() {
    val actionsCompleteSemaphore = Semaphore(0)
    scheduleUpdate(Any()) { actionsCompleteSemaphore.release() }
    assertThat(actionsCompleteSemaphore.tryAcquire(10L, TimeUnit.SECONDS)).isTrue()
  }

  private fun ResourceFolderRepositoryBackgroundActions.waitForWolfQueue() {
    val actionsCompleteSemaphore = Semaphore(0)
    submitToWolfQueue { actionsCompleteSemaphore.release() }
    assertThat(actionsCompleteSemaphore.tryAcquire(10L, TimeUnit.SECONDS)).isTrue()
  }
}
