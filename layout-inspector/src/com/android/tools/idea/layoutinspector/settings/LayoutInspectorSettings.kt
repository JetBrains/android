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

import com.android.tools.idea.layoutinspector.common.ephemeralFlow
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import kotlinx.coroutines.flow.asSharedFlow

@State(name = "LayoutInspectorSettings", storages = [Storage("layoutInspectorSettings.xml")])
class LayoutInspectorSettings : PersistentStateComponent<LayoutInspectorSettings> {

  companion object {
    @JvmStatic
    fun getInstance(): LayoutInspectorSettings {
      return ApplicationManager.getApplication().getService(LayoutInspectorSettings::class.java)
    }
  }

  // Property needs to have public setters and getters in order to be persisted.
  var autoConnectEnabled = true

  /**
   * [embeddedLayoutInspectorEnabled] needs to be the source of truth. The platform needs a property in order to be able to serialize it. So
   * we need both the property and this flow to observe changes.
   */
  private val _embeddedLayoutInspectorChanges = ephemeralFlow<Boolean>()
  /** Used to observe future changes in [embeddedLayoutInspectorEnabled] */
  val embeddedLayoutInspectorChanges = _embeddedLayoutInspectorChanges.asSharedFlow()

  // Property needs to have public setters and getters in order to be persisted.
  var embeddedLayoutInspectorEnabled: Boolean = true
    set(value) {
      field = value
      _embeddedLayoutInspectorChanges.tryEmit(value)
    }

  override fun getState() = this

  override fun loadState(state: LayoutInspectorSettings) = XmlSerializerUtil.copyBean(state, this)
}
