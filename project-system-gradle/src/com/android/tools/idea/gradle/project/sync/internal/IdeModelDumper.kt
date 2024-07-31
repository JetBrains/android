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

import com.android.sdklib.AndroidVersion
import com.android.tools.idea.gradle.model.IdeAaptOptions
import com.android.tools.idea.gradle.model.IdeAndroidArtifact
import com.android.tools.idea.gradle.model.IdeAndroidGradlePluginProjectFlags
import com.android.tools.idea.gradle.model.IdeAndroidLibrary
import com.android.tools.idea.gradle.model.IdeAndroidProject
import com.android.tools.idea.gradle.model.IdeApiVersion
import com.android.tools.idea.gradle.model.IdeArtifactLibrary
import com.android.tools.idea.gradle.model.IdeArtifactName.Companion.toPrintableName
import com.android.tools.idea.gradle.model.IdeBaseArtifact
import com.android.tools.idea.gradle.model.IdeBaseConfig
import com.android.tools.idea.gradle.model.IdeBasicVariant
import com.android.tools.idea.gradle.model.IdeBuildTasksAndOutputInformation
import com.android.tools.idea.gradle.model.IdeBuildTypeContainer
import com.android.tools.idea.gradle.model.IdeCompositeBuildMap
import com.android.tools.idea.gradle.model.IdeDependencies
import com.android.tools.idea.gradle.model.IdeDependenciesInfo
import com.android.tools.idea.gradle.model.IdeExtraSourceProvider
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
import com.android.tools.idea.gradle.model.IdeUnknownLibrary
import com.android.tools.idea.gradle.model.IdeVariant
import com.android.tools.idea.gradle.model.IdeVariantBuildInformation
import com.android.tools.idea.gradle.model.IdeViewBindingOptions
import com.android.tools.idea.gradle.model.impl.IdeResolvedLibraryTable
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.project.model.GradleModuleModel
import com.android.tools.idea.gradle.project.model.NdkModuleModel
import com.android.tools.idea.gradle.project.sync.idea.data.DataNodeCaches
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys
import com.android.tools.idea.model.StudioAndroidModuleInfo
import com.android.tools.idea.projectsystem.gradle.GradleHolderProjectPath
import com.android.tools.idea.projectsystem.gradle.resolveIn
import com.android.tools.idea.projectsystem.isHolderModule
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.io.sanitizeFileName
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.parseCommandLineArguments
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinDependency
import org.jetbrains.kotlin.idea.gradleTooling.KotlinGradleModel
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModel
import org.jetbrains.kotlin.idea.gradleTooling.model.kapt.KaptGradleModel
import org.jetbrains.kotlin.idea.projectModel.KotlinCompilation
import org.jetbrains.kotlin.idea.projectModel.KotlinTaskProperties
import org.jetbrains.plugins.gradle.model.DefaultExternalSourceSet
import org.jetbrains.plugins.gradle.model.ExternalProject
import java.io.File

