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

import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.transport.DisconnectedClient
import com.android.tools.idea.layoutinspector.transport.InspectorClient
import com.android.tools.layoutinspector.proto.LayoutInspectorProto
import com.android.tools.profiler.proto.Common
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

@VisibleForTesting
const val SHOW_ERROR_MESSAGES_IN_DIALOG = false

class LayoutInspector(val layoutInspectorModel: InspectorModel, parentDisposable: Disposable) : Disposable {
  val currentClient: InspectorClient
    get() = currentClientReference.get()

  private val currentClientReference = AtomicReference<InspectorClient>(DisconnectedClient)
  private val latestLoadTime = AtomicLong(-1)

  val allClients: List<InspectorClient> = InspectorClient.createInstances(layoutInspectorModel, this)

  init {
    allClients.forEach { registerClientListeners(it) }
    Disposer.register(parentDisposable, this)
  }

  override fun dispose() {
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
    // TODO: this should only run once at a time. If more requests are made while one is being processed, the last request should be
    // processed after the existing run is complete, and intermediate ones should be dropped.
    val time = System.currentTimeMillis()
    val allIds = currentClient.treeLoader.getAllWindowIds(event, currentClient)
    val (window, generation) = currentClient.treeLoader.loadComponentTree(event, layoutInspectorModel.resourceLookup,
                                                                          currentClient, layoutInspectorModel.project) ?: return
    if (allIds != null) {
      synchronized(latestLoadTime) {
         if (latestLoadTime.get() > time) {
          return
        }
        latestLoadTime.set(time)
        layoutInspectorModel.update(window, allIds, generation)
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
