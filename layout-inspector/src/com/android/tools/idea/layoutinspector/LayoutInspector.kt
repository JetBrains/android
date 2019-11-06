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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.layoutinspector.legacydevice.LegacyClient
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.transport.DisconnectedClient
import com.android.tools.idea.layoutinspector.transport.InspectorClient
import com.android.tools.layoutinspector.proto.LayoutInspectorProto
import com.android.tools.profiler.proto.Common
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.Messages
import java.util.concurrent.atomic.AtomicLong

// TODO: Set this to false before turning the dynamic layout inspector on by default
private const val DEBUG = true

class LayoutInspector(val layoutInspectorModel: InspectorModel) {
  var currentClient: InspectorClient = DisconnectedClient
    private set(client) {
      if (field != client) {
        field.disconnect()
      }
      field = client
    }

  private val latestLoadTime = AtomicLong(-1)

  val allClients: List<InspectorClient>

  init {
    val defaultClient = InspectorClient.createInstance(layoutInspectorModel.project)
    registerClientListeners(defaultClient)

    if (StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_LEGACY_DEVICE_SUPPORT.get()) {
      val legacyClient = LegacyClient(layoutInspectorModel.project)
      registerClientListeners(legacyClient)
      allClients = listOf(defaultClient, legacyClient)
    }
    else {
      allClients = listOf(defaultClient)
    }
  }

  private fun registerClientListeners(client: InspectorClient) {
    client.register(Common.Event.EventGroupIds.LAYOUT_INSPECTOR_ERROR, ::showError)
    client.register(Common.Event.EventGroupIds.COMPONENT_TREE) { event ->
      // TODO: maybe find a better place to do this?
      currentClient = client
      loadComponentTree(event)
    }
    client.registerProcessChanged(::clearComponentTreeWhenProcessEnds)
  }

  private fun loadComponentTree(event: Any) {
    val time = System.currentTimeMillis()
    val root = currentClient.treeLoader.loadComponentTree(event, layoutInspectorModel.resourceLookup, currentClient)
    if (root != null) {
      ApplicationManager.getApplication().invokeLater {
        synchronized(latestLoadTime) {
          if (latestLoadTime.get() > time) {
            return@invokeLater
          }
          latestLoadTime.set(time)
          layoutInspectorModel.update(root)
        }
      }
    }
  }

  private fun showError(event: Any) {
    val error = when (event) {
      is LayoutInspectorProto.LayoutInspectorEvent -> event.errorMessage
      is String -> event
      else -> "Unknown Error"
    }

    Logger.getInstance(LayoutInspector::class.java.canonicalName).warn(error)

    @Suppress("ConstantConditionIf")
    if (DEBUG) {
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
      layoutInspectorModel.update(null)
    }
  }
}
