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
import com.android.tools.adtui.LabelWithEditButton
import com.android.tools.adtui.util.FormScalingUtil
import com.android.tools.adtui.validation.Validator
import com.android.tools.adtui.validation.ValidatorPanel
import com.android.tools.idea.device.FormFactor
import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.npw.labelFor
import com.android.tools.idea.npw.model.NewProjectModel.Companion.nameToJavaPackage
import com.android.tools.idea.npw.module.AndroidApiLevelComboBox
import com.android.tools.idea.npw.module.AppNameToModuleNameExpression
import com.android.tools.idea.npw.platform.AndroidVersionsInfo
import com.android.tools.idea.npw.template.components.LanguageComboProvider
import com.android.tools.idea.npw.validator.ApiVersionValidator
import com.android.tools.idea.npw.validator.ModuleValidator
import com.android.tools.idea.npw.validator.PackageNameValidator
import com.android.tools.idea.observable.BindingsManager
import com.android.tools.idea.observable.ListenerManager
import com.android.tools.idea.observable.core.BoolProperty
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.ObservableBool
import com.android.tools.idea.observable.core.OptionalProperty
import com.android.tools.idea.observable.expressions.Expression
import com.android.tools.idea.observable.ui.SelectedItemProperty
import com.android.tools.idea.observable.ui.SelectedProperty
import com.android.tools.idea.observable.ui.TextProperty
import com.android.tools.idea.project.AndroidProjectInfo
import com.android.tools.idea.ui.wizard.StudioWizardStepPanel
import com.android.tools.idea.ui.wizard.WizardUtils
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.android.tools.idea.wizard.model.SkippableWizardStep
import com.android.tools.idea.wizard.template.Language
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.layout.panel
import org.jetbrains.android.refactoring.isAndroidx
import org.jetbrains.android.util.AndroidBundle
import java.util.Optional
import java.util.function.Consumer
import javax.swing.DefaultComboBoxModel
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JTextField

/**
 * This class configures the Dynamic Feature Module specific data such as the "Base Application Module", "Module Name", "Package Name" and
 * "Minimum SDK".
 */
