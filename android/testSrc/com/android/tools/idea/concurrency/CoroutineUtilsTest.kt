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
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

const val UI_THREAD = "UI thread"
const val WORKER_THREAD = "Worker thread"
const val IO_THREAD = "IO thread"

class CoroutineUtilsTest {

  @get:Rule
  val projectRule = ProjectRule()

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
    ApplicationManager.getApplication().replaceService(AndroidExecutors::class.java, testExecutors, projectRule.project)
  }

  @After
  fun tearDown() {
    uiExecutor.shutdown()
    workerExecutor.shutdown()
    ioExecutor.shutdown()
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

    val defaultProcessor = LoggedErrorProcessor.getInstance()
    try {
      LoggedErrorProcessor.setNewInstance(object : LoggedErrorProcessor() {
        override fun processError(category: String, message: String, t: Throwable?, details: Array<out String>): Boolean {
          messages.add(message)
          return false
        }
      })

      val fooManager = FooManager()
      Disposer.register(projectRule.project, fooManager)

      fooManager.compute1()
      fooManager.compute2()

      workerExecutor.shutdown()
      workerExecutor.awaitTermination(2, TimeUnit.SECONDS)
      assertThat(messages).containsExactly("expected failure", "computing")
    }
    finally {
      LoggedErrorProcessor.setNewInstance(defaultProcessor)
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
}
