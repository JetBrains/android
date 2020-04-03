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
package com.android.tools.idea.emulator

import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Persistent Emulator-related settings.
 */
@State(name = "Emulator", storages = [Storage("emulator.xml")])
class EmulatorSettings : PersistentStateComponent<EmulatorSettings> {

  var launchEmbedded = false
    get() = field && StudioFlags.EMBEDDED_EMULATOR_ENABLED.get()

  override fun getState(): EmulatorSettings {
    return this
  }

  override fun loadState(state: EmulatorSettings) {
    XmlSerializerUtil.copyBean(state, this)
  }

  companion object {
    @JvmStatic
    fun getInstance(): EmulatorSettings {
      return ServiceManager.getService(EmulatorSettings::class.java)
    }
  }
}