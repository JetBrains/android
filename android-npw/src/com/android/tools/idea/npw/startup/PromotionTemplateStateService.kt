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
package com.android.tools.idea.npw.startup

import com.android.tools.idea.wizard.template.FormFactor
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

data class PromotedTemplate(val name: String, val formFactor: FormFactor, val pluginId: String)

@Service
@State(name = "OpenAndroidProjectWizardStartupState", storages = [Storage("android.npw.startup.xml", roamingType = RoamingType.DISABLED)])
internal class PromotionTemplateStateService : SimplePersistentStateComponent<PromotionTemplateStateService.State>(State()) {

  @Synchronized
  fun requestNpwReopenOnNextStartup(promotedPluginId: String, promotedTemplateName: String, promotedFormFactor: FormFactor) {
    state.promotedFormFactor = promotedFormFactor
    state.openAndroidNpwOnStartup = true
    state.promotedPluginId = promotedPluginId
    state.promotedTemplateName = promotedTemplateName
  }

  @Synchronized
  fun markRestartedIfNpwReopenPending() {
    if (state.openAndroidNpwOnStartup) {
      state.wasRestarted = true
    }
  }

  @Synchronized
  fun consumeNpwReopenRequestAfterRestart(): Boolean {
    if (state.openAndroidNpwOnStartup && state.wasRestarted) {
      state.openAndroidNpwOnStartup = false
      state.wasRestarted = false
      return true
    }
    return false
  }

  @Synchronized
  fun consumePromotedTemplate(): PromotedTemplate? {
    val pluginId = state.promotedPluginId
    val templateName = state.promotedTemplateName
    val formFactor = state.promotedFormFactor

    state.promotedPluginId = null
    state.promotedTemplateName = null
    state.promotedFormFactor = null

    return if (pluginId != null && templateName != null && formFactor != null) {
      PromotedTemplate(name = templateName, formFactor = formFactor, pluginId = pluginId)
    } else {
      null
    }
  }

  class State : BaseState() {
    var openAndroidNpwOnStartup: Boolean by property(false)
    var wasRestarted: Boolean by property(false)
    var promotedPluginId: String? by string()
    var promotedTemplateName: String? by string()
    var promotedFormFactor: FormFactor? by enum<FormFactor>()
  }

  companion object {
    fun getInstance(): PromotionTemplateStateService = service()
  }
}
