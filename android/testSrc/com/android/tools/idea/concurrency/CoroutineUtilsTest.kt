/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.concurrency

import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.UiThread
import com.android.annotations.concurrency.WorkerThread
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.DumbServiceImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.psi.PsiManager
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.EnumSet
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

const val UI_THREAD = "UI thread"
const val WORKER_THREAD = "Worker thread"
const val IO_THREAD = "IO thread"

class CoroutineUtilsTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  private lateinit var uiExecutor: ExecutorService
  private lateinit var workerExecutor: ExecutorService
  private lateinit var ioExecutor: ExecutorService
  private lateinit var testExecutors: AndroidExecutors

  private fun createExecutor(threadName: String): ExecutorService {
    val threadFactory = ThreadFactoryBuilder().setNameFormat(threadName).build()
    return Executors.newSingleThreadExecutor(threadFactory)
  }

  private fun checkThread(threadName: String) {
    // Coroutines machinery appends the coroutine name to the thread name, so we use `startsWith` instead of `equals`.
    assertThat(Thread.currentThread().name).startsWith(threadName)
  }

  @Before
  fun setUp() {
    uiExecutor = createExecutor(UI_THREAD)
    workerExecutor = createExecutor(WORKER_THREAD)
    ioExecutor = createExecutor(IO_THREAD)
    testExecutors = AndroidExecutors({ _, code -> uiExecutor.execute(code) }, workerExecutor, ioExecutor)
    ApplicationManager.getApplication().replaceService(AndroidExecutors::class.java, testExecutors, projectRule.testRootDisposable)
  }

  @After
  fun tearDown() {
    listOf(uiExecutor, workerExecutor, ioExecutor).forEach {
      it.shutdown()
      it.awaitTermination(5, TimeUnit.SECONDS)
    }
  }

  @Test
  fun apiExample() {
    val textUpdated = CountDownLatch(1)

    /**
     * Simulated module service.
     */
    class FooManager : UserDataHolderEx by UserDataHolderBase(), AndroidCoroutinesAware {

      override fun dispose() {}

      /** Fake method that has to run on the UI thread. */
      @UiThread
      fun updateLabelText(@Suppress("UNUSED_PARAMETER") text: String) {
        checkThread(UI_THREAD)
        textUpdated.countDown()
      }

      /** Fake method that has to run on the worker thread. */
      @WorkerThread
      fun computeData(): String {
        checkThread(WORKER_THREAD)
        return "computed"
      }

      /** Fake method that is aware of coroutines and enforces its own threading rules. */
      @AnyThread
      private suspend fun suspendComputeData(): String = withContext(workerThread) {
        checkThread(WORKER_THREAD)
        "computed"
      }

      @UiThread
      fun buttonClicked() {
        checkThread(UI_THREAD)

        launch(uiThread) {
          checkThread(UI_THREAD)
          // This suspends the coroutine, releasing the IO thread until computation is done on the worker thread.
          val computedData: String = withContext(workerThread) { computeData() }
          val anotherData = suspendComputeData()

          checkThread(UI_THREAD)
          updateLabelText(computedData + anotherData)
        }
      }
    }

    val fooManager = FooManager()
    Disposer.register(projectRule.project, fooManager)
    uiExecutor.submit { fooManager.buttonClicked() }
    textUpdated.await(2, TimeUnit.SECONDS)
  }

  @Test
  fun exceptionHandler() {
    class FooManager : UserDataHolderEx by UserDataHolderBase(), AndroidCoroutinesAware {
      override fun dispose() {}

      fun compute1() = launch {
        error("expected failure")
      }

      fun compute2() = launch(CoroutineName("computing")) {
        error("expected failure")
      }
    }

    val messages = mutableListOf<String>()

    LoggedErrorProcessor.executeWith<RuntimeException>(object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String, details: Array<out String>, t: Throwable?): Set<Action> {
        messages.add(message)
        return EnumSet.noneOf(Action::class.java)
      }
    }) {
      val fooManager = FooManager()
      Disposer.register(projectRule.project, fooManager)

      fooManager.compute1()
      fooManager.compute2()

      workerExecutor.shutdown()
      workerExecutor.awaitTermination(2, TimeUnit.SECONDS)
      assertThat(messages).containsExactly("expected failure", "computing")
    }
  }

  @Test
  fun disposing() {

    val computationStarted = CountDownLatch(1)
    val computationFinished = CountDownLatch(1)
    val done = CountDownLatch(1)
    val exception = AtomicReference<Exception>(null)
    val uiUpdated = AtomicReference<Boolean>(false)

    class FooManager : UserDataHolderEx by UserDataHolderBase(), AndroidCoroutinesAware {
      override fun dispose() {}

      suspend fun updateUi() = withContext(uiThread) {
        uiUpdated.set(true)
      }

      fun computeAndUpdateUi() = launch {
        checkThread(WORKER_THREAD)
        try {
          computationStarted.countDown()
          computationFinished.await()
          updateUi()
        }
        catch (e: Exception) {
          exception.set(e)
          throw e
        }
        finally {
          done.countDown()
        }
      }
    }

    val fooManager = FooManager()
    Disposer.register(projectRule.project, fooManager)

    fooManager.computeAndUpdateUi()

    computationStarted.await()
    // Simulate the service/project/UI being disposed while computation is running.
    Disposer.dispose(fooManager)
    computationFinished.countDown()
    done.await()

    uiExecutor.shutdown()
    uiExecutor.awaitTermination(2, TimeUnit.SECONDS)
    assertThat(uiUpdated.get()).isFalse()
    assertThat(exception.get()).isInstanceOf(CancellationException::class.java)
    assertThat(exception.get()).hasMessageThat().contains("FooManager")
    assertThat(exception.get()).hasMessageThat().contains("disposed")
  }

  @Test
  fun explicitScope() {
    class FooManager : Disposable {

      private val scope = AndroidCoroutineScope(this)

      fun run() = scope.launch {
        // work
      }

      override fun dispose() {}
    }
  }

  @Test
  fun `read action with coroutines runs sucessfully`() {
    val writeActionIsReady = CountDownLatch(1)
    val writeLatch = CountDownLatch(1)
    UIUtil.invokeLaterIfNeeded {
      WriteAction.run<Throwable> {
        writeActionIsReady.countDown()
        // Hold the read lock
        writeLatch.await()
      }
    }

    // Wait until we know the write action has the lock
    writeActionIsReady.await()
    val readActionExecuted = CompletableDeferred<Boolean>()
    try {
      runBlocking(workerThread) {
        launch(workerThread) {
          assertThat(readActionExecuted.isCompleted).isFalse()
          runReadAction {
            readActionExecuted.complete(true)
          }
          assertThat(readActionExecuted.isCompleted).isTrue()
        }
        withTimeout(500) {
          assertThat(readActionExecuted.getCompletedOrNull()).isNull()
          delay(100)
          // The read action should not have executed yet
          assertThat(readActionExecuted.getCompletedOrNull()).isNull()
          // Release write lock so the read action can execute
          writeLatch.countDown()
          // Check the write action executes
          assertThat(readActionExecuted.await()).isTrue()
        }
      }
    }
    finally {
      writeLatch.countDown()
    }
  }

  @Test
  fun `read action with coroutines times out`() {
    val writeActionIsReady = CountDownLatch(1)
    val writeLatch = CountDownLatch(1)
    UIUtil.invokeLaterIfNeeded {
      WriteAction.run<Throwable> {
        writeActionIsReady.countDown()
        // Hold the read lock
        writeLatch.await()
      }
    }

    // Wait until we know the write action has the lock
    writeActionIsReady.await()
    val readActionExecuted = CompletableDeferred<Boolean>()
    try {
      runBlocking(workerThread) {
        val smartReadJob = launch(workerThread) {
          assertThat(readActionExecuted.isCompleted).isFalse()
          runReadAction {
            readActionExecuted.complete(true)
          }
          assertThat(readActionExecuted.isCompleted).isTrue()
        }
        try {
          withTimeout(2000) {
            // This be block and the timeout will expire
            readActionExecuted.await()
          }
          fail("The write lock is still being held, timeout was expected")
        }
        catch (_: TimeoutCancellationException) {
        }
        finally {
          smartReadJob.cancel()
        }
      }
    }
    finally {
      // Release the lock
      writeLatch.countDown()
    }
  }

  @Test
  fun `write action with coroutines times out`() {
    val readActionIsReady = CountDownLatch(1)
    val readLatch = CountDownLatch(1)

    thread {
      com.intellij.openapi.application.runReadAction {
        readActionIsReady.countDown()
        // Hold the read lock
        readLatch.await()
      }
    }

    // Wait until we know the read action has the lock
    readActionIsReady.await()
    runBlocking(workerThread) {
      val writeActionExecuted = CompletableDeferred<Boolean>()
      try {
        val smartReadJob = launch(workerThread) {
          assertThat(writeActionExecuted.isCompleted).isFalse()
          runWriteActionAndWait {
            writeActionExecuted.complete(true)
          }
          assertThat(writeActionExecuted.isCompleted).isTrue()
        }
        try {
          withTimeout(2000) {
            // This be block and the timeout will expire
            writeActionExecuted.await()
          }
          fail("The read lock is still being held, timeout was expected")
        }
        catch (_: TimeoutCancellationException) {
        }
        finally {
          smartReadJob.cancel()
        }
      }
      finally {
        // Release the lock
        readLatch.countDown()
      }
    }
  }

  @Test
  fun `smart read action with coroutines runs successfully`() {
    val writeActionIsReady = CountDownLatch(1)
    val writeActionCompleted = CompletableDeferred<Boolean>()
    val writeLatch = CountDownLatch(1)
    val project = projectRule.project
    runInEdtAndWait {
      DumbServiceImpl.getInstance(project).isDumb = true
    }
    UIUtil.invokeLaterIfNeeded {
      WriteAction.run<Throwable> {
        writeActionIsReady.countDown()
        // Hold the read lock
        writeLatch.await()
        writeActionCompleted.complete(true)
      }
    }

    // Wait until we know the write action has the lock
    writeActionIsReady.await()
    val smartReadActionExecuted = CompletableDeferred<Boolean>()
    runBlocking(workerThread) {
      launch(workerThread) {
        assertThat(smartReadActionExecuted.isCompleted).isFalse()
        runInSmartReadAction(project) {
          smartReadActionExecuted.complete(true)
        }
        assertThat(smartReadActionExecuted.isCompleted).isTrue()
      }
      try {
        withTimeout(500) {
          assertThat(smartReadActionExecuted.getCompletedOrNull()).isNull()
          delay(100)
          // The read action should not have executed yet
          assertThat(smartReadActionExecuted.getCompletedOrNull()).isNull()
          // Release write lock so the read action can execute
          writeLatch.countDown()
          // Check the write action completed
          assertThat(writeActionCompleted.await()).isTrue()
          // The read action should not have executed yet because we are still in non-smart mode
          assertThat(smartReadActionExecuted.getCompletedOrNull()).isNull()
          runInEdtAndWait {
            DumbServiceImpl.getInstance(project).isDumb = false
          }
          assertThat(smartReadActionExecuted.await()).isTrue()
        }
      }
      finally {
        writeLatch.countDown()
      }
    }
  }

  @Test
  fun `smart read action with coroutines runs times out`() {
    val writeActionIsReady = CountDownLatch(1)
    val writeActionCompleted = CompletableDeferred<Boolean>()
    val writeLatch = CountDownLatch(1)
    val project = projectRule.project
    runInEdtAndWait {
      DumbServiceImpl.getInstance(project).isDumb = true
    }
    UIUtil.invokeLaterIfNeeded {
      WriteAction.run<Throwable> {
        writeActionIsReady.countDown()
        // Hold the read lock
        writeLatch.await()
        writeActionCompleted.complete(true)
      }
    }

    // Wait until we know the write action has the lock
    writeActionIsReady.await()
    val smartReadActionExecuted = CompletableDeferred<Boolean>()
    runBlocking(workerThread) {
      val smartActionJob = launch(workerThread) {
        runInSmartReadAction(project) {
          smartReadActionExecuted.complete(true)
        }
      }
      try {
        withTimeout(500) {
          smartReadActionExecuted.await()
          fail("Not on smart mode and read lock should not be available")
        }
      }
      catch (_: TimeoutCancellationException) {
      }
      finally {
        writeLatch.countDown()
      }
      writeActionCompleted.await()

      // Now we have released the read lock, check we still timeout
      try {
        withTimeout(500) {
          smartReadActionExecuted.await()
          fail("Not on smart mode")
        }
      }
      catch (_: TimeoutCancellationException) {
      }
      smartActionJob.cancel()
    }
  }

  @Test
  fun `run psi file safely`() = runBlocking {
    val project = projectRule.project
    val virtualFile = projectRule.fixture.addFileToProject("src/Test.kt", """
      fun Test() {
      }
    """.trimIndent()).virtualFile
    runWriteActionAndWait { PsiManager.getInstance(project).dropPsiCaches() }
    assertTrue(getPsiFileSafely(project, virtualFile)!!.isValid)
  }

  @Test
  fun `launch with progress cancels if the indicator is stopped`() = runBlocking {
    val progressIndicator = ProgressIndicatorBase()
    progressIndicator.start()
    val coroutineCompleted = CompletableDeferred<Unit>()
    launchWithProgress(progressIndicator) {
      try {
        while (true) {
          delay(1000)
        }
      }
      catch (_: CancellationException) {
      }
      catch (t: Throwable) {
        fail("Unexpected exception $t")
      }
    }.invokeOnCompletion {
      coroutineCompleted.complete(Unit)
    }

    try {
      withTimeout(500) {
        coroutineCompleted.await()
      }
      fail("Expected timeout")
    }
    catch (_: TimeoutCancellationException) {
    }
    // This will cancel the indicator which should stop the launched coroutine
    progressIndicator.cancel()
    coroutineCompleted.await()
  }

  @Test
  fun `launch with progress stops the indicator if the coroutine ends`() = runBlocking {
    val progressIndicator = ProgressIndicatorBase()
    progressIndicator.start()

    // The coroutine will start and wait for the CompletableDeferred to be completed.
    val waitPoint = CompletableDeferred<Unit>()
    val coroutineCompleted = CompletableDeferred<Unit>()

    launchWithProgress(progressIndicator) {
      waitPoint.await()
    }.invokeOnCompletion {
      coroutineCompleted.complete(Unit)
    }

    assertTrue(progressIndicator.isRunning)
    waitPoint.complete(Unit)

    coroutineCompleted.await()
    assertFalse(progressIndicator.isRunning)
  }

  private interface TestCallback {
    fun send()
  }

  @Test
  fun `disposable callback flow`() {
    val parentDisposable = Disposer.newDisposable(projectRule.testRootDisposable, "parent")
    val callbackDeferred = CompletableDeferred<TestCallback>()
    val disposableFlow = disposableCallbackFlow<Unit>("Test", null, parentDisposable) {
      callbackDeferred.complete(object : TestCallback {
        override fun send() {
          this@disposableCallbackFlow.trySend(Unit)
        }
      })
    }

    val flowReceiverCount = AtomicInteger(0)
    val countDownLatch = CountDownLatch(10)
    runBlocking {
      val collectJob = launch(workerThread) {
        disposableFlow.collect {
          flowReceiverCount.incrementAndGet()
          countDownLatch.countDown()
        }
      }

      val callback = callbackDeferred.await()
      repeat(10) { callback.send() }
      countDownLatch.await()
      Disposer.dispose(parentDisposable) // This will stop the flow collect call
      collectJob.join()
      // These callbacks will be ignored since they happened after the collect cancellation.
      repeat(10) { callback.send() }

      assertEquals(10, flowReceiverCount.get())
    }
  }

  @Suppress("UnstableApiUsage")
  @DelicateCoroutinesApi
  @Test
  fun `smart mode flow reports changes in status`() {
    val project = projectRule.project
    val disposable = Disposer.newDisposable(projectRule.testRootDisposable, "TestDisposable")
    val connected = CompletableDeferred<Unit>()
    val smartModeFlow = smartModeFlow(project, disposable, null) { connected.complete(Unit) }

    val expectedModeChanges = 2
    val flowReceiverCount = AtomicInteger(0)
    val countDownLatch = CountDownLatch(expectedModeChanges)
    val job = GlobalScope.launch(workerThread) {
      smartModeFlow.collect {
        flowReceiverCount.incrementAndGet()
        countDownLatch.countDown()
      }
    }

    runBlocking { connected.await() }

    val isDumb = arrayOf(true, true, false, true, false, true)
    val dumbService = DumbServiceImpl.getInstance(project)
    isDumb.forEach {
      runInEdtAndWait { dumbService.isDumb = it }
    }

    // Wait for the changes to be processed.
    countDownLatch.await()

    // We should see two switches to smart mode.
    runBlocking {
      job.cancel() // Stop the channel
      job.join()
      assertEquals(expectedModeChanges, flowReceiverCount.get()) // ensure that no more of expectedModeChanges have been received
    }
  }

  @Test(timeout = 500)
  fun `read action with write priority yields to write actions`() {
    val readActionIsRunning = CompletableDeferred<Unit>()
    var keepReadActionRunning = true
    runBlocking {
      val job = launch {
        runReadActionWithWritePriority(
          maxRetries = Int.MAX_VALUE,
          maxWaitTime = Long.MAX_VALUE,
          maxWaitTimeUnit = TimeUnit.DAYS, // No timeout
          callable = {
            ApplicationManager.getApplication().assertReadAccessAllowed()
            readActionIsRunning.complete(Unit)
            while (keepReadActionRunning) {
              it()
              Thread.sleep(50)
            }
          }
        )
      }

      launch {
        readActionIsRunning.await()

        var executedWriteActions = 0
        repeat(5) {
          runWriteActionAndWait { executedWriteActions++ }
        }
        keepReadActionRunning = false
        job.join()

        assertEquals(5, executedWriteActions)
      }
    }
  }

  @Test(timeout = 500)
  fun `read action with write priority stops after x retries`() {
    val readActionIsRunning = CompletableDeferred<Unit>()
    runBlocking {
      var retries = 0
      val job = launch {
        try {
          runReadActionWithWritePriority(
            maxRetries = 3,
            maxWaitTime = Long.MAX_VALUE,
            maxWaitTimeUnit = TimeUnit.DAYS, // No timeout
            callable = { checkCancelled ->
              readActionIsRunning.complete(Unit)
              retries++
              thread {
                com.intellij.openapi.application.runWriteActionAndWait { }
              }
              while (true) {
                checkCancelled()
                Thread.sleep(50)
              }
            }
          )
          fail("runReadActionWithWritePriority should never return")
        }
        catch (ignore: RetriesExceededException) {
        }
        catch (t: Throwable) {
          fail("Unexpected exception $t")
        }
      }
      readActionIsRunning.await()
      job.join()
      assertEquals(3, retries)
    }
  }

  @Test(timeout = 2500)
  fun `read action with write priority stops after timeout`() {
    val readActionIsRunning = CompletableDeferred<Unit>()
    runBlocking {
      var retries = 0
      val job = launch {
        try {
          runReadActionWithWritePriority(
            maxWaitTime = 1,
            maxWaitTimeUnit = TimeUnit.SECONDS,
            callable = { checkCancelled ->
              readActionIsRunning.complete(Unit)
              retries++
              while (true) {
                checkCancelled()
                Thread.sleep(50)
              }
            }
          )
          fail("runReadActionWithWritePriority should never return")
        }
        catch (ignore: TimeoutException) {
        }
        catch (t: Throwable) {
          fail("Unexpected exception $t")
        }
      }
      readActionIsRunning.await()

      delay(TimeUnit.SECONDS.toMillis(2))

      job.join()
      assertEquals(1, retries)
    }
  }

  @Test(timeout = 500)
  fun `read action with write priority stops if cancelled`() {
    val readActionIsRunning = CompletableDeferred<Unit>()
    runBlocking {
      val job = launch {
        try {
          runReadActionWithWritePriority(
            maxRetries = Int.MAX_VALUE,
            maxWaitTime = Long.MAX_VALUE,
            maxWaitTimeUnit = TimeUnit.DAYS, // No timeout
            callable = { checkCancelled ->
              readActionIsRunning.complete(Unit)
              while (true) {
                checkCancelled()
                Thread.sleep(50)
              }
            }
          )
          fail("runReadActionWithWritePriority should never return")
        }
        catch (ignore: ProcessCanceledException) {
        }
        catch (t: Throwable) {
          fail("Unexpected exception $t")
        }
      }
      readActionIsRunning.await()
      job.cancel()
      job.join()
    }
  }
}
