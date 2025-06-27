/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.projectView

import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.projectView.AndroidProjectViewSettings.Companion.PROJECT_VIEW_KEY
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.ProjectViewDefaultViewEvent
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.annotations.OptionTag

@Service(Service.Level.APP)
@com.intellij.openapi.components.State(
  name = "AndroidProjectViewSettings",
  storages = [Storage("androidProjectWindow.xml", roamingType = RoamingType.LOCAL)],
)
class AndroidProjectViewSettingsImpl: AndroidProjectViewSettings, PersistentStateComponent<ProjectViewSettingsState> {
  private var state = ProjectViewSettingsState()

  override var defaultToProjectView: Boolean
    get() = state.defaultToProjectView
    set(newValue) {
      if (state.defaultToProjectView != newValue) {
        state.defaultToProjectView = newValue
        trackDefaultViewSetting(newValue)
      }
    }

  override fun getState(): ProjectViewSettingsState = state

  override fun loadState(newState: ProjectViewSettingsState) {
    state = newState
  }

  override fun initializeComponent() {
    defaultToProjectView = state.defaultToProjectView
    super.initializeComponent()
  }

  // Used by AdvancedSettingsImpl to configure the visibility of this setting
  fun isDefaultToProjectViewVisible(): Boolean {
    return StudioFlags.SHOW_DEFAULT_PROJECT_VIEW_SETTINGS.get()
  }

  // Used by AdvancedSettingsImpl to configure enabling this setting
  fun isDefaultToProjectViewEnabled(): Boolean {
    // This setting is disabled when "studio.projectview=true" custom property is set
    return StudioFlags.SHOW_DEFAULT_PROJECT_VIEW_SETTINGS.get() && !java.lang.Boolean.getBoolean(PROJECT_VIEW_KEY)
  }

  private fun trackDefaultViewSetting(setting: Boolean) {
    val defaultView = if (setting) ProjectViewDefaultViewEvent.DefaultView.PROJECT_VIEW else ProjectViewDefaultViewEvent.DefaultView.ANDROID_VIEW
    UsageTracker.log(
      AndroidStudioEvent.newBuilder().setKind(AndroidStudioEvent.EventKind.PROJECT_VIEW_DEFAULT_VIEW_EVENT).setProjectViewDefaultViewEvent(
        ProjectViewDefaultViewEvent.newBuilder().setDefaultView(defaultView))
    )
  }

  companion object {
    fun getInstance() = service<AndroidProjectViewSettings>()
  }
}

class ProjectViewSettingsState : BaseState() {
  @get:OptionTag("default_to_project_view")
  var defaultToProjectView by property(false)
}