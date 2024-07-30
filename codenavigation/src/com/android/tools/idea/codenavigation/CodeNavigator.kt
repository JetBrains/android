/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.codenavigation

import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.pom.Navigatable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.function.Supplier

/**
 * Base class for a service responsible for handling navigating to [CodeLocation]s, as well as
 * registering and triggering listeners interested in the event.
 */
class CodeNavigator (source: NavSource, private val executor: Executor) {
  /**
   * Interface for where the [CodeNavigator] should execute actions.
   */
  interface Executor {
    fun onForeground(runnable: Runnable)
    fun onBackground(runnable: Runnable): Future<*>
  }

  companion object {
    /**
     * An executor to be used when using the code navigator within an application to avoid blocking
     * the UI thread.
     */
    val applicationExecutor = object : Executor {
      override fun onForeground(runnable: Runnable) {
        ApplicationManager.getApplication().invokeLater(runnable)
      }

      override fun onBackground(runnable: Runnable): Future<*> {
        return ApplicationManager.getApplication().executeOnPooledThread(runnable)
      }
    }

    /**
     * An executor to be used in tests to avoid a dependency on an application existing.
     */
    val testExecutor = object : Executor {
      override fun onForeground(runnable: Runnable) {
        runnable.run()
      }

      override fun onBackground(runnable: Runnable): Future<*> {
        return CompletableFuture.runAsync(runnable)
      }
    }
  }

  @VisibleForTesting val mySource = source

  private val myListeners = mutableListOf<Listener>()

  /**
   * Supplier of the target CPU architecture (e.g. arm64, x86, etc) used to build the process
   * currently being profiled.
   */
  var cpuArchSource: Supplier<String?> = Supplier { null }

  fun addListener(listener: Listener) {
    myListeners.add(listener)
  }

  fun removeListener(listener: Listener) {
    myListeners.remove(listener)
  }

  fun navigate(location: CodeLocation): CompletableFuture<Boolean>? {
    myListeners.forEach{ it.onNavigated(location) }

    return getNavigatableAsync(location).thenApplyAsync(
      { nav: Navigatable? ->
        nav?.navigate(true)
        nav != null
      }) { runnable: Runnable? -> executor.onForeground(runnable!!)}
  }

  fun isNavigatable(location: CodeLocation): CompletableFuture<Boolean>? {
    return getNavigatableAsync(location).thenApply { nav: Navigatable? -> nav != null }
  }

  /**
   * Gets the navigatable in another thread, so we don't block the UI while potentially performing
   * heavy operations, such as searching for the java class/method in the PSI tree or using
   * llvm-symbolizer to get a native function name.
   *
   * Note: due to IntelliJ PSI threading rules, read operations performed on a non-UI thread need
   * to wrap the action in a ReadAction. Hence all PSI-reading code inside getNavigatable() will
   * need to wrapped in a ReadAction.
   *
   * See https://plugins.jetbrains.com/docs/intellij/threading-model.html
   */
  private fun getNavigatableAsync(location: CodeLocation): CompletableFuture<Navigatable?> {
    return CompletableFuture.supplyAsync(
      {
        ReadAction.compute(
          ThrowableComputable<Navigatable, RuntimeException> { getNavigatable(location) })
      }
    ) { runnable: Runnable? -> executor.onBackground(runnable!!) }
  }

  private fun getNavigatable(location: CodeLocation): Navigatable? {
    return mySource.lookUp(location, cpuArchSource.get())
  }

  interface Listener {
    fun onNavigated(location: CodeLocation)
  }
}