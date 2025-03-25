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
package com.android.tools.idea.insights.persistence

import com.android.tools.idea.insights.AppInsightsState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.PROJECT)
@State(name = "AppInsightsSettings", storages = [Storage("appInsightsSettings.xml")])
class AppInsightsSettings : PersistentStateComponent<AppInsightsSettings> {
  var selectedTabId: String? = null
  var tabSettings: MutableMap<String, InsightsFilterSettings> = mutableMapOf()
  var isOfflineNotificationDismissed: Boolean = false

  override fun getState() = this

  override fun loadState(state: AppInsightsSettings) {
    XmlSerializerUtil.copyBean(state, this)
  }

  fun setTabSetting(tabId: String, state: AppInsightsState) {
    tabSettings[tabId] = state.toFilterSettings()
  }
}
