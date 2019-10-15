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

import com.android.sdklib.AndroidVersion.VersionCodes
import com.android.tools.adtui.LabelWithEditButton
import com.android.tools.adtui.util.FormScalingUtil
import com.android.tools.adtui.validation.Validator
import com.android.tools.adtui.validation.Validator.Result
import com.android.tools.adtui.validation.Validator.Severity
import com.android.tools.adtui.validation.ValidatorPanel
import com.android.tools.idea.npw.FormFactor.MOBILE
import com.android.tools.idea.npw.model.NewProjectModel.Companion.getInitialDomain
import com.android.tools.idea.npw.module.AndroidApiLevelComboBox
import com.android.tools.idea.npw.platform.AndroidVersionsInfo
import com.android.tools.idea.npw.platform.AndroidVersionsInfo.VersionItem
import com.android.tools.idea.npw.platform.Language
import com.android.tools.idea.npw.project.DomainToPackageExpression
import com.android.tools.idea.npw.template.components.LanguageComboProvider
import com.android.tools.idea.npw.validator.ModuleValidator
import com.android.tools.idea.observable.BindingsManager
import com.android.tools.idea.observable.ListenerManager
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.StringValueProperty
import com.android.tools.idea.observable.ui.SelectedItemProperty
import com.android.tools.idea.observable.ui.TextProperty
import com.android.tools.idea.ui.wizard.StudioWizardStepPanel
import com.android.tools.idea.ui.wizard.WizardUtils
import com.android.tools.idea.wizard.model.SkippableWizardStep
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.components.JBLabel
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridConstraints.ANCHOR_NORTH
import com.intellij.uiDesigner.core.GridConstraints.ANCHOR_NORTHWEST
import com.intellij.uiDesigner.core.GridLayoutManager
import org.jetbrains.android.refactoring.isAndroidx
import org.jetbrains.android.util.AndroidBundle.message
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.util.Optional
import java.util.function.Consumer
import javax.swing.JPanel
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
  private val moduleNameLabel = JBLabel("Module name:").apply {
    labelFor = moduleName
  }
  private val moduleNameContextHelp = ContextHelpLabel.create(message("android.wizard.module.help.name"))
  private val moduleNameLabelWithHelp = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
    add(moduleNameLabel)
    add(moduleNameContextHelp)
  }
  private val packageName = LabelWithEditButton()
  private val packageNameLabel = JBLabel("Package name:").apply {
    labelFor = packageName
  }
  private val languageComboBox = LanguageComboProvider().createComponent()
  private val languageComboBoxLabel = JBLabel("Language:").apply {
    labelFor = languageComboBox
  }
  private val apiLevelComboBox = AndroidApiLevelComboBox()
  private val apiLevelComboBoxLabel = JBLabel("Minimum SDK:").apply {
    labelFor = apiLevelComboBox
  }

  // TODO(qumeric): replace with TabularLayout
  private val panel = JPanel(GridLayoutManager(6, 3)).apply {
    val anySize = Dimension(-1, -1)
    val lcSize = Dimension(150, -1)
    add(screenTitle, GridConstraints(0, 0, 1, 3, ANCHOR_NORTH, 0, 0, 0, anySize, anySize, anySize))

    add(moduleNameLabelWithHelp, GridConstraints(1, 0, 1, 1, ANCHOR_NORTHWEST, 0, 0, 0, lcSize, lcSize, anySize))
    add(packageNameLabel, GridConstraints(2, 0, 1, 1, ANCHOR_NORTHWEST, 0, 0, 0, lcSize, lcSize, anySize))
    add(languageComboBoxLabel, GridConstraints(3, 0, 1, 1, ANCHOR_NORTHWEST, 0, 0, 0, lcSize, lcSize, anySize))
    add(apiLevelComboBoxLabel, GridConstraints(4, 0, 1, 1, ANCHOR_NORTHWEST, 0, 0, 0, lcSize, lcSize, anySize))
    add(moduleName, GridConstraints(1, 1, 1, 2, ANCHOR_NORTH, 1, 6, 0, anySize, anySize, anySize))
    add(packageName, GridConstraints(2, 1, 1, 2, ANCHOR_NORTH, 1, 6, 0, anySize, anySize, anySize))
    add(languageComboBox, GridConstraints(3, 1, 1, 2, ANCHOR_NORTH, 1, 6, 0, anySize, anySize, anySize))
    add(apiLevelComboBox, GridConstraints(4, 1, 1, 2, ANCHOR_NORTH, 1, 6, 0, anySize, anySize, anySize))
  }

  private val validatorPanel = ValidatorPanel(this, panel)
  private val rootPanel = StudioWizardStepPanel(validatorPanel)

  init {
    val moduleNameText = TextProperty(moduleName)
    val packageNameText = TextProperty(packageName)
    val language = SelectedItemProperty<Language>(languageComboBox)
    val isPackageNameSynced = BoolValueProperty(true)

    val moduleValidator = ModuleValidator(model.project)
    val packageNameValidator = object: Validator<String> {
      override fun validate(value: String) = Result.fromNullableMessage(WizardUtils.validatePackageName(value))
    }
    val minSdkValidator = object: Validator<Optional<VersionItem>> {
      override fun validate(value: Optional<VersionItem>): Result = when {
        !value.isPresent -> Result(Severity.ERROR, message("select.target.dialog.text"))
        value.get().targetApiLevel >= VersionCodes.Q && !model.project.isAndroidx() ->
          Result(Severity.ERROR, message("android.wizard.validate.module.needs.androidx"))
        else -> Result.OK
      }
    }

    moduleName.text = WizardUtils.getUniqueName(model.moduleName.get(), moduleValidator)
    val computedPackageName = DomainToPackageExpression(StringValueProperty(getInitialDomain()), model.moduleName)

    validatorPanel.apply {
      registerValidator(moduleNameText, moduleValidator)
      registerValidator(model.packageName, packageNameValidator)
      registerValidator(model.minSdk, minSdkValidator)
    }
    bindings.apply {
      bind(model.moduleName, moduleNameText, validatorPanel.hasErrors().not())
      bind(packageNameText, computedPackageName, isPackageNameSynced)
      bind(model.packageName, packageNameText)
      bindTwoWay(language, model.language)
      bind(model.minSdk, SelectedItemProperty(apiLevelComboBox))
    }
    listeners.listen(packageNameText) { value: String -> isPackageNameSynced.set(value == computedPackageName.get()) }

    FormScalingUtil.scaleComponentTree(this.javaClass, rootPanel)
  }

  override fun onEntering() {
    androidVersionsInfo.loadLocalVersions()
    apiLevelComboBox.init(MOBILE, androidVersionsInfo.getKnownTargetVersions(MOBILE, minSdkLevel))
    androidVersionsInfo.loadRemoteTargetVersions(
      MOBILE, minSdkLevel, Consumer { items -> apiLevelComboBox.init(MOBILE, items) }
    )
  }

  override fun canGoForward() = validatorPanel.hasErrors().not()

  override fun getComponent() = rootPanel

  override fun getPreferredFocusComponent() = packageName

  override fun dispose() {
    bindings.releaseAll()
    listeners.releaseAll()
  }
}