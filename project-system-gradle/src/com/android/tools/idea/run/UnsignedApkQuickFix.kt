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
package com.android.tools.idea.run

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.GradleModelProvider
import com.android.tools.idea.gradle.dsl.api.android.SigningConfigModel
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.ConfigurationQuickFix
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLabel
import org.assertj.core.util.VisibleForTesting
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.util.function.Consumer
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Runnable to fix unsigned APK error.
 */
class UnsignedApkQuickFix @VisibleForTesting constructor(
  private val module: Module,
  private val selectedBuildTypeName: String,
  private val makeSigningConfigSelector: (GradleBuildModel) -> SigningConfigSelector) : ConfigurationQuickFix {
  /**
   * Instantiates a dialog as the quick fix action for unsigned APK error. When user closed the dialog with the OK button, the selected
   * signing config is picked.
   *
   * @param module an IDEA module
   * @param selectedBuildTypeName name of the currently selected build type, e.g. debug
   */
  constructor(module: Module, selectedBuildTypeName: String) : this(
    module, selectedBuildTypeName, { gradleBuildModel -> SigningConfigSelectorDialog(gradleBuildModel.android().signingConfigs()) }
  )

  override fun applyFix(dataContext: DataContext) {
    val gradleBuildModel = GradleModelProvider.getInstance().getProjectModel(module.project).getModuleBuildModel(module)
    if (gradleBuildModel != null) {
      val signingConfigSelector = makeSigningConfigSelector(gradleBuildModel)
      if (signingConfigSelector.showAndGet()) {
        gradleBuildModel.android().buildTypes().find { it.name() == selectedBuildTypeName }?.let { selectedBuildType ->
          selectedBuildType.signingConfig().setValue(ReferenceTo(signingConfigSelector.selectedConfig()))
          // Write signingConfig to Gradle.
          WriteCommandAction.runWriteCommandAction(module.project, "Select Signing Config", null, { gradleBuildModel.applyChanges() })
          // Trigger Gradle sync for the signingConfig to take effect.
          GradleSyncInvoker.getInstance().requestProjectSync(
            module.project, GradleSyncInvoker.Request(GradleSyncStats.Trigger.TRIGGER_QF_SIGNING_CONFIG_SELECTED), null)
        }
      }
    }
    else {
      throw IllegalStateException("Gradle build model should not be null for module: ${module.name}.")
    }
  }
}

interface SigningConfigSelector {
  /**
   * Shows the selector UI component and blocks on user selection
   *
   * @return true if the OK button is clicked
   */
  fun showAndGet(): Boolean

  /**
   * @return the selected signing config model
   */
  fun selectedConfig(): SigningConfigModel
}

/**
 * Dialog for selecting an existing signing config, useful for quick-fixing unsigned APK Run config error.
 */
class SigningConfigSelectorDialog(signingConfigs: Collection<SigningConfigModel>) : DialogWrapper(false), SigningConfigSelector {
  private val rootPanel = JPanel(BorderLayout())

  @VisibleForTesting
  val signingConfigComboBox = ComboBox<SigningConfigModel>()

  init {
    title = "Select Signing Config"
    signingConfigs.forEach(Consumer { item: SigningConfigModel -> signingConfigComboBox.addItem(item) })
    signingConfigComboBox.renderer = SimpleListCellRenderer.create("<unnamed>", SigningConfigModel::name)
    init()
  }

  override fun selectedConfig(): SigningConfigModel = signingConfigComboBox.item

  override fun createCenterPanel(): JComponent {
    return rootPanel.apply {
      add(JPanel(FlowLayout()).apply {
        add(JBLabel("Debug keys should be strictly used for development purposes only."))
        add(HyperlinkLabel("Learn more").apply {
          setHyperlinkTarget(DOC_URL)
        })
      }, BorderLayout.NORTH)
      add(signingConfigComboBox, BorderLayout.CENTER)
    }
  }

  companion object {
    const val DOC_URL = "https://developer.android.com/studio/publish/app-signing#debug-mode"
  }
}