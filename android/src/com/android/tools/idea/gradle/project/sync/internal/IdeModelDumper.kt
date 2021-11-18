/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.internal

import com.android.tools.idea.gradle.model.IdeAaptOptions
import com.android.tools.idea.gradle.model.IdeAndroidArtifact
import com.android.tools.idea.gradle.model.IdeAndroidGradlePluginProjectFlags
import com.android.tools.idea.gradle.model.IdeAndroidLibrary
import com.android.tools.idea.gradle.model.IdeAndroidProject
import com.android.tools.idea.gradle.model.IdeApiVersion
import com.android.tools.idea.gradle.model.IdeArtifactLibrary
import com.android.tools.idea.gradle.model.IdeBaseArtifact
import com.android.tools.idea.gradle.model.IdeBaseConfig
import com.android.tools.idea.gradle.model.IdeBuildTasksAndOutputInformation
import com.android.tools.idea.gradle.model.IdeBuildTypeContainer
import com.android.tools.idea.gradle.model.IdeDependencies
import com.android.tools.idea.gradle.model.IdeDependenciesInfo
import com.android.tools.idea.gradle.model.IdeJavaArtifact
import com.android.tools.idea.gradle.model.IdeJavaCompileOptions
import com.android.tools.idea.gradle.model.IdeJavaLibrary
import com.android.tools.idea.gradle.model.IdeLibrary
import com.android.tools.idea.gradle.model.IdeLintOptions
import com.android.tools.idea.gradle.model.IdeModuleLibrary
import com.android.tools.idea.gradle.model.IdeProductFlavor
import com.android.tools.idea.gradle.model.IdeProductFlavorContainer
import com.android.tools.idea.gradle.model.IdeSigningConfig
import com.android.tools.idea.gradle.model.IdeSourceProvider
import com.android.tools.idea.gradle.model.IdeSourceProviderContainer
import com.android.tools.idea.gradle.model.IdeTestOptions
import com.android.tools.idea.gradle.model.IdeTestedTargetVariant
import com.android.tools.idea.gradle.model.IdeVariant
import com.android.tools.idea.gradle.model.IdeVariantBuildInformation
import com.android.tools.idea.gradle.model.IdeViewBindingOptions
import com.android.tools.idea.gradle.model.IdeModelSyncFile
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.project.model.NdkModuleModel
import com.android.tools.idea.projectsystem.isHolderModule
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.io.sanitizeFileName
import org.jetbrains.kotlin.gradle.KotlinGradleModel
import org.jetbrains.kotlin.kapt.idea.KaptGradleModel
import java.io.File

fun ProjectDumper.dumpAndroidIdeModel(
  project: Project,
  kotlinModels: (com.intellij.openapi.module.Module) -> KotlinGradleModel?,
  kaptModels: (com.intellij.openapi.module.Module) -> KaptGradleModel?
) {
  nest(File(project.basePath!!), "PROJECT") {
    with(ideModelDumper(this)) {
      ModuleManager.getInstance(project).modules.sortedBy { it.name }.forEach { module ->
        head("MODULE") { module.name }
        nest {
          AndroidModuleModel.get(module)?.let { it ->
            // Skip all but holders to prevent needless spam in the snapshots. All modules
            // point to the same facet.
            if (!module.isHolderModule()) return@let
            dump(it.androidProject)
            // Dump all the fetched Ide variants.
            head("IdeVariants")
            nest {
              it.variants.forEach { ideVariant ->
                dump(ideVariant)
              }
            }
          }

          NdkModuleModel.get(module)?.let { it ->
            dumpNdkModuleModel(it)
          }

          kotlinModels(module)?.let {
            dump(it)
          }

          kaptModels(module)?.let {
            dump(it)
          }
        }
      }
    }
  }
}

fun ProjectDumper.dumpAllVariantsSyncAndroidModuleModel(androidModuleModel: AndroidModuleModel, projectPath: String) {
  nest(File(projectPath), "PROJECT") {
    with(ideModelDumper(this)) {
      androidModuleModel.let { androidModuleModel ->
        dump(androidModuleModel.androidProject)
        // Dump all the fetched Ide variants.
        head("IdeVariants")
        nest {
          androidModuleModel.variants.forEach { ideVariant ->
            dump(ideVariant)
          }
        }
      }
    }
  }
}


