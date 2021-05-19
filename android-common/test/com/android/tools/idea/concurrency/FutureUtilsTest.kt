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
package com.android.tools.idea.concurrency

import com.google.common.truth.Truth
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import com.google.common.util.concurrent.SettableFuture
import com.intellij.openapi.util.Disposer
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor

/**
 * Tests for [com.android.tools.idea.concurrency.FutureUtils].
 */
class FutureUtilsTest {
  @Test
  fun testTransform() {
    val future = SettableFuture.create<Int>()
    val transformedFuture = future.transform(directExecutor()) { i -> "$i" }

    future.set(1337)
    Truth.assertThat(transformedFuture.get()).isEqualTo("1337")
  }

  @Test
  fun testTransformAsync() {
    val future1 = SettableFuture.create<String>()
    val future2 = SettableFuture.create<String>()
    val transformedFuture = future1.transformAsync(directExecutor()) { i1 -> future2.transform(directExecutor()) { i2 -> "$i1 $i2" } }

    future1.set("foo")
    future2.set("bar")
    Truth.assertThat(transformedFuture.get()).isEqualTo("foo bar")
  }

  @Test
  fun testFinallySync() {
    val future = SettableFuture.create<Int>()
    var value = 0
    val result = future.finallySync(directExecutor()) { value = 42 }
    future.set(1)

    result.get()

    Truth.assertThat(value).isEqualTo(42)
    Truth.assertThat(result.get()).isEqualTo(1)
  }

  @Test
  fun testFinallySyncFinallyIsCalledIfFutureFails() {
    val future = SettableFuture.create<Int>()
    var value = 0
    val result = future.finallySync(directExecutor()) { value = 42 }
    future.setException(RuntimeException())

    Truth.assertThat(value).isEqualTo(42)

    try {
      result.get()
      fail("Future should have thrown")
    } catch (expected: ExecutionException) {
    }
  }

  @Test
  fun testFinallySyncErrorInFinally() {
    val future = SettableFuture.create<Int>()
    val result = future.finallySync(directExecutor()) { throw RuntimeException("fail in finally") }
    future.set(1)

    try {
      result.get()
      fail("Future should have thrown")
    } catch (expected: ExecutionException) {
    }
  }

  @Test
  fun testFinallySyncCancelled() {
    val future = SettableFuture.create<Int>()
    var executionCounter = 0
    val result = future.finallySync(directExecutor()) { executionCounter++ }
    result.cancel(true)
    Truth.assertThat(executionCounter).isEqualTo(1)
    Truth.assertThat(future.isCancelled).isTrue()
  }

  @Test
  fun testFinallySyncInputCancelled() {
    val future = SettableFuture.create<Int>()
    var executionCounter = 0
    val result = future.finallySync(directExecutor()) { executionCounter++ }
    future.cancel(true)
    Truth.assertThat(executionCounter).isEqualTo(1)
    Truth.assertThat(result.isCancelled).isTrue()
  }

  @Test
  fun testCatching() {
    val future = SettableFuture.create<Int>()
    val result = future.catching(directExecutor(), RuntimeException::class.java, { t ->
      Truth.assertThat(t.message).isEqualTo("my error")
      42
    })
    future.setException(RuntimeException("my error"))

    Truth.assertThat(result.get()).isEqualTo(42)
  }

  @Test
  fun testCatchingFutureDoesNotFail() {
    val future = SettableFuture.create<Int>()
    val result = future.catching(directExecutor(), RuntimeException::class.java, { _ -> 42 })
    future.set(1)

    Truth.assertThat(result.get()).isEqualTo(1)
  }

  @Test
  fun testExecuteAsync() {
    val executor = directExecutor()
    val future = executor.executeAsync { 42 }

    Truth.assertThat(future.get()).isEqualTo(42)
  }

  @Test
  fun testExecuteAsyncError() {
    val executor = directExecutor()
    val future = executor.executeAsync { throw RuntimeException("my error") }

    try {
      future.get()
      fail("Future should have thrown")
    } catch (expected: ExecutionException) {
    }
  }

  @Test
  fun testExecuteAsyncCancellation() {
    val tasks = mutableListOf<Runnable>()
    val delayedExecutor = Executor { command -> tasks.add(command) }
    var ignored = true
    val future = delayedExecutor.executeAsync { ignored = false }
    future.cancel(true)
    tasks.removeAt(0).run()
    Truth.assertThat(ignored).isTrue()
  }

  @Test
  fun testCancelOnDisposeParentDisposed() {
    val parent = Disposer.newDisposable()
    val future = SettableFuture.create<String>()
    future.cancelOnDispose(parent)
    Disposer.dispose(parent)
    Truth.assertThat(future.isCancelled).isTrue()
  }
}
