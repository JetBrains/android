/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.npw.module

import com.android.repository.api.RemotePackage
import com.android.repository.api.UpdatablePackage
import com.android.tools.adtui.LabelWithEditButton
import com.android.tools.adtui.util.FormScalingUtil
import com.android.tools.adtui.validation.ValidatorPanel
import com.android.tools.idea.device.FormFactor
import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate
import com.android.tools.idea.npw.labelFor
import com.android.tools.idea.npw.model.NewAndroidModuleModel
import com.android.tools.idea.npw.model.NewProjectModel.Companion.nameToJavaPackage
import com.android.tools.idea.npw.model.RenderTemplateModel
import com.android.tools.idea.npw.model.RenderTemplateModel.Companion.fromModuleModel
import com.android.tools.idea.npw.platform.AndroidVersionsInfo
import com.android.tools.idea.npw.platform.sdkManagerLocalPath
import com.android.tools.idea.npw.template.ChooseActivityTypeStep
import com.android.tools.idea.npw.template.components.BytecodeLevelComboProvider
import com.android.tools.idea.npw.template.components.LanguageComboProvider
import com.android.tools.idea.npw.validator.ApiVersionValidator
import com.android.tools.idea.npw.validator.ModuleValidator
import com.android.tools.idea.npw.validator.PackageNameValidator
import com.android.tools.idea.npw.validator.ProjectNameValidator
import com.android.tools.idea.observable.BindingsManager
import com.android.tools.idea.observable.ListenerManager
import com.android.tools.idea.observable.core.BoolProperty
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.ObservableBool
import com.android.tools.idea.observable.expressions.Expression
import com.android.tools.idea.observable.ui.SelectedItemProperty
import com.android.tools.idea.observable.ui.TextProperty
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.wizard.InstallSelectedPackagesStep
import com.android.tools.idea.sdk.wizard.LicenseAgreementModel
import com.android.tools.idea.sdk.wizard.LicenseAgreementStep
import com.android.tools.idea.ui.wizard.StudioWizardStepPanel
import com.android.tools.idea.ui.wizard.WizardUtils
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.android.tools.idea.wizard.model.SkippableWizardStep
import com.android.tools.idea.wizard.template.BytecodeLevel
import com.android.tools.idea.wizard.template.Language
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.layout.PropertyBinding
import com.intellij.ui.layout.panel
import com.intellij.ui.layout.withTextBinding
import org.jetbrains.android.refactoring.isAndroidx
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.android.util.AndroidBundle.message
import java.util.function.Consumer
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JTextField

class ConfigureAndroidModuleStep(
  model: NewAndroidModuleModel,
  private val formFactor: FormFactor,
  private val minSdkLevel: Int,
  basePackage: String?,
  title: String
) : SkippableWizardStep<NewAndroidModuleModel>(model, title, formFactor.icon) {
  private val bindings = BindingsManager()
  private val listeners = ListenerManager()

  private val androidVersionsInfo = AndroidVersionsInfo()
  private var installRequests: List<UpdatablePackage> = listOf()
  private var installLicenseRequests: List<RemotePackage> = listOf()

  private val appName: JTextField = JBTextField(model.applicationName.get())
  private val moduleName: JTextField = JBTextField()
  private val packageName: LabelWithEditButton = LabelWithEditButton()
  private val languageCombo: JComboBox<Language> = LanguageComboProvider().createComponent()
  private val bytecodeCombo: JComboBox<BytecodeLevel> = BytecodeLevelComboProvider().createComponent()
  private val apiLevelCombo: AndroidApiLevelComboBox = AndroidApiLevelComboBox()

  private val panel: DialogPanel = panel {
    row {
      labelFor("Application/Library name", appName)
      appName().withTextBinding(
        PropertyBinding({ model.applicationName.get() }, { model.applicationName.set(it) })
      )
    }.visible = !model.isLibrary

    row {
      cell {
        labelFor("Module name", moduleName, message("android.wizard.module.help.name"))
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
      labelFor("Bytecode Level", bytecodeCombo)
      bytecodeCombo()
    }

    row {
      labelFor("Minimum SDK", apiLevelCombo)
      apiLevelCombo()
    }
  }
  private val validatorPanel: ValidatorPanel = ValidatorPanel(this, StudioWizardStepPanel.wrappedWithVScroll(panel))

  init {
    val moduleValidator = ModuleValidator(model.project)
    moduleName.text = WizardUtils.getUniqueName(model.moduleName.get(), moduleValidator)

    bindings.bindTwoWay(SelectedItemProperty(languageCombo), model.language)
    bindings.bindTwoWay(SelectedItemProperty(bytecodeCombo), model.bytecodeLevel)
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

    validatorPanel.apply {
      registerValidator(model.applicationName, ProjectNameValidator())
      registerValidator(model.moduleName, moduleValidator)
      registerValidator(model.packageName, PackageNameValidator())
      registerValidator(model.androidSdkInfo, ApiVersionValidator(model.project.isAndroidx(), formFactor))
    }

    FormScalingUtil.scaleComponentTree(this.javaClass, validatorPanel)
  }

  override fun createDependentSteps(): Collection<ModelWizardStep<*>> {
    val renderModel: RenderTemplateModel = fromModuleModel(model, AndroidBundle.message("android.wizard.activity.add", formFactor.id))
    // Note: MultiTemplateRenderer needs that all Models constructed (ie myRenderModel) are inside a Step, so handleSkipped() is called
    val chooseActivityStep = ChooseActivityTypeStep.forNewModule(renderModel, formFactor, model.androidSdkInfo)
    chooseActivityStep.setShouldShow(!model.isLibrary)
    val licenseAgreementStep = LicenseAgreementStep(LicenseAgreementModel(sdkManagerLocalPath), installLicenseRequests)
    val installPackagesStep = InstallSelectedPackagesStep(installRequests, hashSetOf(), AndroidSdks.getInstance().tryToChooseSdkHandler(),
                                                          false)
    return listOf(chooseActivityStep, licenseAgreementStep, installPackagesStep)
  }

  override fun onEntering() {
    // TODO: The old version only loaded the list of version once, and kept everything on a static field
    // Possible solutions: Move AndroidVersionsInfo/load to the class that instantiates this step?
    androidVersionsInfo.loadLocalVersions()
    apiLevelCombo.init(formFactor, androidVersionsInfo.getKnownTargetVersions(formFactor, minSdkLevel)) // Pre-populate
    androidVersionsInfo.loadRemoteTargetVersions(
      formFactor, minSdkLevel, Consumer { items -> apiLevelCombo.init(formFactor, items) }
    )
  }

  override fun onProceeding() {
    // Now that the module name was validated, update the model template
    model.template.set(GradleAndroidModuleTemplate.createDefaultTemplateAt(model.project.basePath!!, model.moduleName.get()))

    installRequests = androidVersionsInfo.loadInstallPackageList(listOf(model.androidSdkInfo.value))
    installLicenseRequests = installRequests.map { it.remote!! }
  }

  public override fun canGoForward(): ObservableBool = validatorPanel.hasErrors().not()

  override fun getComponent(): JComponent = validatorPanel

  override fun getPreferredFocusComponent(): JComponent? = appName

  override fun dispose() {
    bindings.releaseAll()
    listeners.releaseAll()
  }
}