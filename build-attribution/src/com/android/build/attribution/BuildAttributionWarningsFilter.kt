/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.build.attribution

import com.android.build.attribution.data.SuppressedWarnings
import com.android.build.attribution.data.TaskData
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.project.Project

@State(name = "BuildAttributionWarningsFilter")
class BuildAttributionWarningsFilter : PersistentStateComponent<SuppressedWarnings> {
  private var suppressedWarnings: SuppressedWarnings = SuppressedWarnings()

  companion object {
    @JvmStatic
    fun getInstance(project: Project): BuildAttributionWarningsFilter {
      return project.getService(BuildAttributionWarningsFilter::class.java)
    }
  }

  var suppressNoGCSettingWarning: Boolean
    get() = suppressedWarnings.noGCSettingWarning
    set(value) { suppressedWarnings.noGCSettingWarning = value }

  override fun getState(): SuppressedWarnings? {
    return suppressedWarnings
  }

  override fun loadState(state: SuppressedWarnings) {
    suppressedWarnings = state
  }
}
