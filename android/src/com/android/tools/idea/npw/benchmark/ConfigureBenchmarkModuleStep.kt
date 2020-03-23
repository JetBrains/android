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
package com.android.tools.idea.npw.benchmark

import com.android.tools.adtui.LabelWithEditButton
import com.android.tools.adtui.util.FormScalingUtil
import com.android.tools.adtui.validation.ValidatorPanel
import com.android.tools.idea.device.FormFactor.MOBILE
import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate.createDefaultTemplateAt
import com.android.tools.idea.npw.labelFor
import com.android.tools.idea.npw.model.NewProjectModel.Companion.getInitialDomain
import com.android.tools.idea.npw.module.AndroidApiLevelComboBox
import com.android.tools.idea.npw.platform.AndroidVersionsInfo
import com.android.tools.idea.npw.project.DomainToPackageExpression
import com.android.tools.idea.npw.template.components.LanguageComboProvider
import com.android.tools.idea.npw.validator.ApiVersionValidator
import com.android.tools.idea.npw.validator.ModuleValidator
import com.android.tools.idea.npw.validator.PackageNameValidator
import com.android.tools.idea.observable.BindingsManager
import com.android.tools.idea.observable.ListenerManager
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.StringValueProperty
import com.android.tools.idea.observable.ui.SelectedItemProperty
import com.android.tools.idea.observable.ui.TextProperty
import com.android.tools.idea.ui.wizard.StudioWizardStepPanel
import com.android.tools.idea.ui.wizard.WizardUtils
import com.android.tools.idea.wizard.model.SkippableWizardStep
import com.android.tools.idea.wizard.template.Language
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.layout.panel
import org.jetbrains.android.refactoring.isAndroidx
import org.jetbrains.android.util.AndroidBundle.message
import java.awt.Font
import java.util.function.Consumer
import javax.swing.JTextField

class ConfigureBenchmarkModuleStep(
  model: NewBenchmarkModuleModel, title: String, private val minSdkLevel: Int
) : SkippableWizardStep<NewBenchmarkModuleModel>(model, title) {
  private val androidVersionsInfo = AndroidVersionsInfo()
  private val bindings = BindingsManager()
  private val listeners = ListenerManager()

  private val screenTitle = JBLabel(message("android.wizard.module.config.title")).apply {
    font = Font(null, Font.BOLD, 18)
  }
  private val moduleName = JTextField()
  private val packageName = LabelWithEditButton()
  private val languageCombo = LanguageComboProvider().createComponent()
  private val apiLevelCombo = AndroidApiLevelComboBox()

  private val panel: DialogPanel = panel {
    row {
      screenTitle()
    }
    row {
      cell {
        labelFor("Module name:", moduleName, message("android.wizard.module.help.name"))
      }
      moduleName()
    }

    row {
      labelFor("Package name:", packageName)
      packageName()
    }

    row {
      labelFor("Language:", languageCombo)
      languageCombo()
    }

    row {
      labelFor("Minimum SDK:", apiLevelCombo)
      apiLevelCombo()
    }
  }

  private val validatorPanel = ValidatorPanel(this, StudioWizardStepPanel.wrappedWithVScroll(panel))

  init {
    val moduleNameText = TextProperty(moduleName)
    val packageNameText = TextProperty(packageName)
    val language = SelectedItemProperty<Language>(languageCombo)
    val isPackageNameSynced = BoolValueProperty(true)

    val moduleValidator = ModuleValidator(model.project)

    moduleName.text = WizardUtils.getUniqueName(model.moduleName.get(), moduleValidator)
    val computedPackageName = DomainToPackageExpression(StringValueProperty(getInitialDomain()), model.moduleName)

    validatorPanel.apply {
      registerValidator(moduleNameText, moduleValidator)
      registerValidator(model.packageName, PackageNameValidator())
      registerValidator(model.androidSdkInfo, ApiVersionValidator(model.project.isAndroidx(), MOBILE))
    }
    bindings.apply {
      bind(model.moduleName, moduleNameText, validatorPanel.hasErrors().not())
      bind(packageNameText, computedPackageName, isPackageNameSynced)
      bind(model.packageName, packageNameText)
      bindTwoWay(language, model.language)
      bind(model.androidSdkInfo, SelectedItemProperty(apiLevelCombo))
    }
    listeners.listen(packageNameText) { value: String -> isPackageNameSynced.set(value == computedPackageName.get()) }

    FormScalingUtil.scaleComponentTree(this.javaClass, validatorPanel)
  }

  override fun onEntering() {
    androidVersionsInfo.loadLocalVersions()
    apiLevelCombo.init(MOBILE, androidVersionsInfo.getKnownTargetVersions(MOBILE, minSdkLevel))
    androidVersionsInfo.loadRemoteTargetVersions(
      MOBILE, minSdkLevel, Consumer { items -> apiLevelCombo.init(MOBILE, items) }
    )
  }

  override fun onProceeding() {
    // Now that the module name was validated, update the model template
    model.template.set(createDefaultTemplateAt(model.project.basePath!!, model.moduleName.get()))
  }

  override fun canGoForward() = validatorPanel.hasErrors().not()

  override fun getComponent() = validatorPanel

  override fun getPreferredFocusComponent() = packageName

  override fun dispose() {
    bindings.releaseAll()
    listeners.releaseAll()
  }
}