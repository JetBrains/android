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
import com.android.sdklib.SdkVersionInfo
import com.android.tools.adtui.util.FormScalingUtil
import com.android.tools.adtui.validation.ValidatorPanel
import com.android.tools.adtui.device.FormFactor
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate
import com.android.tools.idea.npw.model.NewProjectModel.Companion.getSuggestedProjectPackage
import com.android.tools.idea.npw.model.NewProjectModel.Companion.nameToJavaPackage
import com.android.tools.idea.npw.platform.AndroidVersionsInfo
import com.android.tools.idea.npw.platform.sdkManagerLocalPath
import com.android.tools.idea.npw.template.components.LanguageComboProvider
import com.android.tools.idea.npw.validator.ApiVersionValidator
import com.android.tools.idea.npw.validator.ModuleValidator
import com.android.tools.idea.npw.validator.PackageNameValidator
import com.android.tools.idea.observable.BindingsManager
import com.android.tools.idea.observable.ListenerManager
import com.android.tools.idea.observable.core.BoolProperty
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.ObservableBool
import com.android.tools.idea.observable.expressions.Expression
import com.android.tools.idea.observable.ui.SelectedItemProperty
import com.android.tools.idea.observable.ui.SelectedProperty
import com.android.tools.idea.observable.ui.TextProperty
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.wizard.InstallSelectedPackagesStep
import com.android.tools.idea.sdk.wizard.LicenseAgreementModel
import com.android.tools.idea.sdk.wizard.LicenseAgreementStep
import com.android.tools.idea.ui.wizard.WizardUtils.WIZARD_BORDER.SMALL
import com.android.tools.idea.ui.wizard.WizardUtils.wrapWithVScroll
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.android.tools.idea.wizard.model.SkippableWizardStep
import com.android.tools.idea.wizard.template.Language
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import org.jetbrains.android.refactoring.isAndroidx
import java.util.function.Consumer
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField

abstract class ConfigureModuleStep<ModuleModelKind: ModuleModel>(
  model: ModuleModelKind,
  val formFactor: FormFactor,
  private val minSdkLevel: Int = SdkVersionInfo.LOWEST_ACTIVE_API,
  basePackage: String? = getSuggestedProjectPackage(),
  title: String
) : SkippableWizardStep<ModuleModelKind>(model, title, formFactor.icon) {
  protected val bindings = BindingsManager()
  protected val listeners = ListenerManager()

  private val androidVersionsInfo = AndroidVersionsInfo()
  private var installRequests: List<UpdatablePackage> = listOf()
  private var installLicenseRequests: List<RemotePackage> = listOf()

  protected val moduleName: JTextField = JBTextField()
  protected val packageName: JTextField = JBTextField()
  protected val languageCombo: JComboBox<Language> = LanguageComboProvider().createComponent()
  protected val apiLevelCombo: AndroidApiLevelComboBox = AndroidApiLevelComboBox()
  protected val gradleKtsCheck: JBCheckBox = JBCheckBox("Use Kotlin script (.kts) for Gradle build files")
  protected val validatorPanel: ValidatorPanel by lazy {
    ValidatorPanel(this, createMainPanel()).apply {
      registerValidator(model.moduleName, moduleValidator)
      registerValidator(model.packageName, PackageNameValidator())
      registerValidator(model.androidSdkInfo, ApiVersionValidator(model.project.isAndroidx(), formFactor))
      FormScalingUtil.scaleComponentTree(this@ConfigureModuleStep.javaClass, this)
    }
  }
  protected val rootPanel: JScrollPane by lazy {
    if (StudioFlags.NPW_NEW_MODULE_WITH_SIDE_BAR.get()) {
      wrapWithVScroll(validatorPanel, SMALL)
    }
    else {
      wrapWithVScroll(validatorPanel)
    }
  }

  abstract fun createMainPanel(): JPanel

  private val moduleValidator = ModuleValidator(model.project)
  init {
    bindings.bindTwoWay(SelectedItemProperty(languageCombo), model.language)
    bindings.bind(model.androidSdkInfo, SelectedItemProperty(apiLevelCombo))
    bindings.bindTwoWay(SelectedProperty(gradleKtsCheck), model.useGradleKts)

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
    // Module name is generate from application name in case of Android Module (e.g. mobile, watch) but in all other cases
    // a hardcoded name is used (e.g. "benchmark", "lib").
    val defaultModuleName = if (model.moduleName.isEmpty.get()) model.applicationName else model.moduleName
    val computedModuleName = UniqueModuleGradlePathWithParentExpression(model.project, defaultModuleName, model.moduleParent)
    bindings.bind(moduleNameText, computedModuleName, isModuleNameSynced)
    bindings.bind(model.moduleName, moduleNameText)
    listeners.listen(moduleNameText) { value: String -> isModuleNameSynced.set(value == computedModuleName.get()) }
  }

  override fun createDependentSteps(): Collection<ModelWizardStep<*>> {
    val licenseAgreementStep = LicenseAgreementStep(LicenseAgreementModel(sdkManagerLocalPath), installLicenseRequests)
    val installPackagesStep = InstallSelectedPackagesStep(installRequests, hashSetOf(), AndroidSdks.getInstance().tryToChooseSdkHandler(), false)
    return listOf(licenseAgreementStep, installPackagesStep)
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

  public final override fun canGoForward(): ObservableBool = validatorPanel.hasErrors().not()

  final override fun getComponent(): JComponent = rootPanel

  override fun dispose() {
    bindings.releaseAll()
    listeners.releaseAll()
  }
}