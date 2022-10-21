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
package com.android.build.attribution

import com.android.build.attribution.ui.view.ClearBuildResultsAction
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.text.Formats
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel

@State(name = "BuildAnalyzerSettings")
class BuildAnalyzerSettings : PersistentStateComponent<BuildAnalyzerSettings.State> {
  var settingsState: State = State()

  companion object {
    @JvmStatic
    fun getInstance(project: Project): BuildAnalyzerSettings {
      return project.getService(BuildAnalyzerSettings::class.java)
    }
  }

  data class State(
    var notifyAboutWarnings: Boolean = true,
    var maxNumberOfBuildsStored: Int = 15,
    var maxStorageSpaceKilobytes: Int = 1000,
  )

  override fun getState(): State = settingsState

  override fun loadState(state: State) {
    settingsState = state
  }
}

class BuildAnalyzerConfigurableProvider(val project: Project) : ConfigurableProvider() {
  override fun createConfigurable(): Configurable {
    return BuildAnalyzerConfigurable(project)
  }

  companion object {
    const val DISPLAY_NAME = "Build Analyzer"
  }
}

private class BuildAnalyzerConfigurable(val project: Project) : BoundSearchableConfigurable(
  displayName = BuildAnalyzerConfigurableProvider.DISPLAY_NAME,
  helpTopic = "build.analyzer"
) {
  private val buildAnalyzerSettings = BuildAnalyzerSettings.getInstance(project)

  override fun apply() {
    super.apply()
    BuildAnalyzerStorageManager.getInstance(project).onSettingsChange()
  }

  override fun createPanel(): DialogPanel = panel {
    if (StudioFlags.BUILD_ANALYZER_HISTORY.get()) {
      row {
        text("Number of build results stored: ${BuildAnalyzerStorageManager.getInstance(project).getNumberOfBuildFilesStored()}")
        text("File size taken up by stored build results: ${
          Formats.formatFileSize(BuildAnalyzerStorageManager.getInstance(project).getCurrentBuildHistoryDataSize())
        }")
      }

      row {
        text("Maximum number of build results stored")
        intTextField(IntRange(0, 100)).bindIntText(
          getter = { buildAnalyzerSettings.settingsState.maxNumberOfBuildsStored },
          setter = { buildAnalyzerSettings.settingsState.maxNumberOfBuildsStored = it }
        )
      }

      row {
        text("Maximum storage capacity in kilobytes")
        intTextField(IntRange(0, 100000)).bindIntText(
          getter = { buildAnalyzerSettings.settingsState.maxStorageSpaceKilobytes },
          setter = { buildAnalyzerSettings.settingsState.maxStorageSpaceKilobytes = it }
        )
      }

      row {
        button("Clear Build Results Data", ClearBuildResultsAction())
      }
    }

    row {
      checkBox("Notify about new warning types").bindSelected(
        getter = { buildAnalyzerSettings.settingsState.notifyAboutWarnings },
        setter = { buildAnalyzerSettings.settingsState.notifyAboutWarnings = it }
      )
    }
  }
}