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
package com.android.tools.idea.npw.baselineprofiles

import com.android.AndroidProjectTypes
import com.android.sdklib.SdkVersionInfo
import com.android.tools.adtui.device.FormFactor
import com.android.tools.adtui.validation.Validator
import com.android.tools.adtui.validation.createValidator
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.model.AndroidManifestIndex
import com.android.tools.idea.model.UsedFeatureRawText
import com.android.tools.idea.npw.contextLabel
import com.android.tools.idea.npw.model.NewProjectModel.Companion.getSuggestedProjectPackage
import com.android.tools.idea.npw.module.AndroidApiLevelComboBox
import com.android.tools.idea.npw.module.ConfigureModuleStep
import com.android.tools.idea.npw.module.generateBuildConfigurationLanguageRow
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.BaselineProfilesMacrobenchmarkCommon.BP_PLUGIN_MIN_SUPPORTED
import com.android.tools.idea.npw.template.components.ModuleComboProvider
import com.android.tools.idea.npw.validator.ModuleSelectedValidator
import com.android.tools.idea.observable.ui.SelectedItemProperty
import com.android.tools.idea.observable.ui.SelectedProperty
import com.android.tools.idea.project.AndroidProjectInfo
import com.android.tools.idea.util.androidFacet
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.jetbrains.rd.util.firstOrNull
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.annotations.VisibleForTesting
import javax.swing.JComboBox
import javax.swing.JPanel


private const val GMD_LINK = "https://d.android.com/r/studio-ui/testing/gradle-managed-devices"

private val USES_FEATURE_OTHER_FORM_FACTORS = setOf(
  "android.hardware.type.automotive",
  "android.hardware.type.watch",
  "android.software.leanback"
)

class ConfigureBaselineProfilesModuleStep(
  model: NewBaselineProfilesModuleModel,
  disposable: Disposable? = null,
) : ConfigureModuleStep<NewBaselineProfilesModuleModel>(
  model = model,
  formFactor = FormFactor.MOBILE,
  minSdkLevel = SdkVersionInfo.LOWEST_PROFILE_GUIDED_OPTIMIZATIONS_SDK_VERSION,
  basePackage = getSuggestedProjectPackage(),
  title = AndroidBundle.message("android.wizard.module.new.baselineprofiles.module.app"),
  parentDisposable = disposable
) {

  @VisibleForTesting
  val targetModuleCombo: JComboBox<Module> = ModuleComboProvider().createComponent()

  @VisibleForTesting
  @Suppress("DialogTitleCapitalization")
  val useGmdCheck = JBCheckBox("Use Gradle Managed Device")

  init {
    bindTargetModule()
    validateMinAgpVersion()

    bindings.bind(model.useGmd, SelectedProperty(useGmdCheck))
    bindings.bind(model.agpVersion, agpVersion)
  }

  private fun bindTargetModule() {

    // Map each module with the parsed manifests. This allows to check later
    // what uses-feature tags are defined in the manifest and whether a module is for tv,
    // automotive and/or wear. We only enable GMD for smartphones.
    val appModules = AndroidProjectInfo
      .getInstance(model.project)
      .getAllModulesOfProjectType(AndroidProjectTypes.PROJECT_TYPE_APP)
      .filter { it.androidFacet != null }
      .associateWith { module ->
        AndroidManifestIndex.getDataForMergedManifestContributors(module.androidFacet!!).toList().flatMap { it.usedFeatures }
      }

    appModules.forEach { e ->
      targetModuleCombo.addItem(e.key)
    }

    targetModuleCombo.addItemListener { itemEvent ->
      appModules[itemEvent.item]?.usesFeatureAutomotiveOrWearOrTv()?.let {
        useGmdCheck.isEnabled = !it
        useGmdCheck.isSelected = !it
      }
    }

    // Tries to select the first non automotive, tv, wear project
    appModules
      .toList()
      .firstOrNull { !it.second.usesFeatureAutomotiveOrWearOrTv() }
      ?.first
      ?.let {
        model.targetModule.value = it
        useGmdCheck.isEnabled = true
        useGmdCheck.isSelected = true
      }

    // If nothing was selected, then select the first of the list and set `use gmd` false
    if (!model.targetModule.isPresent.get()) {
      appModules.firstOrNull()?.let { model.targetModule.value = it.key }
      useGmdCheck.isEnabled = false
      useGmdCheck.isSelected = false
    }

    bindings.bindTwoWay(SelectedItemProperty(targetModuleCombo), model.targetModule)

    val targetModuleValidator = ModuleSelectedValidator()
    validatorPanel.registerValidator(model.targetModule, createValidator { value ->
      targetModuleValidator.validate(value)
    })
  }

  private fun validateMinAgpVersion() {
    validatorPanel.registerValidator(agpVersion, createValidator { version ->
      if (version.isPresent && version.get().compareIgnoringQualifiers(BP_PLUGIN_MIN_SUPPORTED) < 0) {
        Validator.Result.fromNullableMessage(
          AndroidBundle.message("android.wizard.validate.module.needs.new.agp.baseline.profiles", BP_PLUGIN_MIN_SUPPORTED.toString())
        )
      }
      else {
        Validator.Result.OK
      }
    })
  }

  override fun createMainPanel(): JPanel = panel {
    row {
      comment("<strong>" + AndroidBundle.message("android.wizard.module.new.baselineprofiles.module.description") + "</strong>")
    }

    row {
      comment(AndroidBundle.message("android.wizard.module.new.baselineprofiles.module.description.extra"))
    }

    row(
      contextLabel("Target application", AndroidBundle.message("android.wizard.module.help.baselineprofiles.target.module.description"))) {
      cell(targetModuleCombo).horizontalAlign(HorizontalAlign.FILL)
    }

    row(contextLabel("Module name", AndroidBundle.message("android.wizard.module.help.name"))) {
      cell(moduleName).horizontalAlign(HorizontalAlign.FILL)
    }

    row("Package name") {
      cell(packageName).horizontalAlign(HorizontalAlign.FILL)
    }

    row("Language") {
      cell(languageCombo).horizontalAlign(HorizontalAlign.FILL)
    }

    if (StudioFlags.NPW_SHOW_KTS_GRADLE_COMBO_BOX.get()) {
      generateBuildConfigurationLanguageRow(buildConfigurationLanguageCombo)
    }

    row {
      cell(useGmdCheck)
      cell(
        ContextHelpLabel.createWithLink(
          null,
          AndroidBundle.message("android.wizard.module.help.baselineprofiles.usegmd.description"),
          "Learn more"
        ) { BrowserUtil.browse(GMD_LINK) }
      ).horizontalAlign(HorizontalAlign.LEFT)
    }
  }

  override fun onEntering() {
    super.onEntering()

    // Apply before targetModule triggers any change
    apiLevelCombo.selectSdkLevel(getBaselineProfilesMinSdk(model.targetModule.valueOrNull))

    // Listen for module changes and update min sdk
    listeners.listen(model.targetModule) { targetModule ->
      apiLevelCombo.selectSdkLevel(getBaselineProfilesMinSdk(targetModule.orElse(null)))
    }
  }

  private fun AndroidApiLevelComboBox.selectSdkLevel(minApiLevel: Int) {
    (0 until itemCount)
      .firstOrNull { getItemAt(it)?.minApiLevel == minApiLevel }
      ?.let { selectedIndex = it }
  }
}

fun List<UsedFeatureRawText>.usesFeatureAutomotiveOrWearOrTv() = any { it.name in USES_FEATURE_OTHER_FORM_FACTORS && it.required?.toBooleanStrictOrNull() ?: true }
