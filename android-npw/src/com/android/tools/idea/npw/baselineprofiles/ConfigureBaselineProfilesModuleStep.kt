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
import com.android.ide.common.gradle.Version
import com.android.sdklib.SdkVersionInfo
import com.android.tools.adtui.device.FormFactor
import com.android.tools.adtui.validation.Validator
import com.android.tools.adtui.validation.createValidator
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.model.IdeArtifactLibrary
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.util.BaselineProfileUtil.BP_PLUGIN_MIN_SUPPORTED
import com.android.tools.idea.model.AndroidManifestIndex
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.model.UsedFeatureRawText
import com.android.tools.idea.npw.contextLabel
import com.android.tools.idea.npw.model.NewProjectModel.Companion.getSuggestedProjectPackage
import com.android.tools.idea.npw.module.AndroidApiLevelComboBox
import com.android.tools.idea.npw.module.ConfigureModuleStep
import com.android.tools.idea.npw.module.generateBuildConfigurationLanguageRow
import com.android.tools.idea.npw.template.components.ModuleComboProvider
import com.android.tools.idea.npw.validator.ModuleSelectedValidator
import com.android.tools.idea.observable.ui.SelectedItemProperty
import com.android.tools.idea.observable.ui.SelectedProperty
import com.android.tools.idea.project.AndroidProjectInfo
import com.android.tools.idea.projectsystem.getSyncManager
import com.android.tools.idea.util.androidFacet
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.jetbrains.rd.util.firstOrNull
import javax.swing.JComboBox
import javax.swing.JPanel
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.annotations.VisibleForTesting


private val USES_FEATURE_OTHER_FORM_FACTORS = setOf(
  "android.hardware.type.automotive",
  "android.hardware.type.watch",
  "android.software.leanback"
)

