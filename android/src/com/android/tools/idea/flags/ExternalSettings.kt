/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.flags

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.XmlSerializerUtil

/** An application level service that stores experimental settings outside the core module. */
@Service(Service.Level.APP)
@State(name = "ExternalSettings", storages = [(Storage("external.experimental.xml"))])
data class ExternalSettings(
  var enableDeviceStreaming: Boolean = StudioFlags.DIRECT_ACCESS.get()
) : PersistentStateComponent<ExternalSettings> {
  override fun getState(): ExternalSettings = this

  override fun loadState(state: ExternalSettings) {
    XmlSerializerUtil.copyBean(state, this)
  }

  companion object {
    @JvmStatic
    fun getInstance(): ExternalSettings = service()
  }
}
