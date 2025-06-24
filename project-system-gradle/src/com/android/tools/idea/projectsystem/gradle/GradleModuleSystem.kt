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

import com.android.ide.common.gradle.Component
import com.android.ide.common.gradle.Dependency
import com.android.ide.common.gradle.RichVersion
import com.android.ide.common.repository.AgpVersion
import com.android.ide.common.repository.WellKnownMavenArtifactId
import com.android.manifmerger.ManifestSystemProperty
import com.android.projectmodel.ExternalAndroidLibrary
import com.android.tools.idea.concurrency.transform
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.dependencies.GradleDependencyManager
import com.android.tools.idea.gradle.model.IdeAndroidGradlePluginProjectFlags
import com.android.tools.idea.gradle.model.IdeAndroidLibrary
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.model.IdeArtifactLibrary
import com.android.tools.idea.gradle.model.IdeArtifactName
import com.android.tools.idea.gradle.model.IdeDeclaredDependencies
import com.android.tools.idea.gradle.model.IdeDependencies
import com.android.tools.idea.gradle.model.IdeJavaLibrary
import com.android.tools.idea.gradle.model.IdeModuleLibrary
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.project.model.GradleAndroidDependencyModel
import com.android.tools.idea.gradle.project.sync.idea.getGradleProjectPath
import com.android.tools.idea.util.DynamicAppUtils
import com.android.tools.idea.projectsystem.AndroidModuleSystem
import com.android.tools.idea.projectsystem.AndroidModuleSystem.Type
import com.android.tools.idea.projectsystem.AndroidProjectRootUtil
import com.android.tools.idea.projectsystem.ClassFileFinder
import com.android.tools.idea.projectsystem.CodeShrinker
import com.android.tools.idea.projectsystem.CommonTestType
import com.android.tools.idea.projectsystem.DependencyScopeType
import com.android.tools.idea.projectsystem.DependencyType
import com.android.tools.idea.projectsystem.ManifestOverrides
import com.android.tools.idea.projectsystem.MergedManifestContributors
import com.android.tools.idea.projectsystem.ModuleHierarchyProvider
import com.android.tools.idea.projectsystem.NamedModuleTemplate
import com.android.tools.idea.projectsystem.ProjectSyncModificationTracker
import com.android.tools.idea.projectsystem.RegisteredDependencyCompatibilityResult
import com.android.tools.idea.projectsystem.RegisteredDependencyId
import com.android.tools.idea.projectsystem.RegisteredDependencyQueryId
import com.android.tools.idea.projectsystem.RegisteringModuleSystem
import com.android.tools.idea.projectsystem.SampleDataDirectoryProvider
import com.android.tools.idea.projectsystem.ScopeType
import com.android.tools.idea.projectsystem.TestArtifactSearchScopes
import com.android.tools.idea.projectsystem.buildNamedModuleTemplatesFor
import com.android.tools.idea.projectsystem.getFlavorAndBuildTypeManifests
import com.android.tools.idea.projectsystem.getFlavorAndBuildTypeManifestsOfLibs
import com.android.tools.idea.projectsystem.getForFile
import com.android.tools.idea.projectsystem.getTransitiveNavigationFiles
import com.android.tools.idea.projectsystem.sourceProviders
import com.android.tools.idea.rendering.StudioModuleDependencies
import com.android.tools.idea.res.AndroidDependenciesCache
import com.android.tools.idea.res.MainContentRootSampleDataDirectoryProvider
import com.android.tools.idea.run.ApplicationIdProvider
import com.android.tools.idea.run.GradleApplicationIdProvider
import com.android.tools.idea.startup.ClearResourceCacheAfterFirstBuild
import com.android.tools.idea.stats.recordTestLibraries
import com.android.tools.idea.testartifacts.scopes.GradleTestArtifactSearchScopes
import com.android.tools.idea.util.androidFacet
import com.android.tools.module.ModuleDependencies
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.wireless.android.sdk.stats.TestLibraries
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.android.dom.manifest.getPrimaryManifestXml
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.plugins.gradle.execution.build.CachedModuleDataFinder
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File
import java.nio.file.Path
import com.android.ide.common.gradle.Module as ExternalModule

/** Creates a map for the given pairs, filtering out null values. */
private fun <K, V> notNullMapOf(vararg pairs: Pair<K, V?>): Map<K, V> {
  @Suppress("UNCHECKED_CAST")
  return pairs.asSequence()
    .filter { it.second != null }
    .toMap() as Map<K, V>
}