private fun ideModelDumper(projectDumper: ProjectDumper) = with(projectDumper) {
  object {
    fun dump(ideAndroidModel: IdeAndroidProject) {
      prop("ModelVersion") { ideAndroidModel.modelVersion.replaceKnownPatterns() }
      prop("ProjectType") { ideAndroidModel.projectType.toString() }
      prop("CompileTarget") { ideAndroidModel.compileTarget.replaceCurrentSdkVersion() }
      prop("BuildFolder") { ideAndroidModel.buildFolder.path.toPrintablePath() }
      prop("ResourcePrefix") { ideAndroidModel.resourcePrefix }
      prop("buildToolsVersion") { ideAndroidModel.buildToolsVersion?.toPrintableString() }
      prop("IsBaseSplit") { ideAndroidModel.isBaseSplit.toString() }
      prop("GroupId") { ideAndroidModel.groupId }
      prop("Namespace") { ideAndroidModel.namespace }
      prop("TestNamespace") { ideAndroidModel.testNamespace }
      dump(ideAndroidModel.aaptOptions)
      dump(ideAndroidModel.lintOptions)
      dump(ideAndroidModel.javaCompileOptions)
      dump(ideAndroidModel.agpFlags)
      ideAndroidModel.variantNames?.forEach { prop("VariantNames") { it } }
      ideAndroidModel.flavorDimensions.forEach { prop("FlavorDimensions") { it } }
      ideAndroidModel.bootClasspath.forEach { prop("BootClassPath") { it.toPrintablePath().replaceCurrentSdkVersion() } }
      ideAndroidModel.dynamicFeatures.forEach { prop("DynamicFeatures") { it } }
      ideAndroidModel.viewBindingOptions?.let { dump(it) }
      ideAndroidModel.dependenciesInfo?.let { dump(it) }
      ideAndroidModel.lintChecksJars?.forEach { prop("lintChecksJars") { it.path.toPrintablePath() } }

      head("DefaultConfig")
      nest {
        dump(ideAndroidModel.defaultConfig)
      }
      if (ideAndroidModel.buildTypes.isNotEmpty()) {
        head("BuildTypes")
        nest {
          ideAndroidModel.buildTypes.forEach {
            dump(it)
          }
        }
      }
      if (ideAndroidModel.productFlavors.isNotEmpty()) {
        head("ProductFlavors")
        nest {
          ideAndroidModel.productFlavors.forEach {
            dump(it)
          }
        }
      }
      head("SigningConfigs")
      nest {
        ideAndroidModel.signingConfigs.forEach { dump(it) }
      }

      head("VariantBuildInformation")
      nest {
        ideAndroidModel.variantsBuildInformation.forEach { dump(it) }
      }
    }

    fun dump(ideVariant: IdeVariant) {
      head("IdeVariant")
      nest {
        prop("Name") { ideVariant.name }
        prop("BuildType") { ideVariant.buildType }
        prop("DisplayName") { ideVariant.displayName }
        prop("InstantAppCompatible") { ideVariant.instantAppCompatible.toString() }
        ideVariant.minSdkVersion.dump("MinSdkVersion")
        ideVariant.targetSdkVersion?.dump("TargetSdkVersion")
        prop("MaxSdkVersion") { ideVariant.maxSdkVersion?.toString()?.replaceCurrentSdkVersion() }
        prop("VersionCode") { ideVariant.versionCode?.toString() }
        prop("VersionNameSuffix") { ideVariant.versionNameSuffix }
        prop("VersionNameWithSuffix") { ideVariant.versionNameWithSuffix }
        prop("TestApplicationId") { ideVariant.testApplicationId }
        prop("DeprecatedPreMergedApplicationId") { ideVariant.deprecatedPreMergedApplicationId }
        ideVariant.proguardFiles.forEach { prop("ProguardFiles") { it.path.toPrintablePath() } }
        ideVariant.consumerProguardFiles.forEach { prop("ConsumerProguardFiles") { it.path.toPrintablePath() } }
        ideVariant.resourceConfigurations.forEach { prop("ResourceConfigurations") { it } }
        ideVariant.productFlavors.forEach { prop("ProductFlavors") { it } }
        prop("TestInstrumentationRunner") { ideVariant.testInstrumentationRunner }
        if (ideVariant.manifestPlaceholders.isNotEmpty()) {
          head("ManifestPlaceholders")
          nest {
            ideVariant.manifestPlaceholders.forEach { (key, value) ->
              prop(key) { value }
            }
          }
        }
        if (ideVariant.resValues.isNotEmpty()) {
          head("ResValues")
          nest {
            ideVariant.resValues.forEach { (key, value) ->
              prop(key) { "(${value.type}, ${value.name}, ${value.value})" }
            }
          }
        }
        if (ideVariant.testInstrumentationRunnerArguments.isNotEmpty()) {
          head("TestInstrumentationRunnerArguments")
          nest {
            ideVariant.testInstrumentationRunnerArguments.forEach { (key, value) ->
              prop(key) { value }
            }
          }
        }
        head("MainArtifact")
        nest {
          dump(ideVariant.mainArtifact)
        }
        ideVariant.androidTestArtifact?.let {
          head("AndroidTestArtifact")
          nest {
            dump(it)
          }
        }
        ideVariant.unitTestArtifact?.let {
          head("UnitTestArtifact")
          nest {
            dump(it)
          }
        }
        ideVariant.testFixturesArtifact?.let {
          head("TestFixturesArtifact")
          nest {
            dump(it)
          }
        }
        ideVariant.testedTargetVariants.forEach {
          head("TestedTargetVariants")
          nest {
            dump(it)
          }
        }
      }
    }

    private fun dump(ideAndroidArtifact: IdeAndroidArtifact) {
      dump(ideAndroidArtifact as IdeBaseArtifact) // dump the IdeBaseArtifact part first.
      prop("SigningConfigName") { ideAndroidArtifact.signingConfigName }
      prop("IsSigned") { ideAndroidArtifact.isSigned.toString() }
      prop("CodeShrinker") { ideAndroidArtifact.codeShrinker.toString() }
      dump(ideAndroidArtifact.buildInformation)
      ideAndroidArtifact.generatedResourceFolders.forEach { prop("GeneratedResourceFolders") { it.path.toPrintablePath() } }
      ideAndroidArtifact.additionalRuntimeApks.forEach { prop("AdditionalRuntimeApks") { it.path.toPrintablePath() } }
      ideAndroidArtifact.testOptions?.let { dump(it) }
      ideAndroidArtifact.abiFilters.forEach { prop("AbiFilters") { it } }
      ideAndroidArtifact.modelSyncFiles.forEach { dump(it) }
    }

    private fun dump(androidLibrary: IdeAndroidLibrary) {
      dump(androidLibrary as IdeLibrary)
      prop("Folder") { androidLibrary.folder?.path?.toPrintablePath() }
      prop("Manifest") { androidLibrary.manifest.toPrintablePath() }
      androidLibrary.compileJarFiles.forEach { prop("CompileJarFiles") { it.toPrintablePath() } }
      androidLibrary.runtimeJarFiles.forEach { prop("RuntimeJarFiles") { it.toPrintablePath() } }
      prop("ResFolder") { androidLibrary.resFolder.toPrintablePath() }
      prop("ResStaticLibrary") { androidLibrary.resStaticLibrary?.path?.toPrintablePath() }
      prop("AssetFolder") { androidLibrary.assetsFolder.toPrintablePath() }
      prop("JniFolder") { androidLibrary.jniFolder.toPrintablePath() }
      prop("AidlFolder") { androidLibrary.aidlFolder.toPrintablePath() }
      prop("RenderscriptFolder") { androidLibrary.renderscriptFolder.toPrintablePath() }
      prop("ProguardRules") { androidLibrary.proguardRules.toPrintablePath() }
      prop("ExternalAnnotations") { androidLibrary.externalAnnotations.toPrintablePath() }
      prop("PublicResources") { androidLibrary.publicResources.toPrintablePath() }
      prop("SymbolFile") { androidLibrary.symbolFile.toPrintablePath() }
    }

    private fun dump(ideLibrary: IdeLibrary) {
      if (ideLibrary is IdeArtifactLibrary) {
        prop("ArtifactAddress") {
          ideLibrary.artifactAddress
            .toPrintablePath()
            .let { if (it.endsWith(" [-]")) it.substring(0, it.indexOf(" [-]")) else it }
            .replaceKnownPatterns()
        }
      }
      prop("LintJars") { ideLibrary.lintJar?.toPrintablePath() }
      when (ideLibrary) {
        is IdeAndroidLibrary -> prop("IsProvided") { ideLibrary.isProvided.toString() }
        is IdeJavaLibrary -> prop("IsProvided") { ideLibrary.isProvided.toString() }
      }
      when (ideLibrary) {
        is IdeAndroidLibrary -> prop("Artifact") { ideLibrary.artifact.path.toPrintablePath() }
        is IdeJavaLibrary -> prop("Artifact") { ideLibrary.artifact.path.toPrintablePath() }
      }
    }

    private fun dump(javaLibrary: IdeJavaLibrary) {
      dump(javaLibrary as IdeLibrary)
    }

    private fun dump(moduleLibrary: IdeModuleLibrary) {
      dump(moduleLibrary as IdeLibrary)
      prop("ProjectPath") { moduleLibrary.projectPath }
      prop("Variant") { moduleLibrary.variant }
      prop("BuildId") { moduleLibrary.buildId?.toPrintablePath() }
    }

    private fun dump(ideBaseArtifact: IdeBaseArtifact) {
      prop("Name") { ideBaseArtifact.name.toString() }
      prop("CompileTaskName") { ideBaseArtifact.compileTaskName }
      prop("AssembleTaskName") { ideBaseArtifact.assembleTaskName }
      prop("ClassFolder") { ideBaseArtifact.classesFolder.path.toPrintablePath() }
      prop("IsTestArtifact") { ideBaseArtifact.isTestArtifact.toString() }
      ideBaseArtifact.ideSetupTaskNames.forEach { prop("IdeSetupTaskNames") { it } }
      ideBaseArtifact.generatedSourceFolders.map { it.path.toPrintablePath() }.sorted().forEach {
        prop("GeneratedSourceFolders") { it }
      }
      ideBaseArtifact.additionalClassesFolders.map { it.path.toPrintablePath() }.sorted().forEach {
        prop("AdditionalClassesFolders") { it }
      }
      ideBaseArtifact.variantSourceProvider?.let {
        head("VariantSourceProvider")
        nest { dump(it) }
      }
      ideBaseArtifact.multiFlavorSourceProvider?.let {
        head("MultiFlavorSourceProvider")
        nest { dump(it) }
      }
      head("Level2Dependencies")
      nest {
        dump(ideBaseArtifact.level2Dependencies)
      }
    }

    private fun dump(ideJavaArtifact: IdeJavaArtifact) {
      dump(ideJavaArtifact as IdeBaseArtifact)
      prop("MockablePlatformJar") { ideJavaArtifact.mockablePlatformJar?.path?.toPrintablePath() }
    }

    private fun dump(ideTestedTargetVariant: IdeTestedTargetVariant) {
      prop("TargetProjectPath") { ideTestedTargetVariant.targetProjectPath }
      prop("TargetVariant") { ideTestedTargetVariant.targetVariant }
    }

    private fun dump(ideDependencies: IdeDependencies) {
      if (ideDependencies.androidLibraries.isNotEmpty()) {
        head("AndroidLibraries")
        nest {
          ideDependencies.androidLibraries.sortedBy { it.name }.forEach {
            head("AndroidLibrary") { it.name }
            nest {
              dump(it)
            }
          }
        }
      }
      if (ideDependencies.javaLibraries.isNotEmpty()) {
        head("JavaLibraries")
        nest {
          ideDependencies.javaLibraries.sortedBy { it.name }.forEach {
            head("JavaLibrary") { it.name.replaceKnownPatterns() }
            nest {
              dump(it)
            }
          }
        }
      }
      if (ideDependencies.moduleDependencies.isNotEmpty()) {
        head("ModuleDependencies")
        nest {
          ideDependencies.moduleDependencies.sortedWith(compareBy<IdeModuleLibrary> { it.projectPath }.thenBy { it.buildId }).forEach {
            head("ModuleDependency")
            nest {
              dump(it)
            }
          }
        }
      }
      ideDependencies.runtimeOnlyClasses.forEach { prop("RuntimeOnlyClasses") { it.path.toPrintablePath() } }

    }

    private fun dump(ideProductFlavorContainer: IdeProductFlavorContainer) {
      head("ProductFlavor")
      nest {
        dump(ideProductFlavorContainer.productFlavor)
      }
      head("SourceProvider")
      nest {
        dump(ideProductFlavorContainer.sourceProvider)
      }
      head("ExtraSourceProviders")
      nest {
        ideProductFlavorContainer.extraSourceProviders.forEach {
          dump(it)
        }
      }
    }

    private fun dump(ideBaseConfig: IdeBaseConfig) {
      prop("Name") { ideBaseConfig.name }
      prop("ApplicationIdSuffix") { ideBaseConfig.applicationIdSuffix }
      prop("VersionNameSuffix") { ideBaseConfig.versionNameSuffix }
      prop("IsMultiDexEnabled") { ideBaseConfig.multiDexEnabled?.toString() }
      if (ideBaseConfig.resValues.isNotEmpty()) {
        head("ResValues")
        nest {
          ideBaseConfig.resValues.forEach { (key, value) ->
            prop(key) { "(${value.type}, ${value.name}, ${value.value})" }
          }
        }
      }

      ideBaseConfig.proguardFiles.forEach { prop("ProguardFiles") { it.path.toPrintablePath() } }
      ideBaseConfig.consumerProguardFiles.forEach { prop("ConsumerProguardFiles") { it.path.toPrintablePath() } }
      if (ideBaseConfig.manifestPlaceholders.isNotEmpty()) {
        head("ManifestPlaceholders")
        nest {
          ideBaseConfig.manifestPlaceholders.forEach { (key, value) ->
            prop(key) { value }
          }
        }
      }
    }

    private fun dump(ideProductFlavor: IdeProductFlavor) {
      dump(ideProductFlavor as IdeBaseConfig)
      prop("Dimension") { ideProductFlavor.dimension }
      prop("ApplicationId") { ideProductFlavor.applicationId }
      prop("VersionCode") { ideProductFlavor.versionCode?.toString() }
      prop("VersionName") { ideProductFlavor.versionName }
      prop("MaxSdkVersion") { ideProductFlavor.maxSdkVersion?.toString()?.replaceCurrentSdkVersion() }
      prop("TestApplicationId") { ideProductFlavor.testApplicationId }
      prop("TestInstrumentationRunner") { ideProductFlavor.testInstrumentationRunner }
      prop("TestHandleProfiling") { ideProductFlavor.testHandleProfiling?.toString() }
      prop("TestFunctionalTest") { ideProductFlavor.testFunctionalTest?.toString() }
      ideProductFlavor.resourceConfigurations.forEach { prop("resourceConfigurations") { it } }
      ideProductFlavor.minSdkVersion?.dump("MinSdkVersion")
      ideProductFlavor.targetSdkVersion?.dump("TargetSdkVersion")
      if (ideProductFlavor.testInstrumentationRunnerArguments.isNotEmpty()) {
        head("TestInstrumentationRunnerArguments")
        nest {
          ideProductFlavor.testInstrumentationRunnerArguments.forEach { (key, value) ->
            prop(key) { value }
          }
        }
      }
      ideProductFlavor.vectorDrawables?.let {
        head("VectorDrawables")
        nest {
          prop("UseSupportLibrary") { it.useSupportLibrary?.toString() }
        }
      }
    }

    private fun dump(ideSourceProvider: IdeSourceProvider?) {
      if (ideSourceProvider == null) return
      prop("Name") { ideSourceProvider.name }
      prop("Manifest") { ideSourceProvider.manifestFile.path.toPrintablePath() }
      ideSourceProvider.javaDirectories.forEach { prop("JavaDirectories") { it.path.toPrintablePath() } }
      ideSourceProvider.kotlinDirectories.forEach { prop("KotlinDirectories") { it.path.toPrintablePath() } }
      ideSourceProvider.resourcesDirectories.forEach { prop("ResourcesDirectories") { it.path.toPrintablePath() } }
      ideSourceProvider.aidlDirectories.forEach { prop("AidlDirectories") { it.path.toPrintablePath() } }
      ideSourceProvider.renderscriptDirectories.forEach { prop("RenderscriptDirectories") { it.path.toPrintablePath() } }
      ideSourceProvider.resDirectories.forEach { prop("ResDirectories") { it.path.toPrintablePath() } }
      ideSourceProvider.assetsDirectories.forEach { prop("AssetsDirectories") { it.path.toPrintablePath() } }
      ideSourceProvider.jniLibsDirectories.forEach { prop("JniLibsDirectories") { it.path.toPrintablePath() } }
      ideSourceProvider.shadersDirectories.forEach { prop("ShadersDirectories") { it.path.toPrintablePath() } }
      ideSourceProvider.mlModelsDirectories.forEach { prop("MlModelsDirectories") { it.path.toPrintablePath() } }
    }

    private fun dump(extraSourceProvider: IdeSourceProviderContainer) {
      head("ExtraSourceProvider")
      nest {
        prop("ArtifactName") { extraSourceProvider.artifactName }
        head("SourceProvider")
        nest {
          dump(extraSourceProvider.sourceProvider)
        }
      }
    }

    private fun dump(ideBuildTypeContainer: IdeBuildTypeContainer) {
      head("BuildType")
      nest {
        dump(ideBuildTypeContainer.buildType as IdeBaseConfig)
        prop("IsDebuggable") { ideBuildTypeContainer.buildType.isDebuggable.toString() }
        prop("IsJniDebuggable") { ideBuildTypeContainer.buildType.isJniDebuggable.toString() }
        prop("IsRenderscriptDebuggable") { ideBuildTypeContainer.buildType.isRenderscriptDebuggable.toString() }
        prop("RenderscriptOptimLevel") { ideBuildTypeContainer.buildType.renderscriptOptimLevel.toString() }
        prop("IsMinifyEnabled") { ideBuildTypeContainer.buildType.isMinifyEnabled.toString() }
        prop("IsZipAlignEnabled") { ideBuildTypeContainer.buildType.isZipAlignEnabled.toString() }
      }
      head("SourceProvider")
      nest {
        dump(ideBuildTypeContainer.sourceProvider)
      }
      ideBuildTypeContainer.extraSourceProviders.forEach {
        head("ExtraSourceProviders")
        nest {
          dump(it)
        }
      }
    }

    private fun dump(ideSigningConfig: IdeSigningConfig) {
      head("SigningConfig")
      nest {
        prop("Name") { ideSigningConfig.name }
        // TODO(karimai): determine if we can compare the file paths for storeFile or just the name ?
        prop("StoreFile") { ideSigningConfig.storeFile?.name }
        prop("StorePassword") { ideSigningConfig.storePassword }
        prop("KeyAlias") { ideSigningConfig.keyAlias }
      }
    }

    private fun dump(aaptOptions: IdeAaptOptions) {
      head("AaptOptions")
      nest {
        prop("NameSpacing") { aaptOptions.namespacing.toString() }
      }
    }

    private fun dump(lintOptions: IdeLintOptions) {
      head("LintOptions")
      nest {
        prop("BaselineFile") { lintOptions.baselineFile?.path?.toPrintablePath() }
        prop("LintConfig") { lintOptions.lintConfig?.path?.toPrintablePath() }
        prop("IsCheckTestSources") { lintOptions.isCheckTestSources.toString() }
        prop("IsCheckDependencies") { lintOptions.isCheckDependencies.toString() }
        prop("IsAbortOnError") { lintOptions.isAbortOnError.toString() }
        prop("IsAbsolutePaths") { lintOptions.isAbsolutePaths.toString() }
        prop("IsNoLines") { lintOptions.isNoLines.toString() }
        prop("IsQuiet") { lintOptions.isQuiet.toString() }
        prop("IsCheckAllWarnings") { lintOptions.isCheckAllWarnings.toString() }
        prop("IsIgnoreWarnings") { lintOptions.isIgnoreWarnings.toString() }
        prop("IsWarningsAsErrors") { lintOptions.isWarningsAsErrors.toString() }
        prop("IsIgnoreTestSources") { lintOptions.isIgnoreTestSources.toString() }
        prop("IsCheckGeneratedSources") { lintOptions.isCheckGeneratedSources.toString() }
        prop("IsCheckReleaseBuilds") { lintOptions.isCheckReleaseBuilds.toString() }
        prop("IsExplainIssues") { lintOptions.isExplainIssues.toString() }
        prop("IsShowAll") { lintOptions.isShowAll.toString() }
        prop("TextReport") { lintOptions.textReport.toString() }
        prop("TextOutput") { lintOptions.textOutput?.path?.toPrintablePath() }
        prop("HtmlReport") { lintOptions.htmlReport.toString() }
        prop("HtmlOutput") { lintOptions.htmlOutput?.path?.toPrintablePath() }
        prop("XmlReport") { lintOptions.xmlReport.toString() }
        prop("XmlOutput") { lintOptions.xmlOutput?.path?.toPrintablePath() }
        prop("SarifReport") { lintOptions.sarifReport.toString() }
        prop("SarifOutput") { lintOptions.sarifOutput?.path?.toPrintablePath() }
        lintOptions.disable.forEach { prop("- Disable") { it } }
        lintOptions.enable.forEach { prop("- Enable") { it } }
        lintOptions.check?.forEach { prop("- Check") { it } }
        if (lintOptions.severityOverrides.orEmpty().isNotEmpty()) {
          head("SeverityOverrides")
          nest {
            lintOptions.severityOverrides?.forEach { key, value ->
              prop(key) { value.toString() }
            }
          }
        }
      }
    }

    private fun dump(javaCompileOptions: IdeJavaCompileOptions) {
      head("JavaCompileOptions")
      nest {
        prop("Encoding") { javaCompileOptions.encoding }
        prop("SourceCompatibility") { javaCompileOptions.sourceCompatibility }
        prop("TargetCompatibility") { javaCompileOptions.targetCompatibility }
        prop("IsCoreLibraryDesugaringEnabled") { javaCompileOptions.isCoreLibraryDesugaringEnabled.toString() }
      }
    }

    private fun dump(viewBindingOptions: IdeViewBindingOptions) {
      head("ViewBindingOptions")
      nest {
        prop("Enabled") { viewBindingOptions.enabled.toString() }
      }
    }

    private fun dump(dependenciesInfo: IdeDependenciesInfo) {
      head("DependenciesInfo")
      nest {
        prop("IncludeInApk") { dependenciesInfo.includeInApk.toString() }
        prop("IncludeInBundle") { dependenciesInfo.includeInBundle.toString() }
      }
    }

    private fun dump(agpFlags: IdeAndroidGradlePluginProjectFlags) {
      head("AgpFlags")
      nest {
        prop("ApplicationRClassConstantIds") { agpFlags.applicationRClassConstantIds.toString() }
        prop("AestRClassConstantIds") { agpFlags.testRClassConstantIds.toString() }
        prop("TransitiveRClasses") { agpFlags.transitiveRClasses.toString() }
        prop("UsesCompose") { agpFlags.usesCompose.toString() }
        prop("MlModelBindingEnabled") { agpFlags.mlModelBindingEnabled.toString() }
      }
    }

    private fun dump(variantBuildInformation: IdeVariantBuildInformation) {
      head("VariantBuildInformation")
      nest {
        prop("VariantName") { variantBuildInformation.variantName }
        dump(variantBuildInformation.buildInformation)
      }
    }

    private fun dump(info: IdeBuildTasksAndOutputInformation) {
      head("BuildTasksAndOutputInformation")
      nest {
        prop("AssembleTaskName") { info.assembleTaskName }
        prop("AssembleTaskOutputListingFile") { info.assembleTaskOutputListingFile?.toPrintablePath() }
        prop("BundleTaskName") { info.bundleTaskName }
        prop("BundleTaskOutputListingFile") { info.bundleTaskOutputListingFile?.toPrintablePath() }
        prop("ApkFromBundleTaskName") { info.apkFromBundleTaskName }
        prop("ApkFromBundleTaskOutputListingFile") { info.apkFromBundleTaskOutputListingFile?.toPrintablePath() }
      }
    }

    private fun IdeApiVersion.dump(name: String) {
      head(name)
      nest {
        prop("ApiLevel") { apiLevel.toString().replaceCurrentSdkVersion() }
        prop("CodeName") { codename }
        prop("ApiString") { apiString.replaceCurrentSdkVersion() }
      }
    }

    private fun dump(testOptions: IdeTestOptions) {
      head("TestOptions")
      nest {
        prop("AnimationsDisabled") { testOptions.animationsDisabled.toString() }
        prop("Execution") { testOptions.execution?.toString() }
      }
    }

    private fun dump(modelSyncFile: IdeModelSyncFile) {
      head("ModelSyncFile")
      nest {
        prop("Type") { modelSyncFile.modelSyncType.toString() }
        prop("TaskName") { modelSyncFile.taskName }
        prop("File") { modelSyncFile.syncFile.path.toPrintablePath() }
      }
    }

    fun dump(kotlinGradleModel: KotlinGradleModel) {
      head("KotlinGradleModel")
      nest {
        prop("hasKotlinPlugin") { kotlinGradleModel.hasKotlinPlugin.takeIf { it }?.toString() }
        prop("coroutines") { kotlinGradleModel.coroutines }
        prop("platformPluginId") { kotlinGradleModel.platformPluginId }
        prop("implements") { kotlinGradleModel.implements.joinToString() }
        prop("kotlinTarget") { kotlinGradleModel.kotlinTarget }
        prop("gradleUserHome") { kotlinGradleModel.gradleUserHome.toPrintablePath() }
        kotlinGradleModel.kotlinTaskProperties.forEach { key, value ->
          head("kotlinTaskProperties") { key }
          nest {
            prop("incremental") { value.incremental?.toString() }
            prop("packagePrefix") { value.packagePrefix }
            value.pureKotlinSourceFolders?.forEach { prop("pureKotlinSourceFolders") { it.path.toPrintablePath() } }
            prop("pluginVersion") { value.pluginVersion?.replaceKnownPatterns() }
          }
        }
        kotlinGradleModel.compilerArgumentsBySourceSet.forEach { key, value ->
          head("compilerArgumentsBySourceSet") { key }
          nest {
            fun dumpArg(title: String, arg: String) {
              val (name, values) = when {
                !arg.contains('=') && !arg.contains(':') -> arg to sequenceOf<String>()
                arg.contains('=') -> arg.substringBefore('=', "") to arg.substringAfter('=', arg).splitToSequence(',')
                else -> "" to arg.splitToSequence(':')
              }
              if (name == "plugin:org.jetbrains.kotlin.android:configuration") return // Base64 encoded serialized format.
              head(title) { name.replaceKnownPaths() }
              nest {
                values.forEach {
                  prop("-") { it.toPrintablePath() }
                }
              }
            }

            value.currentArguments.forEach { dumpArg("currentArguments", it) }
            value.defaultArguments.forEach { dumpArg("defaultArguments", it) }
            value.dependencyClasspath.forEach { prop("dependencyClasspath") { it.toPrintablePath() } }
          }
        }
      }
    }

    fun dump(kaptGradleModel: KaptGradleModel) {
      if (!kaptGradleModel.isEnabled) return // Usually models are present for all modules with Kotlin but disabled.
      head("kaptGradleModel")
      nest {
        prop("buildDirectory") { kaptGradleModel.buildDirectory.path.toPrintablePath() }
        kaptGradleModel.sourceSets.forEach { sourceSet ->
          head("sourceSets") { sourceSet.sourceSetName }
          nest {
            prop("isTest") { sourceSet.isTest.takeIf { it }?.toString() }
            prop("generatedSourcesDir") { sourceSet.generatedSourcesDir.toPrintablePath() }
            prop("generatedClassesDir") { sourceSet.generatedClassesDir.toPrintablePath() }
            prop("generatedKotlinSourcesDir") { sourceSet.generatedKotlinSourcesDir.toPrintablePath() }
          }
        }
      }
    }
  }
}


class DumpProjectIdeModelAction : DumbAwareAction("Dump Project IDE Models") {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    val dumper = ProjectDumper()
    dumper.dumpAndroidIdeModel(project, { null }, { null })
    val dump = dumper.toString().trimIndent()
    val outputFile = File(File(project.basePath), sanitizeFileName(project.name) + ".project_ide_models_dump")
    outputFile.writeText(dump)
    FileEditorManager.getInstance(project).openEditor(OpenFileDescriptor(project, VfsUtil.findFileByIoFile(outputFile, true)!!), true)
    VfsUtil.markDirtyAndRefresh(true, false, false, outputFile)
    println("Dumped to: file://$outputFile")
  }
}
