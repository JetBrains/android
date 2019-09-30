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
import com.android.tools.idea.layoutinspector.transport.InspectorClient
import com.android.tools.layoutinspector.proto.LayoutInspectorProto
import com.android.tools.profiler.proto.Common
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.Messages
import kotlin.properties.Delegates

// TODO: Set this to false before turning the dynamic layout inspector on by default
private const val DEBUG = true

class LayoutInspector(layoutInspectorModel: InspectorModel) {
  val modelChangeListeners = mutableListOf<(InspectorModel, InspectorModel) -> Unit>()
  val client = InspectorClient.createInstance(layoutInspectorModel.project)

  init {
    client.register(Common.Event.EventGroupIds.LAYOUT_INSPECTOR_ERROR, ::showError)
  }

  var layoutInspectorModel: InspectorModel by Delegates.observable(layoutInspectorModel) { _, old, new ->
    modelChangeListeners.forEach { it(old, new) }
  }

  private fun showError(event: LayoutInspectorProto.LayoutInspectorEvent) {
    Logger.getInstance(LayoutInspector::class.java.canonicalName).warn(event.errorMessage)

    @Suppress("ConstantConditionIf")
    if (DEBUG) {
      ApplicationManager.getApplication().invokeLater {
        Messages.showErrorDialog(layoutInspectorModel.project, event.errorMessage, "Inspector Error")
      }
    }
  }
}
