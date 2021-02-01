/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.projectsystem.gradle

import com.android.ide.common.gradle.model.IdeAndroidGradlePluginProjectFlags
import com.android.ide.common.gradle.model.IdeAndroidLibrary
import com.android.ide.common.gradle.model.convertLibraryToExternalLibrary
import com.android.ide.common.repository.GradleCoordinate
import com.android.manifmerger.ManifestSystemProperty
import com.android.projectmodel.ExternalLibrary
import com.android.tools.idea.gradle.dependencies.GradleDependencyManager
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.util.DynamicAppUtils
import com.android.tools.idea.project.getPackageName
import com.android.tools.idea.projectsystem.AndroidModuleSystem
import com.android.tools.idea.projectsystem.AndroidProjectRootUtil
import com.android.tools.idea.projectsystem.CapabilityStatus
import com.android.tools.idea.projectsystem.CapabilitySupported
import com.android.tools.idea.projectsystem.ClassFileFinder
import com.android.tools.idea.projectsystem.CodeShrinker
import com.android.tools.idea.projectsystem.DependencyType
import com.android.tools.idea.projectsystem.ManifestOverrides
import com.android.tools.idea.projectsystem.MergedManifestContributors
import com.android.tools.idea.projectsystem.ModuleHierarchyProvider
import com.android.tools.idea.projectsystem.NamedModuleTemplate
import com.android.tools.idea.projectsystem.SampleDataDirectoryProvider
import com.android.tools.idea.projectsystem.ScopeType
import com.android.tools.idea.projectsystem.TestArtifactSearchScopes
import com.android.tools.idea.projectsystem.buildNamedModuleTemplatesFor
import com.android.tools.idea.projectsystem.getFlavorAndBuildTypeManifests
import com.android.tools.idea.projectsystem.getFlavorAndBuildTypeManifestsOfLibs
import com.android.tools.idea.projectsystem.getForFile
import com.android.tools.idea.projectsystem.getTransitiveNavigationFiles
import com.android.tools.idea.projectsystem.sourceProviders
import com.android.tools.idea.res.MainContentRootSampleDataDirectoryProvider
import com.android.tools.idea.run.ApplicationIdProvider
import com.android.tools.idea.run.GradleApplicationIdProvider
import com.android.tools.idea.testartifacts.scopes.GradleTestArtifactSearchScopes
import com.android.tools.idea.util.androidFacet
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.android.dom.manifest.getPrimaryManifestXml
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidUtils
import java.io.File
import java.nio.file.Path
import java.util.Collections
import java.util.concurrent.TimeUnit
import com.android.builder.model.CodeShrinker as BuildModelCodeShrinker

/**
 * Make [.getRegisteredDependency] return the direct module dependencies.
 *
 * The method [.getRegisteredDependency] should return direct module dependencies,
 * but we do not have those available with the current model see b/128449813.
 *
 * The artifacts in
 *   [com.android.tools.idea.gradle.dsl.api.GradleBuildModel.dependencies().artifacts]
 * is a list of the direct dependencies parsed from the build.gradle files but the
 * information will not be available for complex build files.
 *
 * For now always look at the transitive closure of dependencies.
 */
const val CHECK_DIRECT_GRADLE_DEPENDENCIES = false

private val LOG: Logger get() = Logger.getInstance("GradleModuleSystem.kt")

/** Creates a map for the given pairs, filtering out null values. */
private fun <K, V> notNullMapOf(vararg pairs: Pair<K, V?>): Map<K, V> {
  @Suppress("UNCHECKED_CAST")
  return pairs.asSequence()
    .filter { it.second != null }
    .toMap() as Map<K, V>
}

