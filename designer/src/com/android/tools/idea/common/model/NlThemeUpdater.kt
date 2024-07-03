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
package com.android.tools.idea.common.model

import com.android.annotations.concurrency.Slow
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.resources.ResourceResolver
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.configurations.Configuration
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicReference

/** Updates theme of [Configuration]. */
class NlThemeUpdater(
  private val configuration: () -> Configuration,
  private val parentDisposable: Disposable,
) {

  private val themeUpdateComputation = AtomicReference<Disposable?>()

  /** Executor used for asynchronous updates. */
  private val updateExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("NlModel", 1)

  /**
   * Returns the latest calculated [ResourceResolver]. This is just to be used from those context
   * where obtaining the resource resolver can not be done like the UI thread. The cached resource
   * resolver is updated after every model update, including theme changes.
   */
  @get:Deprecated("Call Configuration.getResourceResolver from a background context")
  var cachedResourceResolver: ResourceResolver = configuration().resourceResolver
    private set

  fun updateTheme() {
    val computationToken = Disposer.newDisposable()
    Disposer.register(parentDisposable, computationToken)
    val oldComputation = themeUpdateComputation.getAndSet(computationToken)
    if (oldComputation != null) {
      Disposer.dispose(oldComputation)
    }
    ReadAction.nonBlocking(
        Callable<Void?> {
          if (themeUpdateComputation.get() !== computationToken) {
            return@Callable null // A new update has already been scheduled.
          }
          val themeUrl = ResourceUrl.parse(configuration().theme)
          if (themeUrl != null && themeUrl.type == ResourceType.STYLE) {
            updateTheme(themeUrl, computationToken)
          }
          null
        }
      )
      .expireWith(computationToken)
      .submit(updateExecutor)
  }

  @Slow
  private fun updateTheme(themeUrl: ResourceUrl, computationToken: Disposable) {
    if (themeUpdateComputation.get() !== computationToken) {
      return // A new update has already been scheduled.
    }
    try {
      val resolver = configuration().resourceResolver
      val themeReference =
        ResourceReference.style(
          if (themeUrl.isFramework) ResourceNamespace.ANDROID else ResourceNamespace.RES_AUTO,
          themeUrl.name,
        )
      if (resolver.getStyle(themeReference) == null) {
        val theme = configuration().preferredTheme
        if (themeUpdateComputation.get() !== computationToken) {
          return // A new update has already been scheduled.
        }
        configuration().setTheme(theme)
        cachedResourceResolver = configuration().resourceResolver
      }
    } finally {
      if (themeUpdateComputation.compareAndSet(computationToken, null)) {
        Disposer.dispose(computationToken)
      }
    }
  }
}
