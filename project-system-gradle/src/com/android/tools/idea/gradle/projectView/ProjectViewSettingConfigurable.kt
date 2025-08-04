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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.projectView.AndroidProjectViewSettings.Companion.PROJECT_VIEW_KEY
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.annotations.NonNls
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Temporary configurable for default project view setting.
 *
 * TODO(b/435683747): Remove this and move checkbox to advanced settings when visibility of
 * custom advanced settings is configurable.
 */
class ProjectViewSettingConfigurable() : SearchableConfigurable {
  private var component: JPanel? = null
  private val projectViewSettings = AndroidProjectViewSettings.getInstance()

  private var defaultProjectView = projectViewSettings.isProjectViewDefault()
  private var initialDefaultProjectView = defaultProjectView
  private var defaultProjectViewCheckBox: JBCheckBox? = null

  override fun getId(): @NonNls String {
    return AndroidBundle.message("configurable.ProjectViewConfigurable.id")
  }

  override fun getDisplayName(): @NlsContexts.ConfigurableName String? {
    return AndroidBundle.message("group.advanced.settings.project.view")
  }

  override fun createComponent(): JComponent? {
    if (component == null) {
      component = createPanel()
    }
    return component
  }

  private fun createPanel() = panel {
    if (StudioFlags.SHOW_DEFAULT_PROJECT_VIEW_SETTINGS.get()) {
      row {
        val projectViewProperty = java.lang.Boolean.getBoolean(PROJECT_VIEW_KEY)

        defaultProjectViewCheckBox = checkBox(AndroidBundle.message("advanced.setting.project.view.default"))
          .bindSelected(
            getter = { defaultProjectView },
            setter = { selected -> defaultProjectView = selected },
          )
          .onChanged { defaultProjectView = it.isSelected }
          .component
        defaultProjectViewCheckBox?.isSelected = defaultProjectView
        defaultProjectViewCheckBox?.isEnabled = !projectViewProperty
        defaultProjectViewCheckBox?.name = "defaultProjectView"
      }
    }
    else {
      defaultProjectViewCheckBox = null
    }
  }

  override fun isModified(): Boolean {
    return defaultProjectView != initialDefaultProjectView
  }

  override fun apply() {
    runWriteAction {
      if (defaultProjectViewCheckBox != null) {
        initialDefaultProjectView = defaultProjectView
        projectViewSettings.defaultToProjectView = defaultProjectView
      }
    }
  }

  override fun reset() {
    defaultProjectView = initialDefaultProjectView
    defaultProjectViewCheckBox?.isSelected = defaultProjectView
  }
}