class GradleModuleSystem(
  override val module: Module,
  private val projectBuildModelHandler: ProjectBuildModelHandler,
  private val moduleHierarchyProvider: ModuleHierarchyProvider,
) : AndroidModuleSystem,
    ClassFileFinder by GradleClassFileFinder(module),
    SampleDataDirectoryProvider by MainContentRootSampleDataDirectoryProvider(module) {

  private val dependencyCompatibility = GradleDependencyCompatibilityAnalyzer(this, projectBuildModelHandler)

  override fun getResolvedDependency(coordinate: GradleCoordinate): GradleCoordinate? {
    return getResolvedLibraryDependencies()
      .asSequence()
      .mapNotNull { GradleCoordinate.parseCoordinateString(it.address) }
      .find { it.matches(coordinate) }
  }

  override fun getDependencyPath(coordinate: GradleCoordinate): Path? {
    return getResolvedLibraryDependencies()
      .find { GradleCoordinate.parseCoordinateString(it.address)?.matches(coordinate) ?: false }
      ?.location?.toPath()
  }

  // TODO: b/129297171
  override fun getRegisteredDependency(coordinate: GradleCoordinate): GradleCoordinate? {
    return getDirectDependencies(module).find { it.matches(coordinate) }
  }

  fun getDirectDependencies(module: Module): Sequence<GradleCoordinate> {
    // TODO: b/129297171
    @Suppress("ConstantConditionIf")
    return if (CHECK_DIRECT_GRADLE_DEPENDENCIES) {
      projectBuildModelHandler.read {
        // TODO: Replace the below artifacts with the direct dependencies from the AndroidModuleModel see b/128449813
        val artifacts = getModuleBuildModel(module)?.dependencies()?.artifacts() ?: return@read emptySequence<GradleCoordinate>()
        artifacts
          .asSequence()
          .mapNotNull { GradleCoordinate.parseCoordinateString("${it.group()}:${it.name().forceString()}:${it.version()}") }
      }
    }
    else {
      getResolvedLibraryDependencies(module)
        .asSequence()
        .mapNotNull { GradleCoordinate.parseCoordinateString(it.address) }
    }
  }

  override fun getResourceModuleDependencies() = AndroidUtils.getAllAndroidDependencies(module, true).map(AndroidFacet::getModule)

  override fun getDirectResourceModuleDependents(): List<Module> = ModuleManager.getInstance(module.project).getModuleDependentModules(
    module)

  override fun getResolvedLibraryDependencies(): Collection<ExternalLibrary> {
    // TODO: b/129297171 When this bug is resolved we may not need getResolvedLibraryDependencies(Module)
    return getResolvedLibraryDependencies(module)
  }

  private fun getResolvedLibraryDependencies(module: Module): Collection<ExternalLibrary> {
    val gradleModel = AndroidModuleModel.get(module) ?: return emptySet()

    val javaLibraries =
      gradleModel.selectedMainCompileLevel2Dependencies.javaLibraries.map(::convertLibraryToExternalLibrary)
    val androidLibraries =
      gradleModel.selectedMainCompileLevel2Dependencies.androidLibraries.map(::convertLibraryToExternalLibrary)

    return javaLibraries + androidLibraries
  }

  override fun canRegisterDependency(type: DependencyType): CapabilityStatus {
    return CapabilitySupported()
  }

  override fun registerDependency(coordinate: GradleCoordinate) {
    registerDependency(coordinate, DependencyType.IMPLEMENTATION)
  }

  override fun registerDependency(coordinate: GradleCoordinate, type: DependencyType) {
    val manager = GradleDependencyManager.getInstance(module.project)
    val coordinates = Collections.singletonList(coordinate)

    if (type == DependencyType.ANNOTATION_PROCESSOR) {
      // addDependenciesWithoutSync doesn't support this: more direct implementation
      manager.addDependenciesWithoutSync(module, coordinates) { _, name, _ ->
        when {
          name.startsWith("androidTest") -> "androidTestAnnotationProcessor"
          name.startsWith("test") -> "testAnnotationProcessor"
          else -> "annotationProcessor"
        }
      }
    }
    else {
      manager.addDependenciesWithoutSync(module, coordinates)
    }
  }

  override fun getModuleTemplates(targetDirectory: VirtualFile?): List<NamedModuleTemplate> {
    val moduleRootDir = AndroidProjectRootUtil.getModuleDirPath(module)?.let { File(it) }
    val sourceProviders = module.androidFacet?.sourceProviders ?: return listOf()
    val selectedSourceProviders = targetDirectory?.let { sourceProviders.getForFile(targetDirectory) }
                                  ?: sourceProviders.currentAndSomeFrequentlyUsedInactiveSourceProviders
    return sourceProviders.buildNamedModuleTemplatesFor(moduleRootDir, selectedSourceProviders)
  }

  override fun canGeneratePngFromVectorGraphics(): CapabilityStatus {
    return supportsPngGeneration(module)
  }

  /**
   * See the documentation on [AndroidModuleSystem.analyzeDependencyCompatibility]
   */
  override fun analyzeDependencyCompatibility(dependenciesToAdd: List<GradleCoordinate>)
    : Triple<List<GradleCoordinate>, List<GradleCoordinate>, String> =
    //TODO: Change the API to return a ListenableFuture instead of calling get with a timeout here...
    dependencyCompatibility.analyzeDependencyCompatibility(dependenciesToAdd).get(20, TimeUnit.SECONDS)

  override fun getManifestOverrides(): ManifestOverrides {
    val facet = AndroidFacet.getInstance(module)
    val androidModel = facet?.let(AndroidModuleModel::get) ?: return ManifestOverrides()
    val directOverrides = notNullMapOf(
      ManifestSystemProperty.MIN_SDK_VERSION to androidModel.minSdkVersion?.apiString,
      ManifestSystemProperty.TARGET_SDK_VERSION to androidModel.targetSdkVersion?.apiString,
      ManifestSystemProperty.VERSION_CODE to androidModel.versionCode?.takeIf { it > 0 }?.toString(),
      ManifestSystemProperty.PACKAGE to androidModel.applicationId
    )
    val gradleModel = AndroidModuleModel.get(facet) ?: return ManifestOverrides(directOverrides)
    val variant = gradleModel.selectedVariant
    val placeholders = variant.manifestPlaceholders
    val directOverridesFromGradle = notNullMapOf(
      ManifestSystemProperty.MAX_SDK_VERSION to variant.maxSdkVersion?.toString(),
      ManifestSystemProperty.VERSION_NAME to getVersionNameOverride(facet, gradleModel)
    )
    return ManifestOverrides(directOverrides + directOverridesFromGradle, placeholders)
  }

  override fun getMergedManifestContributors(): MergedManifestContributors {
    val facet = module.androidFacet!!
    val dependencies = getResourceModuleDependencies().mapNotNull { it.androidFacet }
    return MergedManifestContributors(
      primaryManifest = facet.sourceProviders.mainManifestFile,
      flavorAndBuildTypeManifests = facet.getFlavorAndBuildTypeManifests(),
      libraryManifests = if (facet.configuration.isAppOrFeature) facet.getLibraryManifests(dependencies) else emptyList(),
      navigationFiles = facet.getTransitiveNavigationFiles(dependencies),
      flavorAndBuildTypeManifestsOfLibs = facet.getFlavorAndBuildTypeManifestsOfLibs(dependencies)
    )
  }

  private fun getVersionNameOverride(facet: AndroidFacet, gradleModel: AndroidModuleModel): String? {
    val variant = gradleModel.selectedVariant
    val versionNameWithSuffix = variant.versionNameWithSuffix
    val versionNameSuffix = variant.versionNameWithSuffix
    return when {
      versionNameWithSuffix != null && versionNameWithSuffix.isNotEmpty() -> versionNameWithSuffix
      versionNameSuffix.isNullOrEmpty() -> null
      else -> facet.getPrimaryManifestXml()?.versionName.orEmpty() + versionNameSuffix
    }
  }

  override fun getPackageName(): String? {
    return getPackageName(module)
  }

  override fun getTestPackageName(): String? {
    val facet = AndroidFacet.getInstance(module) ?: return null
    val variant = AndroidModuleModel.get(facet)?.selectedVariant ?: return null
    return variant.testApplicationId ?: run {
      // That's how AGP works today: in apps the applicationId from the model is used with the ".test" suffix (ignoring the manifest), in libs
      // there is no applicationId and the package name from the manifest is used with the suffix.
      val applicationId = if (facet.configuration.isLibraryProject) getPackageName() else variant.deprecatedPreMergedApplicationId
      if (applicationId.isNullOrEmpty()) null else "$applicationId.test"
    }
  }

  override fun getNotRuntimeConfigurationSpecificApplicationIdProviderForLegacyUse(): ApplicationIdProvider {
    return GradleApplicationIdProvider(
      AndroidFacet.getInstance(module) ?: throw IllegalStateException("Cannot find AndroidFacet. Module: ${module.name}"), { null }
    )
  }

  override fun getResolveScope(scopeType: ScopeType): GlobalSearchScope {
    val testScopes = getTestArtifactSearchScopes()
    return when {
      scopeType == ScopeType.MAIN -> module.getModuleWithDependenciesAndLibrariesScope(false)
      testScopes == null -> module.getModuleWithDependenciesAndLibrariesScope(true)
      else -> {
        val excludeScope = when (scopeType) {
          ScopeType.SHARED_TEST -> testScopes.sharedTestExcludeScope
          ScopeType.UNIT_TEST -> testScopes.unitTestExcludeScope
          ScopeType.ANDROID_TEST -> testScopes.androidTestExcludeScope
          else -> error("Unknown test scope")
        }

        // Usual scope minus things to exclude:
        module.getModuleWithDependenciesAndLibrariesScope(true).intersectWith(GlobalSearchScope.notScope(excludeScope))
      }
    }
  }

  override fun getTestArtifactSearchScopes(): TestArtifactSearchScopes? = GradleTestArtifactSearchScopes.getInstance(module)

  private inline fun <T> readFromAgpFlags(read: (IdeAndroidGradlePluginProjectFlags) -> T): T? {
    return AndroidModuleModel.get(module)?.androidProject?.agpFlags?.let(read)
  }

  override val usesCompose: Boolean get() = readFromAgpFlags { it.usesCompose } ?: false

  override val codeShrinker: CodeShrinker?
    get() = when (AndroidModuleModel.get(module)?.selectedVariant?.mainArtifact?.codeShrinker) {
      BuildModelCodeShrinker.PROGUARD -> CodeShrinker.PROGUARD
      BuildModelCodeShrinker.R8 -> CodeShrinker.R8
      null -> null
    }

  override val isRClassTransitive: Boolean get() = readFromAgpFlags { it.transitiveRClasses } ?: true

  override fun getDynamicFeatureModules(): List<Module> {
    val project = AndroidModuleModel.get(module)?.androidProject ?: return emptyList()
    return DynamicAppUtils.getDependentFeatureModulesForBase(module.project, project)
  }

  override val isMlModelBindingEnabled: Boolean get() = readFromAgpFlags { it.mlModelBindingEnabled } ?: false

  override val applicationRClassConstantIds: Boolean get() = readFromAgpFlags { it.applicationRClassConstantIds } ?: true

  override val testRClassConstantIds: Boolean get() = readFromAgpFlags { it.testRClassConstantIds } ?: true

  override val submodules: Collection<Module>
    get() = moduleHierarchyProvider.submodules
}

private fun AndroidFacet.getLibraryManifests(dependencies: List<AndroidFacet>): List<VirtualFile> {
  if (isDisposed) return emptyList()
  val localLibManifests = dependencies.mapNotNull { it.sourceProviders.mainManifestFile }
  fun IdeAndroidLibrary.manifestFile(): File? = this.folder?.resolve(this.manifest)

  val aarManifests =
    AndroidModuleModel.get(this)
      ?.selectedMainCompileLevel2Dependencies
      ?.androidLibraries
      ?.mapNotNull { it.manifestFile() }
      ?.toSet()
      .orEmpty()

  // Local library manifests come first because they have higher priority.
  return localLibManifests +
         // If any of these are null, then the file is specified in the model,
         // but not actually available yet, such as exploded AAR manifests.
         aarManifests.mapNotNull { VfsUtil.findFileByIoFile(it, false) }
}
