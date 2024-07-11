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
package com.android.tools.idea.uibuilder.scene

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.ResourceValue
import com.android.ide.common.rendering.api.StyleItemResourceValueImpl
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.LayoutScannerConfiguration.Companion.DISABLED
import com.android.tools.rendering.RenderResult
import com.android.tools.rendering.RenderService.RenderTaskBuilder
import com.android.tools.rendering.api.RenderModelModule
import com.google.wireless.android.sdk.stats.LayoutEditorRenderResult
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.ui.update.Update
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Function

/** Number of seconds to wait for the render to complete in any of the render calls. */
private const val RENDER_TIMEOUT_SECS = 60L

/** [LayoutlibSceneManager] used for tests that performs all operations synchronously. */
open class SyncLayoutlibSceneManager(
  surface: DesignSurface<LayoutlibSceneManager>,
  model: NlModel,
) :
  LayoutlibSceneManager(
    model,
    surface,
    EdtExecutorService.getInstance(),
    Function {
      object : RenderingQueue {
        override fun queue(update: Update) {
          update.run()
        }
      }
    },
    LayoutlibSceneManagerHierarchyProvider(),
    DISABLED,
  ) {
  var ignoreRenderRequests: Boolean = false
  var ignoreModelUpdateRequests: Boolean = false

  private fun <T> waitForFutureWithoutBlockingUiThread(
    future: CompletableFuture<T>
  ): CompletableFuture<T> {
    if (ApplicationManager.getApplication().isDispatchThread) {
      // If this is happening in the UI thread, keep dispatching the events in the UI thread while
      // we are waiting
      PlatformTestUtil.waitForFuture(future, TimeUnit.SECONDS.toMillis(RENDER_TIMEOUT_SECS))
    }

    val result =
      CompletableFuture.completedFuture(
        future.orTimeout(RENDER_TIMEOUT_SECS, TimeUnit.SECONDS).join()
      )

    // After running render calls, there might be pending actions to run on the UI thread, dispatch
    // those to ensure that after this call, everything
    // is done.
    ApplicationManager.getApplication().invokeAndWait {
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }
    return result
  }

  override fun renderAsync(
    trigger: LayoutEditorRenderResult.Trigger?,
    reverseUpdate: AtomicBoolean,
  ): CompletableFuture<RenderResult> {
    if (ignoreRenderRequests) {
      return CompletableFuture.completedFuture(null)
    }
    return waitForFutureWithoutBlockingUiThread(super.renderAsync(trigger, reverseUpdate))
  }

  override fun requestRenderAsync(): CompletableFuture<Void> {
    if (ignoreRenderRequests) {
      return CompletableFuture.completedFuture(null)
    }
    val result = waitForFutureWithoutBlockingUiThread(super.requestRenderAsync())
    return result
  }

  override fun requestRenderAsync(
    trigger: LayoutEditorRenderResult.Trigger?,
    reverseUpdate: AtomicBoolean,
  ): CompletableFuture<Void> {
    if (ignoreRenderRequests) {
      return CompletableFuture.completedFuture(null)
    }
    return waitForFutureWithoutBlockingUiThread(super.requestRenderAsync(trigger, reverseUpdate))
  }

  override fun updateModelAsync(): CompletableFuture<Void> {
    if (ignoreModelUpdateRequests) {
      return CompletableFuture.completedFuture(null)
    }
    return waitForFutureWithoutBlockingUiThread(super.updateModelAsync())
  }

  override fun wrapRenderModule(core: RenderModelModule): RenderModelModule {
    return TestRenderModelModule(core)
  }

  override fun setupRenderTaskBuilder(taskBuilder: RenderTaskBuilder): RenderTaskBuilder {
    return super.setupRenderTaskBuilder(taskBuilder).disableSecurityManager()
  }

  fun putDefaultPropertyValue(
    component: NlComponent,
    namespace: ResourceNamespace,
    attributeName: String,
    value: String,
  ) {
    if (renderResult == null) {
      updateModelAsync().join()
    }
    var map: MutableMap<ResourceReference, ResourceValue> =
      renderResult!!.defaultProperties.getOrPut(component.snapshot!!) { HashMap() }
    val reference = ResourceReference.attr(namespace, attributeName)
    val resourceValue: ResourceValue =
      StyleItemResourceValueImpl(namespace, attributeName, value, null)
    map[reference] = resourceValue
  }
}
