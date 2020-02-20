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

import com.android.tools.idea.layoutinspector.legacydevice.LegacyClient
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.transport.DisconnectedClient
import com.android.tools.idea.layoutinspector.transport.InspectorClient
import com.android.tools.layoutinspector.proto.LayoutInspectorProto
import com.android.tools.profiler.proto.Common
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.Messages
import java.util.concurrent.atomic.AtomicLong

@VisibleForTesting
const val SHOW_ERROR_MESSAGES_IN_DIALOG = false

class LayoutInspector(val layoutInspectorModel: InspectorModel) {
  var currentClient: InspectorClient = DisconnectedClient
    private set(client) {
      if (field != client) {
        field.disconnect()
      }
      field = client
      layoutInspectorModel.updateConnection(client)
    }

  private val latestLoadTime = AtomicLong(-1)

  val allClients: List<InspectorClient>

  init {
    val defaultClient = InspectorClient.createInstance(layoutInspectorModel)
    registerClientListeners(defaultClient)
    val legacyClient = LegacyClient(layoutInspectorModel.project)
    registerClientListeners(legacyClient)
    allClients = listOf(defaultClient, legacyClient)
  }

  private fun registerClientListeners(client: InspectorClient) {
    client.register(Common.Event.EventGroupIds.LAYOUT_INSPECTOR_ERROR, ::logError)
    client.register(Common.Event.EventGroupIds.COMPONENT_TREE) { event ->
      // TODO: maybe find a better place to do this?
      currentClient = client
      loadComponentTree(event)
    }
    client.registerProcessChanged(::clearComponentTreeWhenProcessEnds)
  }

  private fun loadComponentTree(event: Any) {
    val time = System.currentTimeMillis()
    val allIds = currentClient.treeLoader.getAllWindowIds(event, currentClient)
    val root = currentClient.treeLoader.loadComponentTree(event, layoutInspectorModel.resourceLookup, currentClient)
    if (root != null && allIds != null) {
      ApplicationManager.getApplication().invokeLater {
        synchronized(latestLoadTime) {
          if (latestLoadTime.get() > time) {
            return@invokeLater
          }
          latestLoadTime.set(time)
          layoutInspectorModel.update(root, root.drawId, allIds)
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

  private fun clearComponentTreeWhenProcessEnds() {
    if (currentClient.isConnected) {
      return
    }
    currentClient = DisconnectedClient
    val application = ApplicationManager.getApplication()
    application.invokeLater {
      layoutInspectorModel.update(null, 0, listOf())
    }
  }
}
