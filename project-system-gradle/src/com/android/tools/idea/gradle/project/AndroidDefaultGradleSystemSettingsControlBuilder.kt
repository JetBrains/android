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
package com.android.tools.idea.gradle.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil
import com.intellij.openapi.externalSystem.util.PaintAwarePanel
import com.intellij.openapi.util.Disposer
import com.intellij.ui.TitledSeparator
import org.jetbrains.plugins.gradle.service.settings.GradleConfigurable
import org.jetbrains.plugins.gradle.service.settings.IdeaGradleSystemSettingsControlBuilder
import org.jetbrains.plugins.gradle.settings.GradleSettings

/**
 * Abstract class overriding the [IdeaGradleSystemSettingsControlBuilder] implementation to allow studio
 * to have a customized gradle project settings using specified configuration as a default for new created projects.
 */
abstract class AndroidDefaultGradleSystemSettingsControlBuilder(
  initialSettings: GradleSettings,
  private val disposable: Disposable
) : IdeaGradleSystemSettingsControlBuilder(initialSettings) {

  override fun fillUi(canvas: PaintAwarePanel, indentLevel: Int) {
    super.fillUi(canvas, indentLevel)
    addTitleSeparator(canvas)
  }

  override fun disposeUIResources() {
    super.disposeUIResources()
    Disposer.dispose(disposable)
  }

  private fun addTitleSeparator(canvas: PaintAwarePanel) {
    val titleConstraints = ExternalSystemUiUtil.getFillLineConstraints(0)
    val titleSeparator = TitledSeparator(ExternalSystemBundle.message("settings.title.projects.settings", GradleConfigurable.DISPLAY_NAME))
    canvas.add(titleSeparator, titleConstraints)
  }
}
