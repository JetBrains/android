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
package com.android.tools.idea.layoutinspector

import com.android.ddmlib.AndroidDebugBridge
import com.android.tools.idea.appinspection.api.process.ProcessesModel
import com.android.tools.idea.concurrency.AndroidExecutors
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.pipeline.DisconnectedClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.layoutinspector.proto.LayoutInspectorProto
import com.android.tools.profiler.proto.Common
import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.Messages
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

@VisibleForTesting
const val SHOW_ERROR_MESSAGES_IN_DIALOG = false

/**
 * Create the top level class which manages the high level state of layout inspection.
 *
 * @param executor An executor used for doing background work like loading tree data and
 *    initializing the model. Exposed mainly for testing, where a direct executor can provide
 *    consistent behavior over performance.
 */
class LayoutInspector(
  adb: AndroidDebugBridge,
  val processes: ProcessesModel,
  val layoutInspectorModel: InspectorModel,
  parentDisposable: Disposable,
  @TestOnly private val executor: Executor = AndroidExecutors.getInstance().workerThreadExecutor) {

  val currentClient: InspectorClient
    get() = currentClientReference.get()

  private val currentClientReference = AtomicReference<InspectorClient>(DisconnectedClient)
  private val latestLoadTime = AtomicLong(-1)

  val allClients: List<InspectorClient> = InspectorClient.createInstances(adb, processes, layoutInspectorModel, parentDisposable)

  private val sequentialDispatcher = MoreExecutors.newSequentialExecutor(executor)

  init {
    allClients.forEach { registerClientListeners(it) }
  }

  private fun registerClientListeners(client: InspectorClient) {
    client.register(Common.Event.EventGroupIds.LAYOUT_INSPECTOR_ERROR, ::logError)
    client.register(Common.Event.EventGroupIds.COMPONENT_TREE, ::loadComponentTree)
    client.registerProcessChanged(::processChanged)
  }

  @TestOnly
  fun setCurrentTestClient(client: InspectorClient) {
    currentClientReference.set(client)
  }

  private fun processChanged(client: InspectorClient) {
    if (client.isConnected) {
      val oldClient = currentClientReference.getAndSet(client)
      if (oldClient !== client) {
        oldClient.disconnect()
        layoutInspectorModel.updateConnection(client)
      }
    }
    else if (currentClientReference.compareAndSet(client, DisconnectedClient)) {
      layoutInspectorModel.updateConnection(DisconnectedClient)
      ApplicationManager.getApplication().invokeLater {
        if (currentClient === DisconnectedClient) {
          layoutInspectorModel.update(null, listOf<Any>(), 0)
        }
      }
    }
  }

  private fun loadComponentTree(event: Any) {
    // TODO: If there are many calls to loadComponentTree done before the first one finishes,
    //  intermediate requests are needless and could be skipped.
    sequentialDispatcher.execute {
      val time = System.currentTimeMillis()
      val treeLoader = currentClient.treeLoader
      val allIds = treeLoader.getAllWindowIds(event)
      val (window, generation) = treeLoader.loadComponentTree(event, layoutInspectorModel.resourceLookup) ?: return@execute
      if (allIds != null) {
        synchronized(latestLoadTime) {
          if (latestLoadTime.get() > time) {
            return@execute
          }
          latestLoadTime.set(time)
          layoutInspectorModel.update(window, allIds, generation)
        }
      }
    }
  }

  private fun logError(event: Any) {
    val error = when (event) {
      is LayoutInspectorProto.LayoutInspectorEvent -> event.errorMessage
      is String -> event
      else -> "Unknown Error"
    }

    Logger.getInstance(LayoutInspector::class.java.canonicalName).warn(error)

    @Suppress("ConstantConditionIf")
    if (SHOW_ERROR_MESSAGES_IN_DIALOG) {
      ApplicationManager.getApplication().invokeLater {
        Messages.showErrorDialog(layoutInspectorModel.project, error, "Inspector Error")
      }
    }
  }
}
