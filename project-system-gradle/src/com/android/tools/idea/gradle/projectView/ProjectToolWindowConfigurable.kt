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
import com.android.tools.idea.gradle.projectView.ProjectToolWindowSettings.Companion.PROJECT_VIEW_KEY
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

class ProjectToolWindowConfigurable() : SearchableConfigurable {
  private var component: JPanel? = null
  private val projectToolSettings = ProjectToolWindowSettings.getInstance()

  private var defaultProjectView = projectToolSettings.isProjectViewDefault()
  private var initialDefaultProjectView = defaultProjectView
  private var defaultProjectViewCheckBox: JBCheckBox? = null

  private var showBuildFilesInModule = projectToolSettings.showBuildFilesInModule
  private var initialShowBuildFilesInModule = showBuildFilesInModule
  private var showBuildFilesCheckBox: JBCheckBox? = null

  override fun getId(): @NonNls String {
    return AndroidBundle.message("configurable.ProjectToolWindowConfigurable.id")
  }

  override fun getDisplayName(): @NlsContexts.ConfigurableName String? {
    return AndroidBundle.message("configurable.ProjectToolWindowConfigurable.displayName")
  }

  override fun createComponent(): JComponent? {
    defaultProjectView = projectToolSettings.isProjectViewDefault()
    initialDefaultProjectView = defaultProjectView
    if (component == null) {
      component = createPanel()
    }
    return component
  }

  private fun createPanel() = panel {
    if (StudioFlags.SHOW_BUILD_FILES_IN_MODULE_SETTINGS.get()) {
      row {
        showBuildFilesCheckBox = checkBox("[Android view] Display build files in module")
          .bindSelected(
            getter = { showBuildFilesInModule },
            setter = { selected -> showBuildFilesInModule = selected },
          )
          .onChanged { showBuildFilesInModule = it.isSelected }
          .component
        showBuildFilesCheckBox?.isSelected = showBuildFilesInModule
        showBuildFilesCheckBox?.isEnabled = true
      }
    }
    else {
      showBuildFilesCheckBox = null
    }

    if (StudioFlags.SHOW_DEFAULT_PROJECT_VIEW_SETTINGS.get()) {
      row {
        val projectViewProperty = java.lang.Boolean.getBoolean(PROJECT_VIEW_KEY)

        val propertyText = if (projectViewProperty) " (set via custom property)" else ""
        defaultProjectViewCheckBox = checkBox("Set Project view as the default$propertyText")
          .bindSelected(
            getter = { defaultProjectView },
            setter = { selected -> defaultProjectView = selected },
          )
          .onChanged { defaultProjectView = it.isSelected }
          .component
        defaultProjectViewCheckBox?.isSelected = defaultProjectView
        defaultProjectViewCheckBox?.isEnabled = !projectViewProperty
      }
    }
    else {
      defaultProjectViewCheckBox = null
    }
  }

  override fun isModified(): Boolean {
    return defaultProjectView != initialDefaultProjectView || showBuildFilesInModule != initialShowBuildFilesInModule
  }

  override fun apply() {
    runWriteAction {
      if (defaultProjectViewCheckBox != null) {
        initialDefaultProjectView = defaultProjectView
        projectToolSettings.defaultToProjectView = defaultProjectView
      }

      if (showBuildFilesCheckBox != null) {
        initialShowBuildFilesInModule = showBuildFilesInModule
        projectToolSettings.showBuildFilesInModule = showBuildFilesInModule
      }
    }
  }

  override fun reset() {
    defaultProjectView = initialDefaultProjectView
    defaultProjectViewCheckBox?.isSelected = defaultProjectView

    showBuildFilesInModule = initialShowBuildFilesInModule
    showBuildFilesCheckBox?.isSelected = showBuildFilesInModule
  }
}