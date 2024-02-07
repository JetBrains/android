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
@file:JvmName("DisposableUtils")
package org.jetbrains.android.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.containers.ContainerUtil
import java.util.concurrent.atomic.AtomicBoolean

/** Disposable that gets disposed when any of its parents is disposed. */
open class MultiParentDisposable(vararg parents: Disposable) : Disposable {

  private val parentAdapters = ContainerUtil.createLockFreeCopyOnWriteList<ParentAdapter>()
  private val disposing = AtomicBoolean()

  init {
    require(parents.size >= 2) { "Use of this class with less than two disposable parents is a waste of resources" }
    Disposer.register(this, Cleaner(parentAdapters))
    for (parent in parents) {
      parentAdapters.add(ParentAdapter(parent))
    }
  }

  override fun dispose() {
  }

  private fun triggerDisposal() {
    if (!disposing.getAndSet(true)) {
      Disposer.dispose(this)
    }
  }

  private inner class ParentAdapter(parent: Disposable) : Disposable {

    init {
      Disposer.register(parent, this)
    }

    override fun dispose() {
      parentAdapters.remove(this)
      triggerDisposal()
    }
  }

  private class Cleaner(val disposables: List<Disposable>) : Disposable {

    override fun dispose() {
      for (disposable in disposables) {
        Disposer.dispose(disposable)
      }
    }
  }
}

/**
 * Executes [runnable] exactly once when the first of [disposables] is disposed. To avoid a memory
 * leak, it is important to provide all disposables that on disposal should either trigger
 * [runnable] or allow it to be garbage collected.
 */
fun runOnDisposalOfAnyOf(vararg disposables: Disposable, runnable: Runnable) {
  object : MultiParentDisposable(*disposables) {
    override fun dispose() {
      runnable.run()
    }
  }
}
