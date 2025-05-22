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
package com.android.tools.idea.gradle.structure.configurables.ui

import com.android.tools.idea.concurrency.addCallback
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import org.jetbrains.ide.PooledThreadExecutor

internal fun <I, O> ListenableFuture<I>.continueOnEdt(function: (I) -> O) =
  Futures.transform(
    this,
    { function(it!!) },
    {
      val application = ApplicationManager.getApplication()
      if (application.isDispatchThread) {
        it.run()
      }
      else {
        application.invokeLater({ if (!isCancelled) it.run() }, ModalityState.any())
      }
    })

internal fun <I> ListenableFuture<I>.whenCompletedInvokeOnEdt(callback: (ListenableFuture<I>) -> Unit) =
  also {
    it.addCallback(MoreExecutors.directExecutor(), object : FutureCallback<I> {
      override fun onSuccess(result: I) = invokeCallback()
      override fun onFailure(t: Throwable) = invokeCallback()

      private fun invokeCallback() {
        if (ApplicationManager.getApplication().isUnitTestMode) {
          callback(this@whenCompletedInvokeOnEdt)
        } else {
          com.intellij.openapi.application.invokeLater { callback(this@whenCompletedInvokeOnEdt) }
        }
      }
    })
  }

internal fun <T> ListenableFuture<T>.handleFailureOnEdt(function: (Throwable?) -> Unit): ListenableFuture<T?> =
  Futures.catching(
    this,
    Throwable::class.java,
    { ex ->
      val application = ApplicationManager.getApplication()
      if (application.isDispatchThread) function(ex)
      else application.invokeLater({ function(ex) }, ModalityState.any())
      null
    },
    MoreExecutors.directExecutor())

internal fun <I, O> ListenableFuture<I>.invokeLater(function: (I) -> O) =
  Futures.transform(
    this, { function(it!!) },
    {
      val application = ApplicationManager.getApplication()
      if (application.isUnitTestMode) {
        it.run()
      }
      else {
        application.invokeLater(it, ModalityState.any())
      }
    })

internal fun <T> readOnPooledThread(function: () -> T): ListenableFuture<T> =
  MoreExecutors.listeningDecorator(PooledThreadExecutor.INSTANCE).submit<T> { ReadAction.compute<T, Throwable>(function) }