data class GradleRegisteredDependencyQueryId(val module: ExternalModule): RegisteredDependencyQueryId {
  override fun toString(): String = module.toString()
}
data class GradleRegisteredDependencyId(val dependency: Dependency): RegisteredDependencyId {
  override fun toString(): String = dependency.toString()
}

class GradleModuleSystem(
  override val module: Module,
  private val projectBuildModelHandler: ProjectBuildModelHandler,
  private val moduleHierarchyProvider: ModuleHierarchyProvider,
) : AndroidModuleSystem,
    RegisteringModuleSystem<GradleRegisteredDependencyQueryId, GradleRegisteredDependencyId>,
    SampleDataDirectoryProvider by MainContentRootSampleDataDirectoryProvider(module.getHolderModule()) {

  override val type: Type
    get() = when (GradleAndroidModel.get(module)?.androidProject?.projectType) {
      IdeAndroidProjectType.PROJECT_TYPE_APP -> Type.TYPE_APP
      IdeAndroidProjectType.PROJECT_TYPE_ATOM -> Type.TYPE_ATOM
      IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE -> Type.TYPE_DYNAMIC_FEATURE
      IdeAndroidProjectType.PROJECT_TYPE_FEATURE -> Type.TYPE_FEATURE
      IdeAndroidProjectType.PROJECT_TYPE_INSTANTAPP -> Type.TYPE_INSTANTAPP
      IdeAndroidProjectType.PROJECT_TYPE_LIBRARY -> Type.TYPE_LIBRARY
      IdeAndroidProjectType.PROJECT_TYPE_KOTLIN_MULTIPLATFORM -> Type.TYPE_LIBRARY
      IdeAndroidProjectType.PROJECT_TYPE_TEST -> Type.TYPE_TEST
      IdeAndroidProjectType.PROJECT_TYPE_FUSED_LIBRARY -> Type.TYPE_FUSED_LIBRARY
      null -> Type.TYPE_NON_ANDROID
    }

  override val moduleClassFileFinder by lazy { GradleClassFileFinder.createWithoutTests(module) }
  internal val androidTestsClassFileFinder: ClassFileFinder by lazy { GradleClassFileFinder.createIncludingAndroidTest(module) }
  internal val screenshotTestsClassFileFinder: ClassFileFinder by lazy { GradleClassFileFinder.createIncludingScreenshotTest(module) }

  private val dependencyCompatibility = GradleDependencyCompatibilityAnalyzer(this, projectBuildModelHandler)

  override fun hasResolvedDependency(id: WellKnownMavenArtifactId, scope: DependencyScopeType): Boolean =
    getResolvedDependency(id.getModule(), scope) != null

  fun getResolvedDependency(externalModule: ExternalModule, scope: DependencyScopeType): Component? {
    return getCompileDependenciesFor(module, scope)
      ?.libraries
      ?.filterIsInstance<IdeArtifactLibrary>()
      ?.mapNotNull { it.component }
      ?.find { it.group == externalModule.group && it.name == externalModule.name }
  }

  private fun Component.matches(dependency: Dependency): Boolean =
    this.group == dependency.group &&
    this.name == dependency.name &&
    dependency.version?.contains(this.version) == true

  private fun IdeArtifactLibrary.componentToArtifact(): Pair<Component, File?>? =
    when (this) {
      is IdeAndroidLibrary -> component?.let { it to artifact }
      is IdeJavaLibrary -> component?.let { it to artifact }
      else -> null
    }

  fun getDependencyPath(dependency: Dependency): Path? {
    return getCompileDependenciesFor(module, DependencyScopeType.MAIN)
      ?.libraries
      ?.filterIsInstance<IdeArtifactLibrary>()
      ?.mapNotNull { it.componentToArtifact() }
      ?.find { it.first.matches(dependency) }
      ?.second?.toPath()
  }

  override fun getRegisteredDependencyQueryId(id: WellKnownMavenArtifactId): GradleRegisteredDependencyQueryId =
    GradleRegisteredDependencyQueryId(id.getModule())

  override fun getRegisteredDependencyId(id: WellKnownMavenArtifactId): GradleRegisteredDependencyId =
    GradleRegisteredDependencyId(id.getDependency("+"))

  fun getRegisteredDependencyId(component: Component): GradleRegisteredDependencyId =
    GradleRegisteredDependencyId(component.dependency())

  fun getRegisteredDependencyId(dependency: Dependency): GradleRegisteredDependencyId =
    GradleRegisteredDependencyId(dependency)

  override fun getRegisteredDependency(id: GradleRegisteredDependencyQueryId): GradleRegisteredDependencyId? =
    getRegisteredDependency(id.module)?.let { GradleRegisteredDependencyId(it) }

  fun getRegisteredDependency(externalModule: ExternalModule): Dependency? =
    getDirectDependencies(module).find { it.name == externalModule.name && it.group == externalModule.group }

  fun hasRegisteredDependency(externalModule: ExternalModule): Boolean = getRegisteredDependency(externalModule) != null

  private fun Component.dependency() = Dependency(group, name, RichVersion.require(version))

  private fun IdeDeclaredDependencies.IdeCoordinates.dependency(): Dependency? =
    this.takeIf { group != null }?.run {
      when (this.version) {
        null -> Dependency.parse("$group:$name")
        else -> Dependency.parse("$group:$name:$version")
      }
    }

  fun getDirectDependencies(module: Module): Sequence<Dependency> =
    GradleAndroidModel.get(module)?.declaredDependencies?.configurationsToCoordinates
      ?.filter { setOf("implementation", "api").contains(it.key) }
      ?.flatMap { it.value }
      ?.mapNotNull { it.dependency() }
      ?.asSequence()
    ?: emptySequence()

  override fun getResourceModuleDependencies() =
    AndroidDependenciesCache.getAllAndroidDependencies(module.getMainModule(), true).map(AndroidFacet::getModule)

  override fun getAndroidTestDirectResourceModuleDependencies(): List<Module> {
    val dependencies = GradleAndroidDependencyModel.get(this.module)?.selectedAndroidTestCompileDependencies
    return dependencies?.libraries?.filterIsInstance<IdeModuleLibrary>()
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
      .flatMap { it.libraries }
      .distinct()
      .filterIsInstance<IdeAndroidLibrary>()
      .map(::convertLibraryToExternalLibrary)
      .toList()
  }

  private fun getCompileDependenciesFor(module: Module, scope: DependencyScopeType): IdeDependencies? {
    val gradleModel = GradleAndroidDependencyModel.get(module) ?: return null

    return when (scope) {
      DependencyScopeType.MAIN -> gradleModel.selectedVariantWithDependencies.mainArtifact.compileClasspath
      DependencyScopeType.ANDROID_TEST ->
        gradleModel.selectedVariantWithDependencies.deviceTestArtifacts.find { it.name == IdeArtifactName.ANDROID_TEST }?.compileClasspath
      DependencyScopeType.UNIT_TEST ->
        gradleModel.selectedVariantWithDependencies.hostTestArtifacts.find { it.name == IdeArtifactName.UNIT_TEST }?.compileClasspath
      DependencyScopeType.TEST_FIXTURES -> gradleModel.selectedVariantWithDependencies.testFixturesArtifact?.compileClasspath
      DependencyScopeType.SCREENSHOT_TEST ->
        gradleModel.selectedVariantWithDependencies.hostTestArtifacts.find { it.name == IdeArtifactName.SCREENSHOT_TEST }?.compileClasspath
    }
  }

  private fun getRuntimeDependenciesFor(module: Module, scope: DependencyScopeType): Sequence<IdeDependencies> {
    fun impl(module: Module, scope: DependencyScopeType): Sequence<IdeDependencies> = sequence {
      val gradleModel = GradleAndroidDependencyModel.get(module)
      if (gradleModel == null) {
        // TODO(b/253476264): Returning an incomplete set of dependencies is highly problematic and should be avoided.
        ClearResourceCacheAfterFirstBuild.getInstance(module.project).setIncompleteRuntimeDependencies()
        return@sequence
      }

      val selectedVariant = gradleModel.selectedVariantWithDependencies
      val artifact = when (scope) {
        DependencyScopeType.MAIN -> selectedVariant.mainArtifact
        DependencyScopeType.ANDROID_TEST -> selectedVariant.deviceTestArtifacts.find { it.name == IdeArtifactName.ANDROID_TEST }
        DependencyScopeType.UNIT_TEST -> selectedVariant.hostTestArtifacts.find { it.name == IdeArtifactName.UNIT_TEST }
        DependencyScopeType.TEST_FIXTURES -> selectedVariant.testFixturesArtifact
        DependencyScopeType.SCREENSHOT_TEST -> selectedVariant.hostTestArtifacts.find { it.name == IdeArtifactName.SCREENSHOT_TEST }
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
              thisLogger().error("Cannot find base feature module for: $module")
              emptySequence()
            }
          }
          else -> emptySequence()
        }
      )
    }

    return impl(module, scope)
  }

  override fun registerDependency(dependency: GradleRegisteredDependencyId, type: DependencyType) {
    registerDependencies(listOf(dependency.dependency), type)
  }

  private val DependencyType.configurationName get() = when(this) {
      DependencyType.ANNOTATION_PROCESSOR -> "annotationProcessor"
      DependencyType.DEBUG_IMPLEMENTATION -> "debugImplementation"
      DependencyType.IMPLEMENTATION -> "implementation"
    }

  fun registerDependency(component: Component, type: DependencyType) =
    registerDependency(getRegisteredDependencyId(component), type)

  fun registerDependency(dependency: Dependency, type: DependencyType) =
    registerDependency(GradleRegisteredDependencyId(dependency), type)

  private fun registerDependencies(dependencies: List<Dependency>, type: DependencyType) {
    val manager = GradleDependencyManager.getInstance(module.project)
    manager.addDependencies(module, dependencies, type.configurationName)
  }

  override fun getModuleTemplates(targetDirectory: VirtualFile?): List<NamedModuleTemplate> {
    val moduleRootDir = AndroidProjectRootUtil.getModuleDirPath(module)?.let { File(it) }
    val sourceProviders = module.androidFacet?.sourceProviders ?: return listOf()
    val selectedSourceProviders = targetDirectory?.let { sourceProviders.getForFile(targetDirectory) }
      ?: (sourceProviders.currentAndSomeFrequentlyUsedInactiveSourceProviders +
          sourceProviders.currentDeviceTestSourceProviders[CommonTestType.ANDROID_TEST].orEmpty())
    return sourceProviders.buildNamedModuleTemplatesFor(moduleRootDir, selectedSourceProviders)
  }

  override fun analyzeDependencyCompatibility(
    dependencies: List<GradleRegisteredDependencyId>
  ): ListenableFuture<RegisteredDependencyCompatibilityResult<GradleRegisteredDependencyId>> {
    return dependencyCompatibility.analyzeDependencyCompatibility(dependencies.map { it.dependency })
      .transform(MoreExecutors.directExecutor()) { result ->
        RegisteredDependencyCompatibilityResult(
          compatible = result.first.map { (k, v) -> GradleRegisteredDependencyId(k) to GradleRegisteredDependencyId(v.dependency()) }.toMap(),
          incompatible = result.second.map { GradleRegisteredDependencyId(it) },
          warning = result.third
        )
      }
  }

  data class DependencyCompatibilityResult<T>(
    val compatible: Map<T,Component>,
    val incompatible: List<Dependency>,
    val warning: String
  )

  @JvmName("analyzeGradleDependencyCompatibility")
  fun analyzeDependencyCompatibility(dependencies: List<Dependency>): ListenableFuture<DependencyCompatibilityResult<Dependency>> {
    return dependencyCompatibility.analyzeDependencyCompatibility(dependencies)
      .transform(MoreExecutors.directExecutor()) { result ->
        DependencyCompatibilityResult(
          compatible = result.first,
          incompatible = result.second,
          warning = result.third
        )
      }
  }

  fun analyzeComponentCompatibility(components: List<Component>): ListenableFuture<DependencyCompatibilityResult<Component>> {
    return dependencyCompatibility.analyzeComponentCompatibility(components)
      .transform(MoreExecutors.directExecutor()) { result ->
        DependencyCompatibilityResult(
          compatible = result.first,
          incompatible = result.second,
          warning = result.third
        )
      }
  }

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
            IdeAndroidProjectType.PROJECT_TYPE_APP,
            IdeAndroidProjectType.PROJECT_TYPE_ATOM,
            IdeAndroidProjectType.PROJECT_TYPE_INSTANTAPP,
            IdeAndroidProjectType.PROJECT_TYPE_FEATURE,
            IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE,
            IdeAndroidProjectType.PROJECT_TYPE_TEST -> androidModel.applicationId
            IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
            IdeAndroidProjectType.PROJECT_TYPE_FUSED_LIBRARY,
            IdeAndroidProjectType.PROJECT_TYPE_KOTLIN_MULTIPLATFORM-> getPackageName()
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
    return GradleAndroidModel.get(facet)?.androidProject?.namespace
  }

  override fun getTestPackageName(): String? {
    val facet = AndroidFacet.getInstance(module) ?: return null
    val gradleAndroidModel = GradleAndroidModel.get(facet)
    val variant = gradleAndroidModel?.selectedVariant ?: return null
    // Only report a test package if the selected variant actually has corresponding androidTest components
    if (variant.deviceTestArtifacts.find { it.name == IdeArtifactName.ANDROID_TEST } == null) return null
    return gradleAndroidModel.androidProject.testNamespace ?: variant.deprecatedPreMergedTestApplicationId ?: run {
      // That's how older versions of AGP that do not include testNamespace directly in the model work:
      // in apps the applicationId from the model is used with the ".test" suffix (ignoring the manifest), in libs
      // there is no applicationId and the package name from the manifest is used with the suffix.
      val applicationId = if (facet.configuration.isLibraryProject) getPackageName() else variant.deprecatedPreMergedApplicationId
      if (applicationId.isNullOrEmpty()) null else "$applicationId.test"
    }
  }

  override fun getApplicationIdProvider(): ApplicationIdProvider {
    val androidFacet = AndroidFacet.getInstance(module) ?: error("Cannot find AndroidFacet. Module: ${module.name}")
    val androidModel = GradleAndroidModel.get(androidFacet) ?: error("Cannot find GradleAndroidModel. Module: ${module.name}")
    val forTests =  androidFacet.module.isUnitTestModule() || androidFacet.module.isAndroidTestModule() ||
      androidFacet.module.isScreenshotTestModule() || type == AndroidModuleSystem.Type.TYPE_TEST
    return GradleApplicationIdProvider.create(
      androidFacet, forTests, androidModel, androidModel.selectedBasicVariant, androidModel.selectedVariant
    )
  }

  override fun getResolveScope(scopeType: ScopeType): GlobalSearchScope {
    val type = type
    val mainModule = if (type == Type.TYPE_TEST) null else module.getMainModule()
    val androidTestModule = if (type == Type.TYPE_TEST) module.getMainModule() else module.getAndroidTestModule()
    val unitTestModule = module.getUnitTestModule()
    val fixturesModule = module.getTestFixturesModule()
    val screenshotTestModule = module.getScreenshotTestModule()
    return when (scopeType) {
      ScopeType.MAIN -> mainModule?.getModuleWithDependenciesAndLibrariesScope(false)
      ScopeType.UNIT_TEST -> unitTestModule?.getModuleWithDependenciesAndLibrariesScope(true)
      ScopeType.ANDROID_TEST -> androidTestModule?.getModuleWithDependenciesAndLibrariesScope(true)
      ScopeType.TEST_FIXTURES -> fixturesModule?.getModuleWithDependenciesAndLibrariesScope(true)
      ScopeType.SCREENSHOT_TEST -> screenshotTestModule?.getModuleWithDependenciesAndLibrariesScope(true)
    } ?: GlobalSearchScope.EMPTY_SCOPE
  }

  override fun getTestArtifactSearchScopes(): TestArtifactSearchScopes = GradleTestArtifactSearchScopes(module)

  private inline fun <T> readFromAgpFlags(read: (IdeAndroidGradlePluginProjectFlags) -> T): T? {
    return GradleAndroidModel.get(module)?.androidProject?.agpFlags?.let(read)
  }

  private data class AgpBuildGlobalFlags(
    val useAndroidX: Boolean,
    val generateManifestClass: Boolean
  )

  /**
   * Returns the module that is the root for this build.
   *
   * This does not traverse across builds if there is a composite build,
   * or if multiple gradle projects were imported in idea, the value is per
   * gradle build.
   */
  private fun Module.getGradleBuildRootModule(): Module? {
    val currentPath = module.getGradleProjectPath() ?: return null
    return project.findModule(currentPath.resolve(":"))
  }

  /**
   * For some flags, we know they are global to a build, but are only reported by android projects
   *
   * The value is read from any android model in this Gradle build (not traversing included builds)
   * and cached in the module corresponding to the root of that Gradle build.
   *
   * Returns default values if there are no Android models in the same Gradle build as this module
   */
  private val agpBuildGlobalFlags: AgpBuildGlobalFlags
    get() = module.getGradleBuildRootModule()?.let { gradleBuildRoot ->
      CachedValuesManager.getManager(module.project).getCachedValue(gradleBuildRoot, AgpBuildGlobalFlagsProvider(gradleBuildRoot))
    } ?: AGP_GLOBAL_FLAGS_DEFAULTS

  private class AgpBuildGlobalFlagsProvider(private val gradleBuildRoot: Module) : CachedValueProvider<AgpBuildGlobalFlags> {
    override fun compute(): CachedValueProvider.Result<AgpBuildGlobalFlags> {
      val tracker = ProjectSyncModificationTracker.getInstance(gradleBuildRoot.project)
      val buildRoot = gradleBuildRoot.getGradleProjectPath()?.buildRoot ?: return CachedValueProvider.Result(null, tracker)
      val gradleAndroidModel =
        gradleBuildRoot.project.androidFacetsForNonHolderModules()
          .filter { it.module.getGradleProjectPath()?.buildRoot == buildRoot }
          .mapNotNull { GradleAndroidModel.get(it) }
          .firstOrNull()
        ?: return CachedValueProvider.Result(null, tracker)
      val agpBuildGlobalFlags = AgpBuildGlobalFlags(
        useAndroidX = gradleAndroidModel.androidProject.agpFlags.useAndroidX,
        generateManifestClass = gradleAndroidModel.androidProject.agpFlags.generateManifestClass,
      )
      return CachedValueProvider.Result(agpBuildGlobalFlags, tracker)
    }
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

  override val supportsAndroidResources: Boolean
    get() = when {
      module.isHolderModule() -> false
      else -> readFromAgpFlags { it.androidResourcesEnabled } ?: true
    }

  override val isRClassTransitive: Boolean get() = readFromAgpFlags { it.transitiveRClasses } ?: true

  override fun getTestLibrariesInUse(): TestLibraries? {
    val androidTestArtifact =
      GradleAndroidDependencyModel.get(module)?.selectedVariantWithDependencies?.deviceTestArtifacts?.find { it.name == IdeArtifactName.ANDROID_TEST } ?: return null
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
  override val isDataBindingEnabled: Boolean get() = GradleAndroidModel.get(module)?.androidProject?.agpFlags?.dataBindingEnabled ?: false
  override val isKaptEnabled: Boolean get() = GradleAndroidModel.get(module)?.androidProject?.isKaptEnabled ?: false

  override val applicationRClassConstantIds: Boolean get() = readFromAgpFlags { it.applicationRClassConstantIds } ?: true

  override val testRClassConstantIds: Boolean get() = readFromAgpFlags { it.testRClassConstantIds } ?: true

  /**
   * Whether AndroidX libraries should be used instead of legacy support libraries.
   *
   * This property is global to the Gradle build, but only reported in Android models,
   * so the value is read from the first found android model in the same Gradle build,
   * and cached on the idea module corresponding to the root of that gradle build.
   */
  override val useAndroidX: Boolean get() = agpBuildGlobalFlags.useAndroidX

  /** Whether to generate manifest classes. */
  val generateManifestClass: Boolean
    get() = agpBuildGlobalFlags.generateManifestClass && module.isMainModule()

  override val submodules: Collection<Module>
    get() = moduleHierarchyProvider.submodules

  override val desugarLibraryConfigFilesKnown: Boolean
    get() = GradleAndroidModel.get(module)?.agpVersion?.let { it >= (DESUGAR_LIBRARY_CONFIG_MINIMUM_AGP_VERSION) } ?: false
  override val desugarLibraryConfigFilesNotKnownUserMessage: String?
    get() = when {
      GradleAndroidModel.get(module) == null -> "Not supported for non-Android modules."
      !desugarLibraryConfigFilesKnown -> "Only supported for projects using Android Gradle plugin '$DESUGAR_LIBRARY_CONFIG_MINIMUM_AGP_VERSION' and above."
      else -> null
    }
  override val desugarLibraryConfigFiles: List<Path>
    get() = GradleAndroidModel.get(module)?.androidProject?.desugarLibraryConfigFiles?.map { it.toPath() } ?: emptyList()

  override val moduleDependencies: ModuleDependencies get() = StudioModuleDependencies(module)

  /**
   * Returns a name that should be used when displaying a [Module] to the user. This method should be used unless there is a very
   * good reason why it does not work for you. This method performs as follows:
   *   1 - If the [Module] is not registered as a Gradle module then the module's name is returned.
   *   2 - If the [Module] directly corresponds to a Gradle source set, then the name of the source set is returned.
   *   3 - If the [Module] represents the root Gradle project then the project's name is returned.
   *   4 - If the [Module] represents any other module then the root project, the last part of the Gradle path is used.
   *   5 - If any of 2 to 4 fail, for any reason then we always fall back to just using the [Module]'s name.
   */
  override fun getDisplayNameForModule(): String {
    fun getNameFromGradlePath(module: Module) : String? {
      if (!ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module)) return null
      // If we have a module per source-set we need ensure that the names we display are the name of the source-set rather than the module
      // name.
      if (GradleConstants.GRADLE_SOURCE_SET_MODULE_TYPE_KEY == ExternalSystemApiUtil.getExternalModuleType(module)) {
        return GradleProjectResolverUtil.getSourceSetName(module)
      }
      val shortName: String? = ExternalSystemApiUtil.getExternalProjectId(module)
      val isRootModule = StringUtil.equals(ExternalSystemApiUtil.getExternalProjectPath(module),
                                           ExternalSystemApiUtil.getExternalRootProjectPath(
                                             module))
      return if (isRootModule || shortName == null) shortName else StringUtil.getShortName(shortName, ':')
    }
    return getNameFromGradlePath(module) ?: super.getDisplayNameForModule()
  }

  /**
   * Returns the name of the holder module of this group, thereby hiding the implementation detail that each sourceSet
   * has its own module.
   */
  override fun getDisplayNameForModuleGroup(): String = module.getHolderModule().name

  override fun isProductionAndroidModule() = super.isProductionAndroidModule() && module.isMainModule()

  override fun getProductionAndroidModule() = when (val linkedModuleData = module.getUserData(LINKED_ANDROID_GRADLE_MODULE_GROUP)) {
    null -> super.getProductionAndroidModule()
    else -> linkedModuleData.main?.module
  }

  override fun getHolderModule(): Module = module.getHolderModule()

  override fun isValidForAndroidRunConfiguration() = when(type) {
    Type.TYPE_APP, Type.TYPE_DYNAMIC_FEATURE -> module.isHolderModule()
    else -> super.isValidForAndroidRunConfiguration()
  }

  override fun isValidForAndroidTestRunConfiguration() = when(type) {
    Type.TYPE_APP, Type.TYPE_DYNAMIC_FEATURE, Type.TYPE_LIBRARY -> module.isHolderModule() && module.getAndroidTestModule() != null
    Type.TYPE_TEST -> module.isHolderModule()
    else -> super.isValidForAndroidTestRunConfiguration()
  }

  companion object {
    private val AGP_GLOBAL_FLAGS_DEFAULTS = AgpBuildGlobalFlags(
      useAndroidX = true,
      generateManifestClass = false,
    )
    private val DESUGAR_LIBRARY_CONFIG_MINIMUM_AGP_VERSION = AgpVersion.parse("8.1.0-alpha05")

    @RequiresBackgroundThread
    @JvmStatic
    fun getGradleSourceSetName(module: Module): String? {
      val moduleNode = CachedModuleDataFinder.findModuleData(module) ?: return null
      val sourceSetData = moduleNode.data as? GradleSourceSetData ?: return null
      return sourceSetData.moduleName
    }
  }
}


private fun AndroidFacet.getLibraryManifests(dependencies: List<AndroidFacet>): List<VirtualFile> {
  if (isDisposed) return emptyList()
  val localLibManifests = dependencies.mapNotNull { it.sourceProviders.mainManifestFile }
  fun IdeAndroidLibrary.manifestFile(): File? = this.folder?.resolve(this.manifest)

  val aarManifests =
    (listOf(this) + dependencies)
      .flatMap { androidFacet ->
        GradleAndroidDependencyModel.get(androidFacet)
          ?.mainArtifactWithDependencies?.compileClasspath
          ?.libraries
          ?.filterIsInstance<IdeAndroidLibrary>()
          ?.mapNotNull { it.manifestFile() }
          .orEmpty()
      }
      .toSet()

  // Local library manifests come first because they have higher priority.
  return localLibManifests +
    // If any of these are null, then the file is specified in the model,
    // but not actually available yet, such as exploded AAR manifests.
    aarManifests.mapNotNull { VfsUtil.findFileByIoFile(it, false) }
}