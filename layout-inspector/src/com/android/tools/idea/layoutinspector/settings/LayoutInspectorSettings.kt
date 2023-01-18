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
package com.android.tools.idea.layoutinspector.settings

import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(name = "LayoutInspectorSettings", storages = [Storage("layoutInspectorSettings.xml")])
class LayoutInspectorSettings : PersistentStateComponent<LayoutInspectorSettings> {

  companion object {
    @JvmStatic
    fun getInstance(): LayoutInspectorSettings {
      return ApplicationManager.getApplication().getService(LayoutInspectorSettings::class.java)
    }
  }

  // TODO unify these two variables and set method once the StudioFlag is deleted.
  //  currently they have to be separate because we want clients to always use `autoConnectEnabled`
  //  but need `LayoutInspectorConfigurableProvider` and tests to set the value of `isAutoConnectEnabledInSettings`
  private var isAutoConnectEnabledInSettings = true

  val autoConnectEnabled: Boolean get() = StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_AUTO_CONNECT_TO_FOREGROUND_PROCESS_ENABLED.get() &&
                                          isAutoConnectEnabledInSettings

  fun setAutoConnectEnabledInSettings(isEnabled: Boolean) {
    isAutoConnectEnabledInSettings = isEnabled
  }

  override fun getState() = this

  override fun loadState(state: LayoutInspectorSettings) = XmlSerializerUtil.copyBean(state, this)
}