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

import com.android.tools.idea.FileEditorUtil
import com.android.tools.idea.gradle.model.IdeAaptOptions
import com.android.tools.idea.gradle.model.IdeAndroidArtifact
import com.android.tools.idea.gradle.model.IdeAndroidGradlePluginProjectFlags
import com.android.tools.idea.gradle.model.IdeAndroidLibrary
import com.android.tools.idea.gradle.model.IdeAndroidProject
import com.android.tools.idea.gradle.model.IdeApiVersion
import com.android.tools.idea.gradle.model.IdeBaseArtifact
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
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.project.model.NdkModuleModel
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.util.io.sanitizeFileName
import java.io.File

fun ProjectDumper.dumpAndroidIdeModel(project: Project) {
  nest(File(project.basePath!!), "PROJECT") {
    ModuleManager.getInstance(project).modules.sortedBy { it.name }.forEach { module ->
      head("MODULE") { module.name }
      nest {
        AndroidModuleModel.get(module)?.let { it ->
          dump(it.androidProject)
          // Dump all the fetched Ide variants.
          head("IdeVariants")
            nest {
              it.variants.forEach { ideVariant -> dump(ideVariant)
              }
            }
        }
      }
      nest {
        NdkModuleModel.get(module)?.let { it ->
          dumpNdkModuleModel(it)
        }
      }
    }
  }
}

