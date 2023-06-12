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
package com.android.tools.idea.gradle.structure.configurables

import com.android.tools.idea.gradle.structure.configurables.ui.PropertiesUiModel
import com.android.tools.idea.gradle.structure.configurables.ui.project.ProjectPropertiesConfigPanel
import com.android.tools.idea.gradle.structure.configurables.ui.simplePropertyEditor
import com.android.tools.idea.gradle.structure.configurables.ui.androidGradlePluginVersionViewer
import com.android.tools.idea.gradle.structure.configurables.ui.uiProperty
import com.android.tools.idea.gradle.structure.model.PsProjectDescriptors
import com.android.tools.idea.structure.dialog.TrackedConfigurable
import com.google.wireless.android.sdk.stats.PSDEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.options.BaseConfigurable
import com.intellij.openapi.util.Disposer
import javax.swing.JComponent

const val PROJECT_VIEW = "ProjectView"

/**
 * Configurable defining the Project panel in the Project Structure Dialog.
 */
class ProjectPerspectiveConfigurable(private val context: PsContext)
  : BaseConfigurable(), TrackedConfigurable, Disposable {
  private var uiDisposed = true
  override fun getDisplayName(): String = "Project"
  override val leftConfigurable = PSDEvent.PSDLeftConfigurable.PROJECT_STRUCTURE_DIALOG_LEFT_CONFIGURABLE_PROJECT

  override fun createComponent(): JComponent? =
    ProjectPropertiesConfigPanel(context.project, context)
      .also { Disposer.register(this, it) }
      .getComponent()
      .also {
        it.name = PROJECT_VIEW
      }

  override fun apply() = context.applyChanges()

  override fun isModified(): Boolean = context.project.isModified

  override fun reset() {
    super.reset()
    uiDisposed = false
  }

  override fun disposeUIResources() {
    if (uiDisposed) return
    super.disposeUIResources()
    uiDisposed = true
    Disposer.dispose(this)
  }

  override fun dispose() = Unit
}

fun projectPropertiesModel() =
  PropertiesUiModel(
    listOf(
      // TODO(b/123013466): [New PSD] Analytics for new PSD missing fields.
      uiProperty(PsProjectDescriptors.androidGradlePluginVersion, ::androidGradlePluginVersionViewer, null),
      // TODO(b/123013466): [New PSD] Analytics for new PSD missing fields.
      uiProperty(PsProjectDescriptors.gradleVersion, ::simplePropertyEditor, null)))

