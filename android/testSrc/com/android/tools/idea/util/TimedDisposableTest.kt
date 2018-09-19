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

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.WaitFor
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimedDisposableTest {

  @Test
  fun disposeAfterTimeout() {
    val disposableObject = object : Any(), Disposable {
      override fun dispose() {
      }
    }
    Disposer.setDebugMode(true)
    val parentDisposable = Disposer.newDisposable("myDisposable")
    val timedDisposable = TimedDisposable(disposableObject, parentDisposable, 1, TimeUnit.MILLISECONDS)
    object : WaitFor(100, 10) {
      override fun condition(): Boolean = Disposer.isDisposed(timedDisposable)
    }
    assertTrue(Disposer.isDisposed(timedDisposable))
    assertNull(timedDisposable.get())
    Disposer.getTree().assertNoReferenceKeptInTree(disposableObject)
    assertTrue { Disposer.getTree().containsKey(parentDisposable) }
    Disposer.dispose(parentDisposable)
  }

  @Test
  fun disposeManually() {
    val disposableObject = object : Any(), Disposable {
      override fun dispose() {
      }
    }
    Disposer.setDebugMode(true)
    val parentDisposable = Disposer.newDisposable("myDisposable")
    val timedDisposable = TimedDisposable(disposableObject, parentDisposable, 1, TimeUnit.DAYS)
    Disposer.dispose(timedDisposable)
    assertNull(timedDisposable.get())
    Disposer.getTree().assertNoReferenceKeptInTree(disposableObject)
    assertTrue { Disposer.getTree().containsKey(parentDisposable) }
    Disposer.dispose(parentDisposable)
  }

  @Test
  fun disposeAfterParentDisposed() {
    val disposableObject = object : Any(), Disposable {
      override fun dispose() {
      }
    }
    val parentDisposable = Disposer.newDisposable()
    val timedDisposable = TimedDisposable(disposableObject, parentDisposable, 10, TimeUnit.SECONDS)
    Disposer.dispose(parentDisposable)
    assertNull(timedDisposable.get())
    Disposer.getTree().assertNoReferenceKeptInTree(disposableObject)
    assertFalse { Disposer.getTree().containsKey(parentDisposable) }
  }
}