fun ProjectDumper.dumpAndroidIdeModel(
  project: Project,
  kotlinModels: (Module) -> KotlinGradleModel?,
  kaptModels: (Module) -> KaptGradleModel?,
  mppModels: (Module) -> KotlinMPPGradleModel?,
  externalProjects: (Module) -> ExternalProject?
) {
  val projectRoot = File(project.basePath!!)
  nest(projectRoot, "PROJECT") {
    with(ideModelDumper(this)) {
      // Android Studio projects always have just one Gradle root, and thus we dump the composite build structure of the root project of a
      // build located at the root of the IDE project.
      GradleHolderProjectPath(projectRoot.canonicalPath, ":")
        .resolveIn(project)
        ?.let { dump(it) }

      dumpLibraryTable(project)

      ModuleManager.getInstance(project).modules.sortedBy { it.name }.forEach { module ->
        head("MODULE") { module.name }
        nest {
          GradleFacet.getInstance(module)?.gradleModuleModel?.let {
            // Skip all but holders to prevent needless spam in the snapshots. All modules
            // point to the same facet.
            if (!module.isHolderModule()) return@let
            dump(it)
          }
          GradleAndroidModel.get(module)?.let { it ->
            // Skip all but holders to prevent needless spam in the snapshots. All modules
            // point to the same facet.
            if (!module.isHolderModule()) return@let
            head("CurrentVariantReportedVersions")
            nest {
              StudioAndroidModuleInfo.getInstance(module)?.minSdkVersion?.dump("minSdk")
              StudioAndroidModuleInfo.getInstance(module)?.runtimeMinSdkVersion?.get()?.dump("runtimeMinSdk")
              StudioAndroidModuleInfo.getInstance(module)?.targetSdkVersion?.dump("targetSdk")
            }
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

          mppModels(module)?.let {
            dump(it)
          }

          externalProjects(module)?.let {
            dump(it)
          }
        }
      }
    }
  }
}

fun ProjectDumper.dumpAllVariantsSyncAndroidModuleModel(gradleAndroidModel: GradleAndroidModel, projectPath: String) {
  nest(File(projectPath), "PROJECT") {
    with(ideModelDumper(this)) {
      gradleAndroidModel.let { gradleAndroidModel ->
        dump(gradleAndroidModel.androidProject)
        dumpLibraryTable(gradleAndroidModel.project)
        // Dump all the fetched Ide variants.
        head("IdeVariants")
        nest {
          gradleAndroidModel.variants.forEach { ideVariant ->
            dump(ideVariant)
          }
        }
      }
    }
  }
}

private val jbModelDumpers = listOf(
  SpecializedDumper(property = CommonCompilerArguments::pluginOptions),
  SpecializedDumper<IdeaKotlinDependency> { dependency ->
    prop(propertyName, dependency.coordinates.toString())
  },
  SpecializedDumper(property = KotlinCompilation::compilerArguments) { _, compilerArguments ->
    prop(propertyName, parseCommandLineArguments<K2JVMCompilerArguments>(compilerArguments))
  },
  SpecializedDumper(property = KotlinGradleModel::compilerArgumentsBySourceSet) { _, compilerArgumentsBySourceSet ->
    head(propertyName)
    nest {
      compilerArgumentsBySourceSet.forEach { (sourceSet, compilerArguments) ->
        head(sourceSet)
        nest {
          prop("compilerArguments", parseCommandLineArguments<K2JVMCompilerArguments>(compilerArguments))
        }
      }
    }
  },
  SpecializedDumper<DefaultExternalSourceSet> { externalSourceSet ->
    head(propertyName)
    nest {
      prop("sourceCompatibility", externalSourceSet.sourceCompatibility)
      prop("targetCompatibility", externalSourceSet.targetCompatibility)
      prop("jdkInstallationPath", externalSourceSet.javaToolchainHome)
      prop("artifacts", externalSourceSet.artifacts)
      prop("dependencies", externalSourceSet.dependencies)
      prop("sources", externalSourceSet.sources.toSortedMap())
    }
  },
  SpecializedDumper(property = KotlinMPPGradleModel::dependencies) { holder, dependencies ->
    head(propertyName)
    nest {
      holder.sourceSetsByName.keys.forEach { sourceSet ->
        head(sourceSet)
        nest {
          prop("dependency", dependencies[sourceSet].sortedBy { it.coordinates.toString() })
        }
      }
    }
  },
  // Do nothing as it is a machine specific path to `~/.konan` directory, where `~` is the true user home path rather than the one used
  // in tests.
  SpecializedDumper(property = KotlinMPPGradleModel::kotlinNativeHome),
  SpecializedDumper(property = KotlinTaskProperties::pluginVersion) { _, _ ->
    // We do not have access to `TestUtils.KOTLIN_VERSION_FOR_TESTS` here. Remove the property.
    prop(propertyName, "<CUT>")
  },
  SpecializedDumper(property = KotlinMPPGradleModel::kotlinGradlePluginVersion) { _, kgpVersion ->
    head(propertyName)
    nest {
      prop("versionString", kgpVersion.versionString.replaceKotlinVersionForTests())
    }
  },
)

const val KOTLIN_VERSION_FOR_TESTS = "2.0.20-Beta2"

fun String.replaceKotlinVersionForTests(): String = replace(KOTLIN_VERSION_FOR_TESTS, "<KOTLIN_VERSION_FOR_TESTS>")

private fun ideModelDumper(projectDumper: ProjectDumper) = with(projectDumper) {
  val modelDumper = ModelDumper(jbModelDumpers)
  object {
    fun dump(ideAndroidModel: IdeAndroidProject) {
      prop("RootBuildId") { ideAndroidModel.projectPath.rootBuildId.path.toPrintablePath() }
      prop("BuildId") { ideAndroidModel.projectPath.buildId.path.toPrintablePath() }
      prop("ProjectPath") { ideAndroidModel.projectPath.projectPath }
      prop("ModelVersion") { ideAndroidModel.agpVersion.replaceKnownPatterns() }
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
      ideAndroidModel.basicVariants.forEach { dump(it) }
      ideAndroidModel.flavorDimensions.forEach { prop("FlavorDimensions") { it } }
      ideAndroidModel.bootClasspath.forEach { prop("BootClassPath") { it.toPrintablePath().replaceCurrentSdkVersion() } }
      ideAndroidModel.dynamicFeatures.forEach { prop("DynamicFeatures") { it } }
      prop("BaseFeature") { ideAndroidModel.baseFeature }
      ideAndroidModel.viewBindingOptions?.let { dump(it) }
      ideAndroidModel.dependenciesInfo?.let { dump(it) }
      ideAndroidModel.lintChecksJars?.forEach { prop("lintChecksJars") { it.path.toPrintablePath() } }

      ideAndroidModel.multiVariantData?.defaultConfig?.let { defaultConfig ->
        head("DefaultConfig")
        nest {
          head("ProductFlavor")
          nest {
            dump(defaultConfig)
          }
          dump(ideAndroidModel.defaultSourceProvider)
        }
      } ?: run {
        head("DefaultSourceProvider")
        nest {
          dump(ideAndroidModel.defaultSourceProvider)
        }
      }
      ideAndroidModel.multiVariantData?.buildTypes?.takeIf { it.isNotEmpty() }?.let { buildTypes ->
        head("BuildTypes")
        nest {
          buildTypes.forEach {
            dump(it)
          }
        }
      }
      ideAndroidModel.multiVariantData?.productFlavors?.takeIf { it.isNotEmpty() }?.let { productFlavors ->
        head("ProductFlavors")
        nest {
          productFlavors.forEach {
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

    fun dump(ideBasicVariant: IdeBasicVariant) {
      head("- basicVariant:") { ideBasicVariant.name }
      nest {
        prop("applicationId") { ideBasicVariant.applicationId }
        prop("testApplicationId") { ideBasicVariant.testApplicationId }
        prop("buildType") { ideBasicVariant.buildType }
      }
    }

    fun dump(ideVariant: IdeVariant) {
      fun String.toPrintableArtifactName() = "${this}Artifact"

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
        prop("DeprecatedPreMergedTestApplicationId") { ideVariant.deprecatedPreMergedTestApplicationId }
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
        ideVariant.deviceTestArtifacts.forEach {
          head("${it.name.toPrintableName()}Artifact")
          nest {
            dump(it)
          }
        }
        ideVariant.hostTestArtifacts.forEach {
          head("${it.name.toPrintableName()}Artifact")
          nest {
            dump(it)
          }
        }
        ideVariant.testFixturesArtifact?.let {
          head("${it.name.toPrintableName()}Artifact")
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

    fun dumpLibraryTable(project: Project) {
      // Dump library table - this allows us to just use artifact addresses while dumping the dependency graphs for each artifact
      // It also centralizes all library information in one place.
      DataNodeCaches.getInstance(project).cachedProjectData?.let { projectData ->
        val libraryTable = ExternalSystemApiUtil.find(projectData, AndroidProjectKeys.IDE_LIBRARY_TABLE)
        head("LIBRARY_TABLE")
        nest {
          libraryTable?.data?.let {
            dump(it)
          }
        }
      }
    }

    fun dump(libraryTable: IdeResolvedLibraryTable) {
      val libraryComparator = compareBy<IdeLibrary?> { it?.toLibraryType() }.thenBy { it?.toDisplayString() }
      libraryTable.libraries
        .map { it.sortedWith (libraryComparator) } // sort each list first
        .sortedWith(compareBy(libraryComparator) { it.firstOrNull() } ) // then compare the min elements of lists
        .forEach { library ->
           modelDumper.dumpModel(projectDumper, "library", library)
        }
    }

    fun dump(compositeBuildMap: IdeCompositeBuildMap) {
      modelDumper.dumpModel(projectDumper, "CompositeBuildMap", compositeBuildMap)
    }

    private fun dump(ideAndroidArtifact: IdeAndroidArtifact) {
      dump(ideAndroidArtifact as IdeBaseArtifact) // dump the IdeBaseArtifact part first.
      prop("ApplicationId") { ideAndroidArtifact.applicationId }
      prop("SigningConfigName") { ideAndroidArtifact.signingConfigName }
      prop("IsSigned") { ideAndroidArtifact.isSigned.toString() }
      prop("CodeShrinker") { ideAndroidArtifact.codeShrinker.toString() }
      dump(ideAndroidArtifact.buildInformation)
      ideAndroidArtifact.generatedResourceFolders.forEach { prop("GeneratedResourceFolders") { it.path.toPrintablePath() } }
      ideAndroidArtifact.generatedAssetFolders.forEach { prop("GeneratedAssetFolders") { it.path.toPrintablePath() } }
      ideAndroidArtifact.desugaredMethodsFiles.forEach { prop("DesugaredMethodFiles") { it.path.toPrintablePath() } }
      ideAndroidArtifact.additionalRuntimeApks.forEach { prop("AdditionalRuntimeApks") { it.path.toPrintablePath() } }
      ideAndroidArtifact.testOptions?.let { dump(it) }
      ideAndroidArtifact.abiFilters.forEach { prop("AbiFilters") { it } }
    }

    private fun dump(property: String, ideDependencies: IdeDependencies) {

      head(property)
      nest {
        ideDependencies.unresolvedDependencies.forEach { dependency ->
          ideDependencies.resolver.resolve(dependency).forEach {
            prop(it.toLibraryType()) { it.toDisplayString() }
          }
          nest {
            // All the dependencies are included in unresolvedDependencies so we only need to dump the first level of children
            dependency.dependencies?.map { ideDependencies.lookup(it) }?.forEach { nestedDependency ->
              ideDependencies.resolver.resolve(nestedDependency).forEach { lib ->
                prop(lib.toLibraryType()) { lib.toDisplayString() }
              }
            }
          }
        }
      }
    }

    private fun dump(ideBaseArtifact: IdeBaseArtifact) {
      prop("Name") { ideBaseArtifact.name.toString() }
      prop("CompileTaskName") { ideBaseArtifact.compileTaskName }
      prop("AssembleTaskName") { ideBaseArtifact.assembleTaskName }
      prop("IsTestArtifact") { ideBaseArtifact.isTestArtifact.toString() }
      ideBaseArtifact.ideSetupTaskNames.forEach { prop("IdeSetupTaskNames") { it } }
      ideBaseArtifact.generatedSourceFolders.map { it.path.toPrintablePath() }.sorted().forEach {
        prop("GeneratedSourceFolders") { it }
      }
      ideBaseArtifact.classesFolder.map { it.path.toPrintablePath() }.sorted().forEach {
        prop("ClassesFolder") { it }
      }
      ideBaseArtifact.generatedClassPaths.map { it.value.toPrintablePath() }.sorted().forEach {
        prop("GeneratedClassPaths") { it }
      }
      ideBaseArtifact.variantSourceProvider?.let {
        head("VariantSourceProvider")
        nest { dump(it) }
      }
      ideBaseArtifact.multiFlavorSourceProvider?.let {
        head("MultiFlavorSourceProvider")
        nest { dump(it) }
      }
      head("Dependencies")
      nest {
        dump("compileClasspath", ideBaseArtifact.compileClasspath)
        dump("runtimeClasspath", ideBaseArtifact.runtimeClasspath)
      }
      val runtimeNames = ideBaseArtifact.runtimeClasspath.libraries.filterIsInstance<IdeArtifactLibrary>().map { it.name }.toSet()
      val compileTimeNames = ideBaseArtifact.compileClasspath.libraries.filterIsInstance<IdeArtifactLibrary>().map { it.name }.toSet()
      val providedDependencies = ideBaseArtifact.compileClasspath.libraries.filterIsInstance<IdeArtifactLibrary>()
        .filter { it.name !in runtimeNames }
      if (providedDependencies.isNotEmpty()) {
        head("ProvidedDependencies")
        nest {
          providedDependencies
            .sortedBy { it.name }
            .forEach {
              prop("- provided") { it.name.replaceKnownPatterns().replaceKnownPaths() }
            }
        }
      }
      val runtimeOnlyClasses =
        ideBaseArtifact.runtimeClasspath.libraries
          .filterIsInstance<IdeArtifactLibrary>()
          .filter { it.name !in compileTimeNames }
          .flatMap {
            when (it) {
              is IdeAndroidLibrary -> it.runtimeJarFiles
              is IdeJavaLibrary -> listOf(it.artifact)
              else -> emptyList()
            }
          }
          .distinct()
      if (runtimeOnlyClasses.isNotEmpty()) {
        head("RuntimeOnlyClasses")
        nest {
          runtimeOnlyClasses
            .map { it.toPrintablePath() }
            .sorted()
            .forEach {
              prop("- class") { it }
            }
        }
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

    private fun dump(ideSourceProviderContainer: IdeSourceProviderContainer) {
      head("SourceProvider")
      nest {
        dump(ideSourceProviderContainer.sourceProvider)
      }
      head("ExtraSourceProviders")
      nest {
        ideSourceProviderContainer.extraSourceProviders.forEach {
          dump(it)
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
      ideSourceProvider.customSourceDirectories.forEach {
        head("CustomSourceDirectories")
        nest {
          prop("SourceTypeName") { it.sourceTypeName }
          prop("Directory") { it.directory.path.toPrintablePath() }
        }
      }
      ideSourceProvider.baselineProfileDirectories.forEach { prop("BaselineProfileDirectories") { it.path.toPrintablePath() } }
    }

    private fun dump(extraSourceProvider: IdeExtraSourceProvider) {
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
        prop("IsPseudoLocalesEnabled") { ideBuildTypeContainer.buildType.isPseudoLocalesEnabled.toString() }
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
        prop("IsIgnoreTestFixturesSources") { lintOptions.isIgnoreTestFixturesSources.toString() }
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
        prop("UseAndroidX") { agpFlags.useAndroidX.toString() }
        prop("UsesCompose") { agpFlags.usesCompose.toString() }
        prop("MlModelBindingEnabled") { agpFlags.mlModelBindingEnabled.toString() }
        prop("AndroidResourcesEnabled") { agpFlags.androidResourcesEnabled.toString() }
        prop("DataBindingEnabled") { agpFlags.dataBindingEnabled.toString() }
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
        prop("ApiLevel") { apiLevel.toString().replaceCurrentSdkVersion(apiLevel, codename) }
        prop("CodeName") { codename }
        prop("ApiString") { apiString.replaceCurrentSdkVersion(apiLevel, codename) }
      }
    }

    fun AndroidVersion.dump(name: String) {
      head(name)
      nest {
        prop("ApiLevel") { apiLevel.toString().replaceCurrentSdkVersion(apiLevel, codename) }
        prop("CodeName") { codename }
        prop("ApiString") { apiString.replaceCurrentSdkVersion(apiLevel, codename) }
      }
    }

    private fun dump(testOptions: IdeTestOptions) {
      head("TestOptions")
      nest {
        prop("AnimationsDisabled") { testOptions.animationsDisabled.toString() }
        prop("Execution") { testOptions.execution?.toString() }
        prop("InstrumentedTestTaskName") { testOptions.instrumentedTestTaskName }
      }
    }

    fun dump(model: GradleModuleModel) {
      head("GradleModuleModel")
      nest {
        prop("agpVersion") { model.agpVersion?.replaceAgpVersion() }
        prop("gradlePath") { model.gradlePath }
        prop("gradleVersion") { model.gradleVersion?.replaceGradleVersion() }
        prop("buildFile") { model.buildFile?.path?.toPrintablePath() }
        prop("buildFilePath") { model.buildFilePath?.path?.toPrintablePath() }
        prop("rootFolderPath") { model.rootFolderPath.path.toPrintablePath() }
        prop("hasSafeArgsJava") { model.hasSafeArgsJavaPlugin().toString() }
        prop("hasSafeArgsKotlin") { model.hasSafeArgsKotlinPlugin().toString() }
        model.taskNames.forEach { prop("- taskNames") { it } }
      }
    }

    fun dump(kotlinGradleModel: KotlinGradleModel) {
      modelDumper.dumpModel(this@with, "kotlinGradleModel", kotlinGradleModel)
    }

    fun dump(kaptGradleModel: KaptGradleModel) {
      if (!kaptGradleModel.isEnabled) return // Usually models are present for all modules with Kotlin but disabled.
      modelDumper.dumpModel(this@with, "kaptGradleModel", kaptGradleModel)
    }

    fun dump(mppGradleModel: KotlinMPPGradleModel) {
      modelDumper.dumpModel(this@with, "kotlinMppGradleModel", mppGradleModel)
    }

    fun dump(externalProject: ExternalProject) {
      modelDumper.dumpModel(this@with, "externalProject", externalProject)
    }

    fun IdeLibrary.toDisplayString(): String = when (this) {
      is IdeArtifactLibrary -> artifactAddress
      is IdeModuleLibrary -> "${buildId}-${projectPath}-${sourceSet}"
      is IdeUnknownLibrary -> key
    }.replaceKnownPaths()

    fun IdeLibrary.toLibraryType(): String = when (this) {
      is IdeAndroidLibrary -> "androidLibrary"
      is IdeJavaLibrary -> "javaLibrary"
      is IdeModuleLibrary -> "module"
      is IdeUnknownLibrary -> "unknown"
    }
  }
}

class DumpProjectIdeModelAction : DumbAwareAction("Dump Project IDE Models") {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    val dumper = ProjectDumper(projectJdk = ProjectRootManager.getInstance(project).projectSdk)
    dumper.dumpAndroidIdeModel(project, { null }, { null }, { null }, { null })
    val dump = dumper.toString().trimIndent()
    val outputFile = File(File(project.basePath), sanitizeFileName(project.name) + ".project_ide_models_dump")
    outputFile.writeText(dump)
    FileEditorManager.getInstance(project).openEditor(OpenFileDescriptor(project, VfsUtil.findFileByIoFile(outputFile, true)!!), true)
    VfsUtil.markDirtyAndRefresh(true, false, false, outputFile)
    println("Dumped to: file://$outputFile")
  }
}
