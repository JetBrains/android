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
package com.android.tools.idea.npw.dynamicapp

import com.android.AndroidProjectTypes
import com.android.sdklib.SdkVersionInfo
import com.android.tools.adtui.device.FormFactor
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.npw.contextLabel
import com.android.tools.idea.npw.module.ConfigureModuleStep
import com.android.tools.idea.npw.module.generateBuildConfigurationLanguageRow
import com.android.tools.idea.npw.template.components.ModuleComboProvider
import com.android.tools.idea.npw.validator.ModuleSelectedValidator
import com.android.tools.idea.observable.core.OptionalProperty
import com.android.tools.idea.observable.ui.SelectedItemProperty
import com.android.tools.idea.observable.ui.SelectedProperty
import com.android.tools.idea.observable.ui.TextProperty
import com.android.tools.idea.project.AndroidProjectInfo
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.intellij.openapi.module.Module
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.JBColor
import com.intellij.ui.RoundedLineBorder
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.Borders.empty
import icons.StudioIcons.Common.WARNING
import java.awt.Component
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import org.jetbrains.android.util.AndroidBundle

class ConfigureDynamicModuleStep(model: DynamicFeatureModel, basePackage: String) :
  ConfigureModuleStep<DynamicFeatureModel>(
    model,
    FormFactor.MOBILE,
    SdkVersionInfo.LOWEST_ACTIVE_API,
    basePackage,
    AndroidBundle.message("android.wizard.module.config.title"),
  ) {
  private val baseApplication: JComboBox<Module> = ModuleComboProvider().createComponent()

  // TODO(qumeric): unify with ConfigureModuleDownloadOptionsStep?
  private val moduleTitle: JTextField = JBTextField()
  private val fusingCheckbox: JCheckBox =
    JBCheckBox("Fusing (install module on pre-Lollipop devices)")

  override fun createMainPanel(): DialogPanel =
    panel {
        if (model.isInstant) {
          row { cell(createDeprecationWarningPanel()).align(AlignX.FILL) }
            .bottomGap(BottomGap.MEDIUM)
        }

        row("Base Application Module") { cell(baseApplication).align(AlignX.FILL) }

        row(contextLabel("Module name", AndroidBundle.message("android.wizard.module.help.name"))) {
          cell(moduleName).align(AlignX.FILL)
        }

        row("Package name") { cell(packageName).align(AlignX.FILL) }

        row("Language") { cell(languageCombo).align(AlignX.FILL) }

        row("Minimum SDK") { cell(apiLevelCombo).align(AlignX.FILL) }

        if (model.isInstant) {
          row(contextLabel("Module title", "This may be visible to users")) {
            cell(moduleTitle).align(AlignX.FILL)
          }
        }

        if (StudioFlags.NPW_SHOW_KTS_GRADLE_COMBO_BOX.get()) {
          generateBuildConfigurationLanguageRow(buildConfigurationLanguageCombo)
        }

        row { cell(fusingCheckbox) }.topGap(TopGap.SMALL)
      }
      .withBorder(empty(6))

  init {
    AndroidProjectInfo.getInstance(model.project)
      .getAllModulesOfProjectType(AndroidProjectTypes.PROJECT_TYPE_APP)
      .forEach { module: Module -> baseApplication.addItem(module) }
    val baseApplication: OptionalProperty<Module> = model.baseApplication
    bindings.bind(baseApplication, SelectedItemProperty(this.baseApplication))

    validatorPanel.registerValidator(baseApplication, ModuleSelectedValidator())

    if (model.isInstant) {
      bindings.bind(model.featureFusing, SelectedProperty(fusingCheckbox))
      bindings.bindTwoWay(TextProperty(moduleTitle), getModel().featureTitle)
    } else {
      fusingCheckbox.isVisible = false
    }
  }

  override fun createDependentSteps(): Collection<ModelWizardStep<*>> =
    listOf(ConfigureModuleDownloadOptionsStep(model)) + super.createDependentSteps()

  override fun getPreferredFocusComponent(): JComponent? = moduleName

  private fun createDeprecationWarningPanel(): JPanel {
    return JPanel().apply {
      layout = BoxLayout(this, BoxLayout.X_AXIS)

      val darkBackground = 0x3D3223
      val darkBorder = 0xD6AE58
      val lightBackground = 0xFFFAEB
      val lightBorder = 0xC27D04

      background = JBColor(lightBackground, darkBackground)
      border =
        JBUI.Borders.compound(
          RoundedLineBorder(JBColor(lightBorder, darkBorder), JBUIScale.scale(8), 1),
          empty(JBUIScale.scale(8)),
        )

      val iconLabel = JLabel(WARNING).apply { alignmentY = Component.TOP_ALIGNMENT }
      add(iconLabel)

      add(Box.createHorizontalStrut(JBUIScale.scale(8)))

      val warningTextLabel =
        JLabel(
            "<html>Instant Apps support will be removed by Google Play in December 2025. " +
              "Publishing and all Google Play Instant APIs will no longer work. " +
              "Tooling support will be removed in Android Studio Otter Feature Drop.</html>"
          )
          .apply { alignmentY = Component.TOP_ALIGNMENT }
      add(warningTextLabel)
    }
  }
}