private val ANDROIDX_BENCHMARK_MIN_LIBRARY_VERSION = Version.parse("1.2.0")
private const val ANDROIDX_BENCHMARK_LIBRARY_GROUP = "androidx.benchmark"

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
  val useGmdCheck = JBCheckBox("Use Gradle Managed Device", false)

  init {
    bindTargetModule()
    validateAgpVersion()
    validateTargetModule()
    validatePackageName()
    validateAndroidXBenchmarkDependencyVersion()

    bindings.bind(model.useGmd, SelectedProperty(useGmdCheck))
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
      useGmdCheck.isEnabled = appModules[itemEvent.item]?.usesFeatureAutomotiveOrWearOrTv() == false
      useGmdCheck.isSelected = false
    }

    // Tries to select the first non automotive, tv, wear project
    appModules
      .toList()
      .firstOrNull { !it.second.usesFeatureAutomotiveOrWearOrTv() }
      ?.first
      ?.let {
        model.targetModule.value = it
        useGmdCheck.isEnabled = true
        useGmdCheck.isSelected = false
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

  private fun validateAgpVersion() =
    validatorPanel.registerValidator(
      model.agpVersion,
      createValidator { version ->
        if (version.compareIgnoringQualifiers(BP_PLUGIN_MIN_SUPPORTED) < 0) {
          Validator.Result.fromNullableMessage(
            AndroidBundle.message(
              "android.wizard.validate.module.needs.new.agp.baseline.profiles",
              BP_PLUGIN_MIN_SUPPORTED.toString()
            )
          )
        } else {
          Validator.Result.OK
        }
      }
    )

  private fun validateTargetModule() =
    validatorPanel.registerValidator(
      model.targetModule,
      createValidator {
        if (model.project.getSyncManager().isSyncNeeded()) {
          return@createValidator Validator.Result.fromNullableMessage(
            AndroidBundle.message("android.wizard.validate.module.sync.needed.baseline.profiles")
          )
        }
        if (it.isEmpty) {
          return@createValidator Validator.Result.fromNullableMessage(
            AndroidBundle.message("android.wizard.validate.module.not.present.baseline.profiles")
          )
        }
        val module = it.get()
        val androidModel =
          GradleAndroidModel.get(module)
            ?: return@createValidator Validator.Result.fromNullableMessage(
              AndroidBundle.message(
                "android.wizard.validate.module.invalid.application.baseline.profiles"
              )
            )
        if (
          androidModel.applicationId.isEmpty() ||
            androidModel.applicationId == AndroidModel.UNINITIALIZED_APPLICATION_ID
        ) {
          Validator.Result.fromNullableMessage(
            AndroidBundle.message(
              "android.wizard.validate.module.invalid.application.id.baseline.profiles"
            )
          )
        } else {
          Validator.Result.OK
        }
      },
      model.packageName
    )

  private fun validatePackageName() =
    validatorPanel.registerValidator(
      model.packageName,
      createValidator {
        val packageName =
          it.ifEmpty {
            return@createValidator Validator.Result.fromNullableMessage(
              AndroidBundle.message(
                "android.wizard.validate.module.empty.package.name.baseline.profiles"
              )
            )
          }
        val module =
          model.targetModule.get().let { optionalModule ->
            if (optionalModule.isEmpty) {
              return@createValidator Validator.Result.fromNullableMessage(
                AndroidBundle.message(
                  "android.wizard.validate.module.not.present.baseline.profiles"
                )
              )
            } else {
              optionalModule.get()
            }
          }
        val androidModel =
          GradleAndroidModel.get(module)
            ?: return@createValidator Validator.Result.fromNullableMessage(
              AndroidBundle.message(
                "android.wizard.validate.module.invalid.application.baseline.profiles"
              )
            )
        if (androidModel.applicationId == packageName) {
          Validator.Result.fromNullableMessage(
            AndroidBundle.message(
              "android.wizard.validate.module.same.package.name.baseline.profiles"
            )
          )
        } else {
          Validator.Result.OK
        }
      },
      model.targetModule
    )

  private fun validateAndroidXBenchmarkDependencyVersion() =
    validatorPanel.registerValidator(
      model.targetModule,
      createValidator {
        if (model.project.getSyncManager().isSyncNeeded()) {
          return@createValidator Validator.Result.fromNullableMessage(
            AndroidBundle.message("android.wizard.validate.module.sync.needed.baseline.profiles")
          )
        }
        if (it.isEmpty) {
          return@createValidator Validator.Result.fromNullableMessage(
            AndroidBundle.message("android.wizard.validate.module.not.present.baseline.profiles")
          )
        }
        val module = it.get()
        GradleAndroidModel.get(module) ?: return@createValidator Validator.Result.fromNullableMessage(
          AndroidBundle.message("android.wizard.validate.module.invalid.application.baseline.profiles")
        )

        if (getBenchmarkLibrariesInTestModulesLessThanMinVersion(module.project).isNotEmpty()) {
          return@createValidator Validator.Result(
            Validator.Severity.WARNING,
            AndroidBundle.message("android.wizard.module.help.baselineprofiles.minversionrequired")
          )
        } else {
          return@createValidator Validator.Result.OK
        }
      },
      model.targetModule
    )

  override fun createMainPanel(): JPanel = panel {
    row {
      comment("<strong>" + AndroidBundle.message("android.wizard.module.new.baselineprofiles.module.description") + "</strong>")
    }

    row {
      comment(AndroidBundle.message("android.wizard.module.new.baselineprofiles.module.description.extra"))
    }

    row(
      contextLabel("Target application", AndroidBundle.message("android.wizard.module.help.baselineprofiles.target.module.description"))) {
      cell(targetModuleCombo).align(AlignX.FILL)
    }

    row(contextLabel("Module name", AndroidBundle.message("android.wizard.module.help.name"))) {
      cell(moduleName).align(AlignX.FILL)
    }

    row("Package name") {
      cell(packageName).align(AlignX.FILL)
    }

    row("Language") {
      cell(languageCombo).align(AlignX.FILL)
    }

    if (StudioFlags.NPW_SHOW_KTS_GRADLE_COMBO_BOX.get()) {
      generateBuildConfigurationLanguageRow(buildConfigurationLanguageCombo)
    }

    row {
      topGap(TopGap.SMALL)
      cell(useGmdCheck)
      rowComment(AndroidBundle.message("android.wizard.module.help.baselineprofiles.usegmd.description"))
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

fun getBenchmarkLibrariesInTestModulesLessThanMinVersion(project: Project) =
  AndroidProjectInfo
    .getInstance(project)
    .getAllModulesOfProjectType(AndroidProjectTypes.PROJECT_TYPE_TEST)
    .asSequence()
    .filter { it.androidFacet != null }
    .mapNotNull { GradleAndroidModel.get(it) }
    .flatMap { it.variants }
    .flatMap { v -> v.mainArtifact.compileClasspath.libraries }
    .filterIsInstance<IdeArtifactLibrary>()
    .filter { it.name.startsWith(ANDROIDX_BENCHMARK_LIBRARY_GROUP) }
    .filter { it.component?.let { c -> c.version <= ANDROIDX_BENCHMARK_MIN_LIBRARY_VERSION } ?: false }
    .toList()

fun List<UsedFeatureRawText>.usesFeatureAutomotiveOrWearOrTv() = any { it.name in USES_FEATURE_OTHER_FORM_FACTORS && it.required?.toBooleanStrictOrNull() ?: true }