private fun ProjectDumper.dump(ideAndroidModel: IdeAndroidProject) {
  prop("ModelVersion") { ideAndroidModel.modelVersion }
  prop("ProjectType") { ideAndroidModel.projectType.toString() }
  prop("CompileTarget") { ideAndroidModel.compileTarget }
  prop("BuildFolder") { ideAndroidModel.buildFolder.path.toPrintablePath() }
  prop("ResourcePrefix") { ideAndroidModel.resourcePrefix }
  prop("buildToolsVersion") { ideAndroidModel.buildToolsVersion }
  prop("NdkVersion") { ideAndroidModel.ndkVersion }
  prop("IsBaseSplit") { ideAndroidModel.isBaseSplit.toString() }
  prop("GroupId") { ideAndroidModel.groupId }
  dump(ideAndroidModel.aaptOptions)
  dump(ideAndroidModel.lintOptions)
  dump(ideAndroidModel.javaCompileOptions)
  dump(ideAndroidModel.agpFlags)
  ideAndroidModel.variantNames?.forEach { prop("VariantNames") {it} }
  ideAndroidModel.flavorDimensions.forEach { prop("FlavorDimensions") { it } }
  ideAndroidModel.bootClasspath.forEach { prop("BootClassPath") { it.toPrintablePath() } }
  ideAndroidModel.dynamicFeatures.forEach { prop("DynamicFeatures") { it } }
  ideAndroidModel.viewBindingOptions?.let { dump(it) }
  ideAndroidModel.dependenciesInfo?.let { dump(it) }
  ideAndroidModel.lintRuleJars?.forEach { prop("LintRuleJars") { it.path.toPrintablePath() } }
  head("DefaultConfig")
    nest {
      this.dump(ideAndroidModel.defaultConfig)
    }
  if (ideAndroidModel.buildTypes.isNotEmpty()) {
    head("BuildTypes")
    nest {
      ideAndroidModel.buildTypes.forEach {
        this.dump(it)
      }
    }
  }
  if (ideAndroidModel.productFlavors.isNotEmpty()) {
    head("ProductFlavors")
    nest {
      ideAndroidModel.productFlavors.forEach {
        this.dump(it)
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

private fun ProjectDumper.dump(ideVariant: IdeVariant) {
  head("IdeVariant")
    nest {
      prop("Name") { ideVariant.name }
      prop("BuildType") { ideVariant.buildType }
      prop("DisplayName") { ideVariant.displayName }
      prop("InstantAppCompatible") { ideVariant.instantAppCompatible.toString() }
      prop("MinSdkVersion") { ideVariant.minSdkVersion?.toString() }
      prop("TargetSdkVersion") { ideVariant.targetSdkVersion?.toString() }
      prop("MaxSdkVersion") { ideVariant.maxSdkVersion?.toString() }
      prop("TestApplicationId") { ideVariant.testApplicationId }
      prop("DeprecatedPreMergedApplicationId") { ideVariant.deprecatedPreMergedApplicationId }
      ideVariant.resourceConfigurations.forEach { prop("ResourceConfigurations") { it } }
      ideVariant.productFlavors.forEach { prop("ProductFlavors") { it } }
      prop("TestInstrumentationRunner") { ideVariant.testInstrumentationRunner }
      if (ideVariant.testInstrumentationRunnerArguments.isNotEmpty()) {
        head("TestInstrumentationRunnerArguments")
        nest {
          ideVariant.testInstrumentationRunnerArguments.forEach { (key, value) -> prop(key) { value }
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
      ideVariant.testedTargetVariants.forEach {
        head("TestedTargetVariants")
          nest {
            dump(it)
          }
      }
    }
}

private fun ProjectDumper.dump(ideAndroidArtifact: IdeAndroidArtifact) {
  dump(ideAndroidArtifact as IdeBaseArtifact) // dump the IdeBaseArtifact part first.
  prop("SigningConfigName") { ideAndroidArtifact.signingConfigName }
  prop("IsSigned") { ideAndroidArtifact.isSigned.toString() }
  prop("CodeShrinker") { ideAndroidArtifact.codeShrinker.toString() }
  dump(ideAndroidArtifact.buildInformation)
  ideAndroidArtifact.generatedResourceFolders.forEach { prop("GeneratedResourceFolders") { it.path.toPrintablePath() } }
  ideAndroidArtifact.additionalRuntimeApks.forEach { prop("AdditionalRuntimeApks") { it.path.toPrintablePath() } }
  ideAndroidArtifact.testOptions?.let { dump(it) }
  ideAndroidArtifact.abiFilters.forEach { prop("AbiFilters") { it } }
}

private fun ProjectDumper.dump(androidLibrary: IdeAndroidLibrary) {
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
  prop("RenderscriptFolder") {androidLibrary.renderscriptFolder.toPrintablePath() }
  prop("ProguardRules") { androidLibrary.proguardRules.toPrintablePath() }
  prop("ExternalAnnotations") { androidLibrary.externalAnnotations.toPrintablePath() }
  prop("PublicResources") { androidLibrary.publicResources.toPrintablePath() }
  prop("SymbolFile") { androidLibrary.symbolFile.toPrintablePath() }
}

private fun ProjectDumper.dump(ideLibrary: IdeLibrary) {
  prop("ArtifactAddress") { ideLibrary.artifactAddress.toPrintablePath() }
  prop("LintJars") { ideLibrary.lintJar?.toPrintablePath() }
  prop("IsProvided") { ideLibrary.isProvided.toString() }
  if (ideLibrary !is IdeModuleLibrary) prop("Artifact") { ideLibrary.artifact.path.toPrintablePath() }
}

private fun ProjectDumper.dump(javaLibrary: IdeJavaLibrary) {
  dump(javaLibrary as IdeLibrary)
}

private fun ProjectDumper.dump(moduleLibrary: IdeModuleLibrary) {
  dump(moduleLibrary as IdeLibrary)
  prop("ProjectPath") { moduleLibrary.projectPath }
  prop("Variant") { moduleLibrary.variant }
  prop("BuildId") { moduleLibrary.buildId?.toPrintablePath() }
}

private fun ProjectDumper.dump(ideBaseArtifact: IdeBaseArtifact) {
  prop("Name") { ideBaseArtifact.name.toString() }
  prop("CompileTaskName") { ideBaseArtifact.compileTaskName }
  prop("AssembleTaskName") { ideBaseArtifact.assembleTaskName }
  prop("ClassFolder") { ideBaseArtifact.classesFolder.path.toPrintablePath() }
  prop("JavaResourcesFolder") { ideBaseArtifact.javaResourcesFolder?.path?.toPrintablePath() }
  prop("IsTestArtifact") { ideBaseArtifact.isTestArtifact.toString() }
  ideBaseArtifact.ideSetupTaskNames.forEach { prop("IdeSetupTaskNames") { it } }
  ideBaseArtifact.generatedSourceFolders.forEach { prop("GeneratedSourceFolders") { it.path.toPrintablePath() } }
  ideBaseArtifact.additionalClassesFolders.sorted().forEach { prop("AdditionalClassesFolders") { it.path.toPrintablePath() } }
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

private fun ProjectDumper.dump(ideJavaArtifact: IdeJavaArtifact) {
  dump(ideJavaArtifact as IdeBaseArtifact)
  prop("MockablePlatformJar") { ideJavaArtifact.mockablePlatformJar?.path?.toPrintablePath() }
}

private fun ProjectDumper.dump(ideTestedTargetVariant: IdeTestedTargetVariant) {
  prop("TargetProjectPath") { ideTestedTargetVariant.targetProjectPath }
  prop("TargetVariant") { ideTestedTargetVariant.targetVariant }
}

private fun ProjectDumper.dump(ideDependencies: IdeDependencies) {
  if (ideDependencies.androidLibraries.isNotEmpty()) {
    head("AndroidLibraries")
    nest {
      ideDependencies.androidLibraries.forEach {
        head("AndroidLibrary")
        nest {
          dump(it)
        }
      }
    }
  }
  if (ideDependencies.javaLibraries.isNotEmpty()) {
    head("JavaLibraries")
    nest {
      ideDependencies.javaLibraries.forEach {
        head("JavaLibrary")
        nest {
          dump(it)
        }
      }
    }
  }
  if (ideDependencies.moduleDependencies.isNotEmpty()) {
    head("ModuleDependencies")
    nest {
      ideDependencies.moduleDependencies.forEach {
        head("ModuleDependency")
        nest {
          dump(it)
        }
      }
    }
  }
  ideDependencies.runtimeOnlyClasses.forEach { prop("RuntimeOnlyClasses") { it.path.toPrintablePath() } }

}

private fun ProjectDumper.dump(ideProductFlavorContainer: IdeProductFlavorContainer) {
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

private fun ProjectDumper.dump(ideProductFlavor: IdeProductFlavor) {
  prop("Dimension") { ideProductFlavor.dimension }
  prop("ApplicationId") { ideProductFlavor.applicationId }
  prop("VersionCode") { ideProductFlavor.versionCode?.toString() }
  prop("VersionName") { ideProductFlavor.versionName }
  prop("MaxSdkVersion") { ideProductFlavor.maxSdkVersion?.toString() }
  prop("TestApplicationId") { ideProductFlavor.testApplicationId }
  prop("TestInstrumentationRunner") { ideProductFlavor.testInstrumentationRunner }
  prop("TestHandleProfiling") { ideProductFlavor.testHandleProfiling?.toString() }
  prop("TestFunctionalTest") { ideProductFlavor.testFunctionalTest?.toString() }
  ideProductFlavor.resourceConfigurations.forEach { prop("resourceConfigurations") { it } }
  ideProductFlavor.minSdkVersion?.let {
    head("MinSdkVersion")
      nest {
        dump(it)
      }
  }
  ideProductFlavor.targetSdkVersion?.let {
    head("TargetSdkVersion")
      nest {
        dump(it)
      }
  }
  if (ideProductFlavor.testInstrumentationRunnerArguments.isNotEmpty()) {
    head("TestInstrumentationRunnerArguments")
      nest {
        ideProductFlavor.testInstrumentationRunnerArguments.forEach { (key, value) -> prop(key) { value }
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

private fun ProjectDumper.dump(ideSourceProvider: IdeSourceProvider) {
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

private fun ProjectDumper.dump(extraSourceProvider: IdeSourceProviderContainer) {
  head("ExtraSourceProvider")
    nest {
      prop("ArtifactName") { extraSourceProvider.artifactName }
      head("SourceProvider")
        nest {
          dump(extraSourceProvider.sourceProvider)
        }
    }
}

private fun ProjectDumper.dump(ideBuildTypeContainer: IdeBuildTypeContainer) {
  head("BuildType")
    nest {
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

private fun ProjectDumper.dump(ideSigningConfig: IdeSigningConfig) {
  head("SigningConfig")
    nest {
      prop("Name") { ideSigningConfig.name }
      // TODO(karimai): determine if we can compare the file paths for storeFile or just the name ?
      prop("StoreFile") { ideSigningConfig.storeFile?.name }
      prop("StorePassword") { ideSigningConfig.storePassword }
      prop("KeyAlias") { ideSigningConfig.keyAlias }
    }
}

private fun ProjectDumper.dump(aaptOptions: IdeAaptOptions) {
  head("AaptOptions")
    nest {
      prop("NameSpacing") { aaptOptions.namespacing.toString() }
    }
}

private fun ProjectDumper.dump(lintOptions: IdeLintOptions) {
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
            lintOptions.severityOverrides?.forEach { key, value -> prop(key) { value.toString() }
            }
          }
      }
    }
}

private fun ProjectDumper.dump(javaCompileOptions: IdeJavaCompileOptions) {
  head("JavaCompileOptions")
    nest {
      prop("Encoding") { javaCompileOptions.encoding }
      prop("SourceCompatibility") { javaCompileOptions.sourceCompatibility }
      prop("TargetCompatibility") { javaCompileOptions.targetCompatibility }
      prop("IsCoreLibraryDesugaringEnabled") { javaCompileOptions.isCoreLibraryDesugaringEnabled.toString() }
    }
}

private fun ProjectDumper.dump(viewBindingOptions: IdeViewBindingOptions) {
  head("ViewBindingOptions")
    nest {
      prop("Enabled") { viewBindingOptions.enabled.toString() }
    }
}

private fun ProjectDumper.dump(dependenciesInfo: IdeDependenciesInfo) {
  head("DependenciesInfo")
    nest {
      prop("IncludeInApk") { dependenciesInfo.includeInApk.toString() }
      prop("IncludeInBundle") { dependenciesInfo.includeInBundle.toString() }
    }
}

private fun ProjectDumper.dump(agpFlags: IdeAndroidGradlePluginProjectFlags) {
  head("AgpFlags")
    nest {
      prop("ApplicationRClassConstantIds") { agpFlags.applicationRClassConstantIds.toString() }
      prop("AestRClassConstantIds") { agpFlags.testRClassConstantIds.toString() }
      prop("TransitiveRClasses") { agpFlags.transitiveRClasses.toString() }
      prop("UsesCompose") { agpFlags.usesCompose.toString() }
      prop("MlModelBindingEnabled") { agpFlags.mlModelBindingEnabled.toString() }
    }
}

private fun ProjectDumper.dump(variantBuildInformation: IdeVariantBuildInformation) {
  head("VariantBuildInformation")
    nest {
      prop("VariantName") { variantBuildInformation.variantName }
      dump(variantBuildInformation.buildInformation)
    }
}

private fun ProjectDumper.dump(info: IdeBuildTasksAndOutputInformation) {
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

private fun ProjectDumper.dump(apiVersion: IdeApiVersion) {
  prop("ApiLevel") { apiVersion.apiLevel.toString() }
  prop("CodeName") { apiVersion.codename }
  prop("ApiString") { apiVersion.apiString }
}

private fun ProjectDumper.dump(testOptions: IdeTestOptions) {
  head("TestOptions")
    nest {
      prop("AnimationsDisabled") { testOptions.animationsDisabled.toString() }
      prop("Execution") { testOptions.execution?.toString() }
    }
}

class DumpProjectIdeModelAction : DumbAwareAction("Dump Project IDE Models") {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    val dumper = ProjectDumper()
    dumper.dumpAndroidIdeModel(project)
    val dump = dumper.toString().trimIndent()
    val outputFile = File(File(project.basePath), sanitizeFileName(project.name) + ".project_ide_models_dump")
    outputFile.writeText(dump)
    FileEditorManager.getInstance(project).openEditor(OpenFileDescriptor(project, VfsUtil.findFileByIoFile(outputFile, true)!!), true)
    VfsUtil.markDirtyAndRefresh(true, false, false, outputFile)
    println("Dumped to: file://$outputFile")
  }
}
