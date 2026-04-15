/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.gemini

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class NepFineTuneDataService(val project: Project) {
  companion object {
    fun getInstance(project: Project): NepFineTuneDataService = project.service<NepFineTuneDataService>()
  }

  fun sendData(data: NepFineTuneData) {
    NepFineTuneDataListener.EP_NAME.extensionList.forEach { it.onData(project, data) }
  }
}

interface NepFineTuneDataListener {
  companion object {
    val EP_NAME = ExtensionPointName.create<NepFineTuneDataListener>("com.android.tools.idea.gemini.nepFineTuneDataListener")
  }

  fun onData(project: Project, data: NepFineTuneData)
}

data class NepFineTuneData(val caller: Any, val type: String, val values: Map<String, String>, val durationNanos: Long)
