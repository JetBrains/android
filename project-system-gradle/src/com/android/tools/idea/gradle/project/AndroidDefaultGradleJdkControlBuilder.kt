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
package com.android.tools.idea.gradle.project

import com.android.tools.idea.gradle.ui.GradleJdkPathEditComboBox
import com.android.tools.idea.gradle.ui.GradleJdkPathEditComboBoxBuilder
import com.android.tools.idea.sdk.GradleDefaultJdkPathStore
import com.android.tools.idea.sdk.IdeSdks
import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil
import com.intellij.openapi.externalSystem.util.PaintAwarePanel
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.ui.components.JBLabel
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.plugins.gradle.settings.GradleSettings

/**
 * An [AndroidDefaultGradleSystemSettingsControlBuilder] exposing settings allowing to configure
 * the default JDK used for gradle builds/sync process on new created projects.
 */
class AndroidDefaultGradleJdkControlBuilder(
  initialSettings: GradleSettings,
  disposable: Disposable
) : AndroidDefaultGradleSystemSettingsControlBuilder(initialSettings, disposable) {

  private lateinit var defaultGradleJdkComboBox: GradleJdkPathEditComboBox

  override fun fillUi(canvas: PaintAwarePanel, indentLevel: Int) {
    super.fillUi(canvas, indentLevel)
    addDefaultGradleJdk(canvas, indentLevel)
  }

  override fun apply(settings: GradleSettings) {
    validate(settings)
    GradleDefaultJdkPathStore.jdkPath = defaultGradleJdkComboBox.selectedJdkPath
    super.apply(settings)
  }

  override fun validate(settings: GradleSettings): Boolean {
    if (!ExternalSystemJdkUtil.isValidJdk(defaultGradleJdkComboBox.selectedJdkPath)) {
      throw ConfigurationException(
        AndroidBundle.message("gradle.settings.jdk.invalid.path.error", defaultGradleJdkComboBox.selectedJdkPath))
    }
    return super.validate(settings)
  }

  override fun isModified(): Boolean {
    if (defaultGradleJdkComboBox.isModified) return true
    return super.isModified()
  }

  private fun addDefaultGradleJdk(canvas: PaintAwarePanel, indentLevel: Int) {
    val defaultGradleJdkLabel = JBLabel(AndroidBundle.message("gradle.settings.jdk.default.component.label.text"))
    canvas.add(defaultGradleJdkLabel, ExternalSystemUiUtil.getLabelConstraints(indentLevel))

    defaultGradleJdkComboBox = createDefaultGradleJdkJdkComboBox()
    defaultGradleJdkLabel.labelFor = defaultGradleJdkComboBox
    canvas.add(defaultGradleJdkComboBox, ExternalSystemUiUtil.getFillLineConstraints(indentLevel))
  }

  private fun createDefaultGradleJdkJdkComboBox(): GradleJdkPathEditComboBox {
    return GradleJdkPathEditComboBoxBuilder.build(
      currentJdkPath = GradleDefaultJdkPathStore.jdkPath,
      embeddedJdkPath = IdeSdks.getInstance().embeddedJdkPath,
      suggestedJdks = ProjectJdkTable.getInstance().getSdksOfType(JavaSdk.getInstance()),
      hintMessage = AndroidBundle.message("gradle.settings.jdk.default.override.path.hint")
    )
  }
}