class ConfigureDynamicModuleStep(
  model: DynamicFeatureModel, basePackage: String
) : SkippableWizardStep<DynamicFeatureModel>(model, AndroidBundle.message("android.wizard.module.config.title")) {
  private val bindings = BindingsManager()
  private val listeners = ListenerManager()

  private val androidVersionsInfo = AndroidVersionsInfo()

  private val baseApplication: JComboBox<Module> = ComboBox<Module>(DefaultComboBoxModel()).apply {
    setRenderer(SimpleListCellRenderer.create { label: JBLabel, module: Module?, _: Int ->
      if (module == null) {
        label.text = AndroidBundle.message("android.wizard.module.config.new.base.missing")
      }
      else {
        label.icon = ModuleType.get(module).icon
        label.text = module.name
      }
    })
  }
  private val moduleName: JTextField = JBTextField()
  private val packageName: LabelWithEditButton = LabelWithEditButton()
  private val languageCombo: JComboBox<Language> = LanguageComboProvider().createComponent()
  // TODO(qumeric): private val bytecodeCombo: JComboBox<BytecodeLevel> = BytecodeLevelComboProvider().createComponent()
  private val apiLevelCombo: AndroidApiLevelComboBox = AndroidApiLevelComboBox()

  // specific to dynamic modules
  private val moduleTitle: JTextField = JBTextField()
  private val fusingCheckbox: JCheckBox = JBCheckBox("Fusing (install module on pre-Lollipop devices)")

  private val panel: DialogPanel = panel {
    row {
      labelFor("Base Application Module", baseApplication)
      baseApplication()
    }

    row {
      cell {
        labelFor("Module name", moduleName)
        ContextHelpLabel.create(AndroidBundle.message("android.wizard.module.help.name"))()
      }
      moduleName()
    }

    row {
      labelFor("Package name", packageName)
      packageName()
    }

    row {
      labelFor("Language", languageCombo)
      languageCombo()
    }

    row {
      labelFor("Minimum SDK", apiLevelCombo)
      apiLevelCombo()
    }

    row {
      labelFor("Module title (this may be visible to users)", moduleTitle)
      moduleTitle()
    }.visible = model.isInstant

    row {
      // TODO(qumeric): labelFor?
      fusingCheckbox()
    }
  }
  private val validatorPanel: ValidatorPanel = ValidatorPanel(this, panel)
  private val scrollPanel: JBScrollPane = StudioWizardStepPanel.wrappedWithVScroll(validatorPanel)

  init {
    val moduleValidator = ModuleValidator(model.project)
    moduleName.text = WizardUtils.getUniqueName(model.moduleName.get(), moduleValidator)

    bindings.bindTwoWay(SelectedItemProperty(languageCombo), model.language)
    // TODO(qumeric): bindings.bindTwoWay(SelectedItemProperty(bytecodeCombo), model.bytecodeLevel)
    bindings.bind(model.androidSdkInfo, SelectedItemProperty(apiLevelCombo))

    val isPackageNameSynced: BoolProperty = BoolValueProperty(true)
    val packageNameText = TextProperty(packageName)
    val computedPackageName: Expression<String> = object : Expression<String>(model.moduleName) {
      override fun get() = "${basePackage}.${nameToJavaPackage(model.moduleName.get())}"
    }
    bindings.bind(packageNameText, computedPackageName, isPackageNameSynced)
    bindings.bind(model.packageName, packageNameText)
    listeners.listen(packageNameText) { value: String -> isPackageNameSynced.set(value == computedPackageName.get()) }

    val isModuleNameSynced: BoolProperty = BoolValueProperty(true)
    val moduleNameText = TextProperty(moduleName)
    val computedModuleName: Expression<String> =
      AppNameToModuleNameExpression(model.project, model.applicationName, model.moduleParent)
    bindings.bind(moduleNameText, computedModuleName, isModuleNameSynced)
    bindings.bind(model.moduleName, moduleNameText)
    listeners.listen(moduleNameText) { value: String -> isModuleNameSynced.set(value == computedModuleName.get()) }

    AndroidProjectInfo.getInstance(model.project).getAllModulesOfProjectType(AndroidProjectTypes.PROJECT_TYPE_APP)
      .stream()
      .filter { module: Module? -> AndroidModuleModel.get(module!!) != null }
      .forEach { module: Module -> baseApplication.addItem(module) }
    val baseApplication: OptionalProperty<Module> = model.baseApplication
    bindings.bind(baseApplication, SelectedItemProperty(this.baseApplication))

    validatorPanel.apply {
      registerValidator(model.moduleName, moduleValidator)
      registerValidator(model.packageName, PackageNameValidator())
      registerValidator(model.androidSdkInfo, ApiVersionValidator(model.project.isAndroidx(), FormFactor.MOBILE))
      // TODO(qumeric): consider moving to a separate class
      registerValidator(
        baseApplication,
        object : Validator<Optional<Module>> {
          override fun validate(value: Optional<Module>): Validator.Result =
            if (value.isPresent)
              Validator.Result.OK
            else
              Validator.Result(Validator.Severity.ERROR, AndroidBundle.message("android.wizard.module.new.dynamic.select.base"))
        })
    }

    if (model.isInstant) {
      val isFusingSelected = SelectedProperty(fusingCheckbox)
      bindings.bind(model.featureFusing, isFusingSelected)
      val isOnDemand: BoolProperty = BoolValueProperty(false)
      bindings.bind(model.featureOnDemand, isOnDemand)
      val isInstantModule: BoolProperty = BoolValueProperty(true)
      bindings.bind(model.instantModule, isInstantModule)
      bindings.bindTwoWay(TextProperty(moduleTitle), getModel().featureTitle)
    }
    else {
      fusingCheckbox.isVisible = false
    }

    FormScalingUtil.scaleComponentTree(this.javaClass, validatorPanel)
  }

  override fun createDependentSteps(): Collection<ModelWizardStep<*>> = listOf(ConfigureModuleDownloadOptionsStep(model))

  override fun onEntering() {
    androidVersionsInfo.loadLocalVersions()
    apiLevelCombo.init(FormFactor.MOBILE, androidVersionsInfo.getKnownTargetVersions(FormFactor.MOBILE, SdkVersionInfo.LOWEST_ACTIVE_API))
    androidVersionsInfo.loadRemoteTargetVersions(
      FormFactor.MOBILE, SdkVersionInfo.LOWEST_ACTIVE_API, Consumer { items -> apiLevelCombo.init(FormFactor.MOBILE, items) }
    )
  }

  override fun onProceeding() {
    // Now that the module name was validated, update the model template
    model.template.set(GradleAndroidModuleTemplate.createDefaultTemplateAt(model.project.basePath!!, model.moduleName.get()))

    // TODO(qumeric): should we set installRequests here?
  }

  override fun canGoForward(): ObservableBool = validatorPanel.hasErrors().not()

  override fun getComponent(): JComponent = scrollPanel

  override fun getPreferredFocusComponent(): JComponent? = moduleName

  override fun dispose() {
    bindings.releaseAll()
    listeners.releaseAll()
  }
}