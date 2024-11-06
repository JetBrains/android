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
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.res.ResourceNotificationManager
import com.android.tools.rendering.RenderResult
import com.google.common.collect.ImmutableSet
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.concurrency.EdtExecutorService
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/** [LayoutlibSceneManager] used for tests that performs all operations synchronously. */
open class SyncLayoutlibSceneManager(
  surface: DesignSurface<LayoutlibSceneManager>,
  model: NlModel,
) :
  LayoutlibSceneManager(
    model,
    surface,
    EdtExecutorService.getInstance(),
    LayoutlibSceneManagerHierarchyProvider(),
    DISABLED,
  ) {
  var ignoreRenderRequests: Boolean = false
  var ignoreModelUpdateRequests: Boolean = false

  init {
    sceneRenderConfiguration.setRenderModuleWrapperForTest { TestRenderModelModule(it) }
    sceneRenderConfiguration.setRenderTaskBuilderWrapperForTest { it.disableSecurityManager() }
  }

  /**
   * Allows to set desirable render result for tests, if result is not set returns default value.
   */
  override var renderResult: RenderResult? = null
    get() = field ?: super.renderResult

  override fun requestRender() {
    if (ignoreRenderRequests) return
    runBlocking { requestRenderAndWait() }
    runInEdtAndWait { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
  }

  override suspend fun requestRenderAndWait() =
    withContext(workerThread) {
      if (ignoreRenderRequests) return@withContext
      super.requestRenderAndWait()
    }

  override fun executeInRenderSessionAsync(
    block: Runnable,
    timeout: Long,
    timeUnit: TimeUnit,
  ): CompletableFuture<Void> {
    block.run()
    return CompletableFuture.completedFuture(null)
  }

  fun putDefaultPropertyValue(
    component: NlComponent,
    namespace: ResourceNamespace,
    attributeName: String,
    value: String,
  ) {
    if (renderResult == null) {
      sceneRenderConfiguration.needsInflation.set(true)
      requestRender()
    }
    val map: MutableMap<ResourceReference, ResourceValue> =
      renderResult!!.defaultProperties.getOrPut(component.snapshot!!) { HashMap() }
    val reference = ResourceReference.attr(namespace, attributeName)
    val resourceValue: ResourceValue =
      StyleItemResourceValueImpl(namespace, attributeName, value, null)
    if (map[reference] != resourceValue) {
      // Make sure to "emulate" the consequences of a change
      resourcesChanged(ImmutableSet.of(ResourceNotificationManager.Reason.EDIT))
    }
    map[reference] = resourceValue
  }
}
