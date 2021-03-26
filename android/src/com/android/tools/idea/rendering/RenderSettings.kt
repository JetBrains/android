/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.rendering

import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

@State(name = "RenderSettings", storages = [(Storage("render.experimental.xml"))])
data class RenderSettings(var quality: Float = 0.9f,
                          var useLiveRendering: Boolean = true,
                          var showDecorations: Boolean = false) : PersistentStateComponent<RenderSettings> {
  override fun getState(): RenderSettings = this

  override fun loadState(state: RenderSettings) {
    XmlSerializerUtil.copyBean(state, this)
  }

  companion object {
    @JvmStatic
    fun getProjectSettings(project: Project): RenderSettings {
      return project.getService(RenderSettings::class.java) ?: RenderSettings()
    }
  }
}
