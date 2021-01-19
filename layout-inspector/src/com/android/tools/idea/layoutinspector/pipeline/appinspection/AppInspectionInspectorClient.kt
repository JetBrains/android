/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.pipeline.appinspection

import com.android.ddmlib.AndroidDebugBridge
import com.android.tools.idea.appinspection.api.AppInspectionApiServices
import com.android.tools.idea.appinspection.ide.AppInspectionDiscoveryService
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.concurrency.coroutineScope
import com.android.tools.idea.concurrency.createChildScope
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.pipeline.AbstractInspectorClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientSettings
import com.android.tools.idea.layoutinspector.pipeline.TreeLoader
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.ViewLayoutInspectorClient
import com.android.tools.idea.layoutinspector.properties.PropertiesProvider
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.TestOnly
import java.util.EnumSet

/**
 * An [InspectorClient] that talks to an app-inspection based inspector running on a target device.
 *
 * @param apiServices App inspection services used for initializing and shutting down app
 *     inspection-based inspectors.
 * @param scope App inspection APIs use coroutines, while this class's interface does not, so this
 *     coroutine scope is used to handle the bridge between the two approaches.
 */
class AppInspectionInspectorClient(
  private val adb: AndroidDebugBridge,
  process: ProcessDescriptor,
  private val model: InspectorModel,
  @TestOnly private val apiServices: AppInspectionApiServices = AppInspectionDiscoveryService.instance.apiServices,
  @TestOnly private val scope: CoroutineScope = model.project.coroutineScope.createChildScope(true),
) : AbstractInspectorClient(process) {

  private lateinit var viewInspector: ViewLayoutInspectorClient
  private lateinit var propertiesProvider: AppInspectionPropertiesProvider
  private val exceptionHandler = CoroutineExceptionHandler { _, t ->
    fireError(t.message!!)
  }

  private val debugViewAttributes = DebugViewAttributes(adb, model.project, process)

  override fun doConnect() {
    runBlocking {
      viewInspector = ViewLayoutInspectorClient.launch(apiServices, model.project, process, scope, ::fireError, ::fireTreeEvent)
      propertiesProvider = AppInspectionPropertiesProvider(viewInspector, model)
    }

    debugViewAttributes.set()

    if (isCapturing) {
      startFetching()
    }
    else {
      refresh()
    }
  }

  override fun doDisconnect(): ListenableFuture<Nothing> {
    val future = SettableFuture.create<Nothing>()
    scope.launch(exceptionHandler) {
      debugViewAttributes.clear()
      viewInspector.disconnect()
      future.set(null)
    }
    return future
  }

  override fun startFetching() {
    scope.launch(exceptionHandler) {
      viewInspector.startFetching(continuous = true)
    }
  }

  override fun stopFetching() {
    scope.launch(exceptionHandler) {
      viewInspector.stopFetching()
    }
  }

  override fun refresh() {
    scope.launch(exceptionHandler) {
      viewInspector.startFetching(continuous = false)
    }
  }

  override val capabilities = EnumSet.of(InspectorClient.Capability.SUPPORTS_CONTINUOUS_MODE)!!
  override val treeLoader: TreeLoader = AppInspectionTreeLoader(model.project)
  override val provider: PropertiesProvider
    get() = propertiesProvider
  override val isCapturing: Boolean
    get() = InspectorClientSettings.isCapturingModeOn
}
