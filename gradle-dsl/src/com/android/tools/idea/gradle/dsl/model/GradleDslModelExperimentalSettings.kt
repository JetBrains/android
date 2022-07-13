/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(name = "GradleDslModelExperimentalSettings", storages = [(Storage("gradle.dsl.model.xml"))])
data class GradleDslModelExperimentalSettings(
  var isVersionCatalogEnabled: Boolean = true
) : PersistentStateComponent<GradleDslModelExperimentalSettings> {
  override fun getState(): GradleDslModelExperimentalSettings = this

  override fun loadState(state: GradleDslModelExperimentalSettings) {
    XmlSerializerUtil.copyBean(state, this)
  }

  companion object {
    @JvmStatic
    fun getInstance(): GradleDslModelExperimentalSettings =
      ApplicationManager.getApplication().getService(GradleDslModelExperimentalSettings::class.java)
  }
}