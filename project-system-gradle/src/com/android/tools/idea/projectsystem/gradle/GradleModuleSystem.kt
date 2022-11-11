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

import com.android.ide.common.repository.GradleCoordinate
import com.android.manifmerger.ManifestSystemProperty
import com.android.projectmodel.ExternalAndroidLibrary
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.dependencies.GradleDependencyManager
import com.android.tools.idea.gradle.model.IdeAndroidGradlePluginProjectFlags
import com.android.tools.idea.gradle.model.IdeAndroidLibrary
import com.android.tools.idea.gradle.model.IdeAndroidLibraryDependency
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.model.IdeDependencies
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.project.sync.idea.getGradleProjectPath
import com.android.tools.idea.gradle.util.DynamicAppUtils
import com.android.tools.idea.project.getPackageName
import com.android.tools.idea.projectsystem.AndroidModuleSystem
import com.android.tools.idea.projectsystem.AndroidProjectRootUtil
import com.android.tools.idea.projectsystem.CapabilityStatus
import com.android.tools.idea.projectsystem.CapabilitySupported
import com.android.tools.idea.projectsystem.ClassFileFinder
import com.android.tools.idea.projectsystem.CodeShrinker
import com.android.tools.idea.projectsystem.DependencyScopeType
import com.android.tools.idea.projectsystem.DependencyType
import com.android.tools.idea.projectsystem.ManifestOverrides
import com.android.tools.idea.projectsystem.MergedManifestContributors
import com.android.tools.idea.projectsystem.ModuleHierarchyProvider
import com.android.tools.idea.projectsystem.NamedModuleTemplate
import com.android.tools.idea.projectsystem.SampleDataDirectoryProvider
import com.android.tools.idea.projectsystem.ScopeType
import com.android.tools.idea.projectsystem.TestArtifactSearchScopes
import com.android.tools.idea.projectsystem.buildNamedModuleTemplatesFor
import com.android.tools.idea.projectsystem.getAndroidTestModule
import com.android.tools.idea.projectsystem.getFlavorAndBuildTypeManifests
import com.android.tools.idea.projectsystem.getFlavorAndBuildTypeManifestsOfLibs
import com.android.tools.idea.projectsystem.getForFile
import com.android.tools.idea.projectsystem.getMainModule
import com.android.tools.idea.projectsystem.getTestFixturesModule
import com.android.tools.idea.projectsystem.getTransitiveNavigationFiles
import com.android.tools.idea.projectsystem.getUnitTestModule
import com.android.tools.idea.projectsystem.isAndroidTestFile
import com.android.tools.idea.projectsystem.sourceProviders
import com.android.tools.idea.res.AndroidDependenciesCache
import com.android.tools.idea.res.MainContentRootSampleDataDirectoryProvider
import com.android.tools.idea.run.ApplicationIdProvider
import com.android.tools.idea.run.GradleApplicationIdProvider
import com.android.tools.idea.startup.ClearResourceCacheAfterFirstBuild
import com.android.tools.idea.stats.recordTestLibraries
import com.android.tools.idea.testartifacts.scopes.GradleTestArtifactSearchScopes
import com.android.tools.idea.util.androidFacet
import com.google.wireless.android.sdk.stats.TestLibraries
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.android.dom.manifest.getPrimaryManifestXml
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.idea.versions.LOG
import java.io.File
import java.nio.file.Path
import java.util.Collections
import java.util.concurrent.TimeUnit

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
    SampleDataDirectoryProvider by MainContentRootSampleDataDirectoryProvider(module) {

  override val type: AndroidModuleSystem.Type
    get() = when (GradleAndroidModel.get(module)?.androidProject?.projectType) {
      IdeAndroidProjectType.PROJECT_TYPE_APP -> AndroidModuleSystem.Type.TYPE_APP
      IdeAndroidProjectType.PROJECT_TYPE_ATOM -> AndroidModuleSystem.Type.TYPE_ATOM
      IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE -> AndroidModuleSystem.Type.TYPE_DYNAMIC_FEATURE
      IdeAndroidProjectType.PROJECT_TYPE_FEATURE -> AndroidModuleSystem.Type.TYPE_FEATURE
      IdeAndroidProjectType.PROJECT_TYPE_INSTANTAPP -> AndroidModuleSystem.Type.TYPE_INSTANTAPP
      IdeAndroidProjectType.PROJECT_TYPE_LIBRARY -> AndroidModuleSystem.Type.TYPE_LIBRARY
      IdeAndroidProjectType.PROJECT_TYPE_TEST -> AndroidModuleSystem.Type.TYPE_TEST
      null -> AndroidModuleSystem.Type.TYPE_NON_ANDROID
    }

  override val moduleClassFileFinder: ClassFileFinder = GradleClassFileFinder(module)
  private val androidTestsClassFileFinder: ClassFileFinder = GradleClassFileFinder(module, true)

  private val dependencyCompatibility = GradleDependencyCompatibilityAnalyzer(this, projectBuildModelHandler)

  /**
   * Return the corresponding [ClassFileFinder], depending on whether the [sourceFile] is an android
   * test file or not. In case the [sourceFile] is not specified (is null), the [androidTestsClassFileFinder]
   * will be returned, as it has a wider search scope than [moduleClassFileFinder].
   */
  override fun getClassFileFinderForSourceFile(sourceFile: VirtualFile?) =
    if (sourceFile == null || isAndroidTestFile(module.project, sourceFile)) androidTestsClassFileFinder else moduleClassFileFinder

  override fun getResolvedDependency(coordinate: GradleCoordinate, scope: DependencyScopeType): GradleCoordinate? {
    return getCompileDependenciesFor(module, scope)
      ?.let { it.androidLibraries.asSequence() + it.javaLibraries.asSequence() }
      ?.mapNotNull { GradleCoordinate.parseCoordinateString(it.target.artifactAddress) }
      ?.find { it.matches(coordinate) }
  }

  override fun getDependencyPath(coordinate: GradleCoordinate): Path? {
    return getCompileDependenciesFor(module, DependencyScopeType.MAIN)
      ?.let { dependencies ->
        dependencies.androidLibraries.asSequence().map { it.target.artifactAddress to it.target.artifact } +
          dependencies.javaLibraries.asSequence().map { it.target.artifactAddress to it.target.artifact }
      }
      ?.find { GradleCoordinate.parseCoordinateString(it.first)?.matches(coordinate) ?: false }
      ?.second?.toPath()
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
    } else {
      getCompileDependenciesFor(module, DependencyScopeType.MAIN)
        ?.let { it.androidLibraries.asSequence() + it.javaLibraries.asSequence() }
        ?.mapNotNull { GradleCoordinate.parseCoordinateString(it.target.artifactAddress) } ?: emptySequence()
    }
  }

  override fun getResourceModuleDependencies() =
    AndroidDependenciesCache.getAllAndroidDependencies(module, true).map(AndroidFacet::getModule)

  override fun getAndroidTestDirectResourceModuleDependencies(): List<Module> {
    val dependencies = GradleAndroidModel.get(this.module)?.selectedAndroidTestCompileDependencies
    return dependencies?.moduleDependencies
      // TODO(b/149203281): Rework. This doesn't work with composite build projects and it is extremely slow.
      ?.mapNotNull { it.getGradleProjectPath().resolveIn(this.module.project) }
      ?.toList()
      ?: emptyList()
  }

  override fun getDirectResourceModuleDependents(): List<Module> = ModuleManager.getInstance(module.project).getModuleDependentModules(
    module
  )

  override fun getAndroidLibraryDependencies(scope: DependencyScopeType): Collection<ExternalAndroidLibrary> {
    // TODO: b/129297171 When this bug is resolved we may not need getResolvedLibraryDependencies(Module)
    return getRuntimeDependenciesFor(module, scope)
      .flatMap { it.androidLibraries }
      .distinct()
      .map(IdeAndroidLibraryDependency::target)
      .map(::convertLibraryToExternalLibrary)
      .toList()
  }

  private fun getCompileDependenciesFor(module: Module, scope: DependencyScopeType): IdeDependencies? {
    val gradleModel = GradleAndroidModel.get(module) ?: return null

    return when (scope) {
      DependencyScopeType.MAIN -> gradleModel.selectedVariant.mainArtifact.compileClasspath
      DependencyScopeType.ANDROID_TEST -> gradleModel.selectedVariant.androidTestArtifact?.compileClasspath
      DependencyScopeType.UNIT_TEST -> gradleModel.selectedVariant.unitTestArtifact?.compileClasspath
      DependencyScopeType.TEST_FIXTURES -> gradleModel.selectedVariant.testFixturesArtifact?.compileClasspath
    }
  }

  private fun getRuntimeDependenciesFor(module: Module, scope: DependencyScopeType): Sequence<IdeDependencies> {
    fun impl(module: Module, scope: DependencyScopeType): Sequence<IdeDependencies> = sequence {
      val gradleModel = GradleAndroidModel.get(module)
      if (gradleModel == null) {
        // TODO(b/253476264): Returning an incomplete set of dependencies is highly problematic and should be avoided.
        ClearResourceCacheAfterFirstBuild.getInstance(module.project).setIncompleteRuntimeDependencies()
        return@sequence
      }

      val selectedVariant = gradleModel.selectedVariant
      val artifact = when (scope) {
        DependencyScopeType.MAIN -> selectedVariant.mainArtifact
        DependencyScopeType.ANDROID_TEST -> selectedVariant.androidTestArtifact
        DependencyScopeType.UNIT_TEST -> selectedVariant.unitTestArtifact
        DependencyScopeType.TEST_FIXTURES -> selectedVariant.testFixturesArtifact
      }
      if (artifact != null) yield(artifact.runtimeClasspath)

      yieldAll(
        when {
          scope != DependencyScopeType.MAIN -> impl(module, DependencyScopeType.MAIN)
          gradleModel.androidProject.projectType == IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE -> {
            val baseFeature = DynamicAppUtils.getBaseFeature(module)
            if (baseFeature != null) {
              impl(baseFeature, DependencyScopeType.MAIN)
            } else {
              LOG.error("Cannot find base feature module for: $module")
              emptySequence()
            }
          }
          else -> emptySequence()
        }
      )
    }

    return impl(module, scope)
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

    when (type) {
      DependencyType.ANNOTATION_PROCESSOR -> {
        // addDependenciesWithoutSync doesn't support this: more direct implementation
        manager.addDependenciesWithoutSync(module, coordinates) { _, name, _ ->
          when {
            name.startsWith("androidTest") -> "androidTestAnnotationProcessor"
            name.startsWith("test") -> "testAnnotationProcessor"
            else -> "annotationProcessor"
          }
        }
      }
      DependencyType.DEBUG_IMPLEMENTATION -> {
        manager.addDependenciesWithoutSync(module, coordinates) { _, _, _ ->
          "debugImplementation"
        }
      }
      else -> {
        manager.addDependenciesWithoutSync(module, coordinates)
      }
    }
  }

  override fun updateLibrariesToVersion(toVersions: List<GradleCoordinate>) {
    val manager = GradleDependencyManager.getInstance(module.project)
    manager.updateLibrariesToVersion(module, toVersions)
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
    val androidModel = facet?.let(GradleAndroidModel::get) ?: return ManifestOverrides()
    val directOverrides = notNullMapOf(
      ManifestSystemProperty.UsesSdk.MIN_SDK_VERSION to androidModel.minSdkVersion?.apiString,
      ManifestSystemProperty.UsesSdk.TARGET_SDK_VERSION to androidModel.targetSdkVersion?.apiString,
      ManifestSystemProperty.Manifest.VERSION_CODE to androidModel.versionCode?.takeIf { it > 0 }?.toString(),
      ManifestSystemProperty.Document.PACKAGE to
        (
          when (androidModel.androidProject.projectType) {
            IdeAndroidProjectType.PROJECT_TYPE_APP -> androidModel.applicationId
            IdeAndroidProjectType.PROJECT_TYPE_ATOM -> androidModel.applicationId
            IdeAndroidProjectType.PROJECT_TYPE_INSTANTAPP -> androidModel.applicationId
            IdeAndroidProjectType.PROJECT_TYPE_FEATURE -> androidModel.applicationId
            IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE -> androidModel.applicationId
            IdeAndroidProjectType.PROJECT_TYPE_TEST -> androidModel.applicationId
            IdeAndroidProjectType.PROJECT_TYPE_LIBRARY -> getPackageName()
          }
        )
    )
    val variant = androidModel.selectedVariant
    val placeholders = getManifestPlaceholders()
    val directOverridesFromGradle = notNullMapOf(
      ManifestSystemProperty.UsesSdk.MAX_SDK_VERSION to variant.maxSdkVersion?.toString(),
      ManifestSystemProperty.Manifest.VERSION_NAME to getVersionNameOverride(facet, androidModel)
    )
    return ManifestOverrides(directOverrides + directOverridesFromGradle, placeholders)
  }

  override fun getManifestPlaceholders(): Map<String, String> {
    val facet = AndroidFacet.getInstance(module)
    val androidModel = facet?.let(GradleAndroidModel::get) ?: return emptyMap()
    return androidModel.selectedVariant.manifestPlaceholders
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

  private fun getVersionNameOverride(facet: AndroidFacet, gradleModel: GradleAndroidModel): String? {
    val variant = gradleModel.selectedVariant
    val versionNameWithSuffix = variant.versionNameWithSuffix
    val versionNameSuffix = variant.versionNameSuffix
    return when {
      !versionNameWithSuffix.isNullOrEmpty() -> versionNameWithSuffix
      versionNameSuffix.isNullOrEmpty() -> null
      else -> facet.getPrimaryManifestXml()?.versionName.orEmpty() + versionNameSuffix
    }
  }

  override fun getPackageName(): String? {
    val facet = AndroidFacet.getInstance(module) ?: return null
    return GradleAndroidModel.get(facet)?.androidProject?.namespace ?: getPackageName(module)
  }

  override fun getTestPackageName(): String? {
    val facet = AndroidFacet.getInstance(module) ?: return null
    val androidModuleModel = GradleAndroidModel.get(facet)
    val variant = androidModuleModel?.selectedVariant ?: return null
    // Only report a test package if the selected variant actually has corresponding androidTest components
    if (variant.androidTestArtifact == null) return null
    return androidModuleModel.androidProject.testNamespace ?: variant.deprecatedPreMergedTestApplicationId ?: run {
      // That's how older versions of AGP that do not include testNamespace directly in the model work:
      // in apps the applicationId from the model is used with the ".test" suffix (ignoring the manifest), in libs
      // there is no applicationId and the package name from the manifest is used with the suffix.
      val applicationId = if (facet.configuration.isLibraryProject) getPackageName() else variant.deprecatedPreMergedApplicationId
      if (applicationId.isNullOrEmpty()) null else "$applicationId.test"
    }
  }

  override fun getApplicationIdProvider(): ApplicationIdProvider {
    val androidFacet = AndroidFacet.getInstance(module) ?: error("Cannot find AndroidFacet. Module: ${module.name}")
    val androidModel = GradleAndroidModel.get(androidFacet) ?: error("Cannot find AndroidModuleModel. Module: ${module.name}")
    return GradleApplicationIdProvider.create(
      androidFacet, false, androidModel, androidModel.selectedBasicVariant, androidModel.selectedVariant
    )
  }

  override fun getResolveScope(scopeType: ScopeType): GlobalSearchScope {
    val type = type
    val mainModule = if (type == AndroidModuleSystem.Type.TYPE_TEST) null else module.getMainModule()
    val androidTestModule = if (type == AndroidModuleSystem.Type.TYPE_TEST) module.getMainModule() else module.getAndroidTestModule()
    val unitTestModule = module.getUnitTestModule()
    val fixturesModule = module.getTestFixturesModule()
    return when (scopeType) {
      ScopeType.MAIN -> mainModule?.getModuleWithDependenciesAndLibrariesScope(false)
      ScopeType.UNIT_TEST -> unitTestModule?.getModuleWithDependenciesAndLibrariesScope(true)
      ScopeType.ANDROID_TEST -> androidTestModule?.getModuleWithDependenciesAndLibrariesScope(true)
      ScopeType.TEST_FIXTURES -> fixturesModule?.getModuleWithDependenciesAndLibrariesScope(false)
      ScopeType.SHARED_TEST -> GlobalSearchScope.EMPTY_SCOPE
    } ?: GlobalSearchScope.EMPTY_SCOPE
  }

  override fun getTestArtifactSearchScopes(): TestArtifactSearchScopes = GradleTestArtifactSearchScopes(module)

  private inline fun <T> readFromAgpFlags(read: (IdeAndroidGradlePluginProjectFlags) -> T): T? {
    return GradleAndroidModel.get(module)?.androidProject?.agpFlags?.let(read)
  }

  override val usesCompose: Boolean
    get() = StudioFlags.COMPOSE_PROJECT_USES_COMPOSE_OVERRIDE.get() ||
            readFromAgpFlags { it.usesCompose } ?: false

  override val codeShrinker: CodeShrinker?
    get() = when (GradleAndroidModel.get(module)?.selectedVariant?.mainArtifact?.codeShrinker) {
      com.android.tools.idea.gradle.model.CodeShrinker.PROGUARD -> CodeShrinker.PROGUARD
      com.android.tools.idea.gradle.model.CodeShrinker.R8 -> CodeShrinker.R8
      null -> null
    }

  override val isRClassTransitive: Boolean get() = readFromAgpFlags { it.transitiveRClasses } ?: true

  override fun getTestLibrariesInUse(): TestLibraries? {
    val androidTestArtifact = GradleAndroidModel.get(module)?.selectedVariant?.androidTestArtifact ?: return null
    return TestLibraries.newBuilder().also { recordTestLibraries(it, androidTestArtifact) }.build()
  }

  override fun getDynamicFeatureModules(): List<Module> {
    val project = GradleAndroidModel.get(module)?.androidProject ?: return emptyList()
    val ourGradleProjectPath = gradleProjectPath.toHolder()
    return project.dynamicFeatures.map { dynamicFeature ->
      val dynamicFeatureGradleProjectPath = ourGradleProjectPath.copy(path = dynamicFeature)
      dynamicFeatureGradleProjectPath.resolveIn(module.project) ?: error("Missing dynamic feature module: $dynamicFeatureGradleProjectPath")
    }
  }

  override fun getBaseFeatureModule(): Module? {
    val ideAndroidProject = GradleAndroidModel.get(module)?.androidProject ?: return null
    return ideAndroidProject
      .baseFeature
      ?.let { baseFeature -> gradleProjectPath.toHolder().copy(path = baseFeature) }
      ?.resolveIn(module.project)
  }

  private val gradleProjectPath: GradleProjectPath get() = module.getGradleProjectPath() ?: error("getGradleProjectPath($module) == null")

  override val isMlModelBindingEnabled: Boolean get() = readFromAgpFlags { it.mlModelBindingEnabled } ?: false

  override val isViewBindingEnabled: Boolean get() = GradleAndroidModel.get(module)?.androidProject?.viewBindingOptions?.enabled ?: false

  override val isKaptEnabled: Boolean get() = GradleAndroidModel.get(module)?.androidProject?.isKaptEnabled ?: false

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
    (listOf(this) + dependencies)
      .flatMap {
        GradleAndroidModel.get(it)
          ?.selectedMainCompileDependencies
          ?.androidLibraries
          ?.mapNotNull { it.target.manifestFile() }
          .orEmpty()
      }
      .toSet()

  // Local library manifests come first because they have higher priority.
  return localLibManifests +
    // If any of these are null, then the file is specified in the model,
    // but not actually available yet, such as exploded AAR manifests.
    aarManifests.mapNotNull { VfsUtil.findFileByIoFile(it, false) }
}
