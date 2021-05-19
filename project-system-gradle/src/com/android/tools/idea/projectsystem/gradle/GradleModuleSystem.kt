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

import com.android.SdkConstants
import com.android.SdkConstants.ANNOTATIONS_LIB_ARTIFACT_ID
import com.android.ide.common.gradle.model.GradleModelConverter
import com.android.ide.common.gradle.model.IdeAndroidGradlePluginProjectFlags
import com.android.ide.common.gradle.model.IdeBuildType
import com.android.ide.common.gradle.model.IdeLibrary
import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.repository.GradleVersion
import com.android.ide.common.repository.GradleVersionRange
import com.android.ide.common.repository.MavenRepositories
import com.android.manifmerger.ManifestSystemProperty
import com.android.projectmodel.ExternalLibrary
import com.android.repository.io.FileOpUtils
import com.android.tools.idea.gradle.dependencies.GradleDependencyManager
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.repositories.RepositoryUrlManager
import com.android.tools.idea.gradle.util.DynamicAppUtils
import com.android.tools.idea.model.AndroidModel
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
import com.google.common.base.Predicate
import com.google.common.base.Predicates
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Multimap
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.android.dom.manifest.getPrimaryManifestXml
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidUtils
import org.jetbrains.annotations.TestOnly
import java.io.File
import java.util.ArrayDeque
import java.util.Collections
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

private val LOG: Logger get() = logger<GradleModuleSystem>()

/** Creates a map for the given pairs, filtering out null values. */
private fun <K, V> notNullMapOf(vararg pairs: Pair<K, V?>): Map<K, V> {
  return pairs.asSequence()
    .filter { it.second != null }
    .toMap() as Map<K, V>
}

class GradleModuleSystem(
  override val module: Module,
  private val projectBuildModelHandler: ProjectBuildModelHandler,
  private val moduleHierarchyProvider: ModuleHierarchyProvider,
  @TestOnly private val repoUrlManager: RepositoryUrlManager = RepositoryUrlManager.get()
) : AndroidModuleSystem,
    ClassFileFinder by GradleClassFileFinder(module),
    SampleDataDirectoryProvider by MainContentRootSampleDataDirectoryProvider(module) {

  private val groupsWithVersionIdentifyRequirements = listOf(SdkConstants.SUPPORT_LIB_GROUP_ID)

  override fun getResolvedDependency(coordinate: GradleCoordinate): GradleCoordinate? {
    return getResolvedLibraryDependencies()
      .asSequence()
      .mapNotNull { GradleCoordinate.parseCoordinateString(it.address) }
      .find { it.matches(coordinate) }
  }

  // TODO: b/129297171
  override fun getRegisteredDependency(coordinate: GradleCoordinate): GradleCoordinate? {
    return getDirectDependencies(module).find { it.matches(coordinate) }
  }

  private fun getDirectDependencies(module: Module): Sequence<GradleCoordinate> {
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

  override fun getResolvedLibraryDependencies(includeExportedTransitiveDeps: Boolean): Collection<ExternalLibrary> {
    // TODO: b/129297171 When this bug is resolved we may not need getResolvedLibraryDependencies(Module)
    return getResolvedLibraryDependencies(module)
  }

  private fun getResolvedLibraryDependencies(module: Module): Collection<ExternalLibrary> {
    val gradleModel = AndroidModuleModel.get(module) ?: return emptySet()

    val converter = GradleModelConverter(gradleModel.androidProject)
    val javaLibraries = gradleModel.selectedMainCompileLevel2Dependencies.javaLibraries.mapNotNull(converter::convert)
    val androidLibraries = gradleModel.selectedMainCompileLevel2Dependencies.androidLibraries.mapNotNull(converter::convert)

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

  private fun createGradleCoordinate(mavenGroupId: String, mavenArtifactId: String, version: GradleVersion) =
    GradleCoordinate(mavenGroupId, mavenArtifactId, version.toString())

  /**
   * Return the list of modules including [module] that are related to [module].
   *
   * All modules that transitively depends on [module] and all modules that [module] transitively depends on are returned.
   * The returned list will start with [module] followed by its immediate dependents. The iteration order after that is
   * undefined.
   */
  private fun findRelatedModules(): List<Module> {
    val nameLookup = HashMap<String, Module>()
    ModuleManager.getInstance(module.project).modules.forEach { nameLookup[moduleReference(it.name)] = it }

    val dependencies = ArrayListMultimap.create<String, String>()
    val reverseDependencies = ArrayListMultimap.create<String, String>()
    nameLookup.values.forEach { findModuleDependencies(it, dependencies, reverseDependencies) }

    val relatedModules = findTransitiveClosure(dependencies, reverseDependencies)
    return relatedModules.mapNotNull { nameLookup[it] }
  }

  private fun findModuleDependencies(module: Module,
                                     dependencies: Multimap<String, String>,
                                     reverseDependencies: Multimap<String, String>) {
    projectBuildModelHandler.read {
      val dependentNames = getModuleBuildModel(module)?.dependencies()?.modules()?.map { it.path().forceString() } ?: return@read
      val moduleReference = moduleReference(module.name)
      dependencies.putAll(moduleReference, dependentNames)
      dependentNames.forEach { reverseDependencies.put(it, moduleReference) }
    }
  }

  private fun findTransitiveClosure(dependencies: Multimap<String, String>, reverseDependencies: Multimap<String, String>): Set<String> {
    val result = linkedSetOf<String>()
    val stack = ArrayDeque<String>()
    stack.push(moduleReference(module.name))
    while (stack.isNotEmpty()) {
      val element = stack.pop()
      dependencies[element]?.stream()
        ?.filter { !result.contains(it) }
        ?.forEach { stack.add(it) }
      reverseDependencies[element]?.stream()
        ?.filter { !result.contains(it) }
        ?.forEach { stack.add(it) }
      result.add(element)
    }
    return result
  }

  private fun moduleReference(moduleName: String): String {
    return ":$moduleName"
  }

  /**
   * Analyze the existing artifacts and [dependenciesToAdd] for version capability.
   * The decision is designed to help choose versions for [dependenciesToAdd] such
   * that Gradle can still build the project after the dependencies are added.
   *
   * There are (at least) 3 possible error conditions:
   * <ul>
   *   <li>The latest version of a new artifact has a dependency that is newer than
   *       an existing dependency. This method should handle this case by attempting
   *       to match an earlier version of that new artifact.</li>
   *   <li>The latest version of a new artifact has a dependency that is older than
   *       an existing dependency. The situation could be handled by choosing older
   *       versions of the existing dependencies. However this method is not attempting
   *       to handle this situation. Instead a warning message is returned, and the
   *       user has to edit the resulting dependencies if addition is accepted with
   *       those warnings.</li>
   *   <li>There is theoretically a possibility that there is no possible matches.
   *       Give a warning and choose the newest available version.</li>
   * </ul>
   *
   * See the documentation on [AndroidModuleSystem.analyzeDependencyCompatibility]
   * for information on the return value.
   */
  override fun analyzeDependencyCompatibility(dependenciesToAdd: List<GradleCoordinate>)
    : Triple<List<GradleCoordinate>, List<GradleCoordinate>, String> {
    val missing = mutableListOf<GradleCoordinate>()
    val latestVersions = findLatestVersions(dependenciesToAdd, missing)
    if (latestVersions.isEmpty()) {
      // The new dependencies were not found, just return.
      return Triple(emptyList(), missing, "")
    }

    // First analyze the existing dependency artifacts of all the related modules.
    val found = mutableListOf<GradleCoordinate>()
    val analyzer = AndroidDependencyAnalyzer()
    try {
      projectBuildModelHandler.read {
        for (relatedModule in findRelatedModules()) {
          getDirectDependencies(relatedModule).forEach { analyzer.addExplicitDependency(it, relatedModule) }
        }
      }
    }
    catch (ex: VersionIncompatibilityException) {
      // The existing dependencies are not compatible.
      // There is no point in trying to find the correct new dependency.
      latestVersions.forEach { artifact, version -> found.add(GradleCoordinate(artifact.groupId, artifact.artifactId, version.toString())) }
      return Triple(found, missing, "Inconsistencies in the existing project dependencies found.\n${ex.message}")
    }

    // Then attempt to find a version of each new artifact that would not cause compatibility problems with the existing dependencies.
    val baseAnalyzer = AndroidDependencyAnalyzer(analyzer)
    val warning = StringBuilder()
    for ((artifact, latestVersion) in latestVersions) {
      try {
        found.add(findCompatibleVersion(analyzer, baseAnalyzer, artifact, latestVersion))
      }
      catch (ex: VersionIncompatibilityException) {
        warning.append(if (warning.isNotEmpty()) "\n\n" else "").append(ex.message)
        found.add(GradleCoordinate(artifact.groupId, artifact.artifactId, latestVersion.toString()))
      }
    }
    return Triple(found, missing, warning.toString())
  }

  /**
   * Find the latest version in the maven repository for the given [dependenciesToAdd].
   * Return the result as a map from the [GradleCoordinateId] to the latest version found.
   * Any dependencies that are not found in the maven repository is added to [missing].
   */
  private fun findLatestVersions(dependenciesToAdd: List<GradleCoordinate>,
                                 missing: MutableList<GradleCoordinate>): Map<GradleCoordinateId, GradleVersion> {
    val foundArtifactToVersion = mutableMapOf<GradleCoordinateId, GradleVersion>()
    for (coordinate in dependenciesToAdd) {
      val id = GradleCoordinateId(coordinate)

      // Always look for a stable version first. If none exists look for a preview version.
      val latestVersion = repoUrlManager.findVersion(id.groupId, id.artifactId, Predicates.alwaysTrue(), false, FileOpUtils.create())
                          ?: repoUrlManager.findVersion(id.groupId, id.artifactId, Predicates.alwaysTrue(), true, FileOpUtils.create())
      if (latestVersion == null) {
        missing.add(coordinate)
      }
      else {
        foundArtifactToVersion[id] = latestVersion
      }
    }
    return foundArtifactToVersion
  }

  /**
   * Find a compatible version of an [id] starting with the [latestVersion].
   *
   * If a compatibility problem is found try the previous known version of the [id]
   * until either there is no compatibility problems or the added id requires a
   * dependent library that are 2 or more major versions older than an existing dependency.
   * At that point we assume that there are no possible compatible libraries
   * (note: in theory this may be wrong, but is considered safe for practical purposes).
   *
   * Use [analyzer] for testing the dependency. If a version incompatibility is found
   * during testing of all possible versions, the analyzer should be reset to the state
   * specified by [baseAnalyzer]. When a compatible version is found, [baseAnalyzer]
   * should be updated with the state created by adding the successful version of this
   * [id] such that other artifacts can be tested with the dependencies added.
   */
  private fun findCompatibleVersion(analyzer: AndroidDependencyAnalyzer,
                                    baseAnalyzer: AndroidDependencyAnalyzer,
                                    id: GradleCoordinateId,
                                    latestVersion: GradleVersion): GradleCoordinate {
    var found: GradleCoordinate? = null
    val testVersion = analyzer.getVersionIdentityMatch(id.groupId) ?: latestVersion
    var candidate = createGradleCoordinate(id.groupId, id.artifactId, testVersion)
    var bestError: VersionIncompatibilityException? = null

    while (found == null) {
      try {
        analyzer.addExplicitDependency(candidate, module)
        baseAnalyzer.copy(analyzer)
        found = candidate
      }
      catch (ex: VersionIncompatibilityException) {
        analyzer.copy(baseAnalyzer)
        val nextVersionToTest = when {
          ex.problemVersion1.min.major + 2 < ex.problemVersion2.min.major -> throw bestError ?: ex
          id == ex.problemId2 && candidate.version!!.major > ex.problemVersion2.min.major ->
            findNextVersion(id, { it.major == ex.problemVersion2.min.major }, candidate.isPreview) ?: throw bestError ?: ex
          else ->
            findNextVersion(id, { it < candidate.version!! }, candidate.isPreview) ?: throw bestError ?: ex
        }
        candidate = createGradleCoordinate(id.groupId, id.artifactId, nextVersionToTest)
        bestError = ex
      }
    }
    return found
  }

  private fun findNextVersion(id: GradleCoordinateId, filter: (GradleVersion) -> Boolean, isPreview: Boolean): GradleVersion? =
    repoUrlManager.findVersion(id.groupId, id.artifactId, Predicate { filter(it!!) }, isPreview, FileOpUtils.create())

  private data class GradleCoordinateId(val groupId: String, val artifactId: String) {
    constructor(coordinate: GradleCoordinate) : this(coordinate.groupId, coordinate.artifactId)

    override fun toString() = "$groupId:$artifactId"
    fun isSameAs(coordinate: GradleCoordinate) = groupId == coordinate.groupId && artifactId == coordinate.artifactId
  }

  override fun getManifestOverrides(): ManifestOverrides {
    val facet = AndroidFacet.getInstance(module)
    val androidModel = facet?.let(AndroidModel::get) ?: return ManifestOverrides()
    val directOverrides = notNullMapOf(
      ManifestSystemProperty.MIN_SDK_VERSION to androidModel.minSdkVersion?.apiString,
      ManifestSystemProperty.TARGET_SDK_VERSION to androidModel.targetSdkVersion?.apiString,
      ManifestSystemProperty.VERSION_CODE to androidModel.versionCode?.takeIf { it > 0 }?.toString(),
      ManifestSystemProperty.PACKAGE to androidModel.applicationId
    )
    val gradleModel = AndroidModuleModel.get(facet) ?: return ManifestOverrides(directOverrides)
    val flavor = gradleModel.selectedVariant.mergedFlavor
    val buildType = gradleModel.findBuildType(gradleModel.selectedVariant.buildType)!!.buildType
    val placeholders = (flavor.manifestPlaceholders + buildType.manifestPlaceholders).mapValues { it.value.toString() }
    val directOverridesFromGradle = notNullMapOf(
      ManifestSystemProperty.MAX_SDK_VERSION to flavor.maxSdkVersion?.toString(),
      ManifestSystemProperty.VERSION_NAME to getVersionNameOverride(facet, gradleModel, buildType)
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

  private fun getVersionNameOverride(facet: AndroidFacet, gradleModel: AndroidModuleModel, buildType: IdeBuildType): String? {
    val flavor = gradleModel.selectedVariant.mergedFlavor
    val versionName = flavor.versionName
    val flavorSuffix = if (gradleModel.features.isProductFlavorVersionSuffixSupported) flavor.versionNameSuffix.orEmpty() else ""
    val suffix = flavorSuffix + buildType.versionNameSuffix.orEmpty()
    return when {
      versionName != null && versionName.isNotEmpty() -> versionName + suffix
      suffix.isEmpty() -> null
      else -> facet.getPrimaryManifestXml()?.versionName.orEmpty() + suffix
    }
  }

  override fun getPackageName(): String? {
    return getPackageName(module)
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

  /**
   * Specifies a version incompatibility between [conflict1] from [module1] and [conflict2] from [module2].
   * Some incompatibilities are indirect incompatibilities i.e. from the dependencies of [conflict1] and [conflict2].
   * The details are then found in [problemId1] with [problemVersion1] found from [conflict1] and
   * [problemId2] with [problemVersion2] found from [conflict2].
   *
   * This information is gathered such that a meaningful message can be generated for the user.
   */
  private class VersionIncompatibilityException(
    val conflict1: GradleCoordinate,
    val module1: Module?,
    val conflict2: GradleCoordinate,
    val module2: Module?,
    val problemId1: GradleCoordinateId,
    val problemVersion1: GradleVersionRange,
    val problemId2: GradleCoordinateId,
    val problemVersion2: GradleVersionRange) : RuntimeException() {

    override val message: String by lazy {
      val version1 = formatVersion(problemId1, problemVersion1)
      val version2 = formatVersion(problemId2, problemVersion2)
      val module1Name = if (module1 != null && module1 != module2) " in module ${module1.name}" else ""
      val module2Name = if (module2 != null && module1 != module2) " in module ${module2.name}" else ""
      var message = "Version incompatibility between:\n-   $conflict1$module1Name\nand:\n-   $conflict2$module2Name"
      if (!problemId1.isSameAs(conflict1) || !problemId1.isSameAs(conflict2)) {
        message += "\n\nWith the dependency:\n-   $problemId1:$version1\nversus:\n-   $problemId2:$version2"
      }
      message
    }

    /**
     * AndroidX dependency ranges are displayed as simply a version.
     */
    private fun formatVersion(id: GradleCoordinateId, version: GradleVersionRange): String {
      val max = version.max
      if (MavenRepositories.isAndroidX(id.groupId) && max != null &&
          max.minor == 0 && max.micro == 0 && max.major == version.min.major + 1) {
        return version.min.toString()
      }
      return version.toString()
    }
  }

  /**
   * A dependency analyzer that can track which explicit artifact and which module a dependency is coming from.
   * Special handling are included for pre androidX support artifacts which require version identify.
   */
  private inner class AndroidDependencyAnalyzer() {
    private val dependencyMap = mutableMapOf<GradleCoordinateId, GradleVersionRange>()
    private val explicitDependencies = mutableSetOf<GradleCoordinateId>()
    private val explicitMap = mutableMapOf<GradleCoordinateId, GradleCoordinate>()
    private val moduleMap = mutableMapOf<GradleCoordinateId, Module>()
    private val groupMap = mutableMapOf<String, GradleCoordinate>()

    constructor(analyzer: AndroidDependencyAnalyzer) : this() {
      add(analyzer)
    }

    fun copy(analyzer: AndroidDependencyAnalyzer) {
      clear()
      add(analyzer)
    }

    private fun clear() {
      dependencyMap.clear()
      explicitDependencies.clear()
      explicitMap.clear()
      moduleMap.clear()
      groupMap.clear()
    }

    private fun add(analyzer: AndroidDependencyAnalyzer) {
      dependencyMap.putAll(analyzer.dependencyMap)
      explicitDependencies.addAll(analyzer.explicitDependencies)
      explicitMap.putAll(analyzer.explicitMap)
      moduleMap.putAll(analyzer.moduleMap)
      groupMap.putAll(analyzer.groupMap)
    }


    fun getVersionIdentityMatch(groupId: String): GradleVersion? {
      return groupMap[groupId]?.versionRange?.min
    }

    fun addExplicitDependency(dependency: GradleCoordinate, fromModule: Module) {
      val id = GradleCoordinateId(dependency)
      val existingDependency = explicitMap[id]
      val existingVersion = dependencyMap[id]
      val existingModule = moduleMap[id]
      val dependencyVersion = dependency.versionRange ?: GradleVersionRange.parse("+")
      if (existingDependency != null && existingVersion != null && dependencyVersion.intersection(existingVersion) == null) {
        throw VersionIncompatibilityException(dependency, fromModule, existingDependency, existingModule,
                                              id, dependencyVersion, id, existingVersion)
      }
      addDependency(dependency, dependency, fromModule)
      explicitDependencies.add(id)
    }

    private fun addDependency(dependency: GradleCoordinate, explicitDependency: GradleCoordinate, fromModule: Module) {
      val id = GradleCoordinateId(dependency)
      val versionRange = dependency.versionRange ?: return
      val existingVersionRange = dependencyMap[id]
      val existingExplicitCoordinate = explicitMap[id]
      if (versionRange != existingVersionRange) {
        val effectiveRange = if (existingVersionRange != null) existingVersionRange.intersection(versionRange) else versionRange
        if (existingVersionRange != null && existingExplicitCoordinate != null && effectiveRange == null) {
          throw VersionIncompatibilityException(explicitDependency, fromModule, existingExplicitCoordinate, moduleMap[id],
                                                id, versionRange, id, existingVersionRange)
        }

        // Special case for the support annotations. See details here: b/129408604
        if (groupsWithVersionIdentifyRequirements.contains(id.groupId) && id.artifactId != ANNOTATIONS_LIB_ARTIFACT_ID) {
          val otherGroupCoordinate = groupMap[id.groupId]
          if (otherGroupCoordinate != null) {
            val dependencyVersion = dependency.versionRange ?: GradleVersionRange.parse("+")
            val existingVersion = otherGroupCoordinate.versionRange ?: GradleVersionRange.parse("+")
            val otherId = GradleCoordinateId(otherGroupCoordinate)
            val otherExplicitCoordinate = explicitMap[otherId]
            if (dependencyVersion != existingVersion && otherExplicitCoordinate != null) {
              throw VersionIncompatibilityException(explicitDependency, fromModule, otherExplicitCoordinate, moduleMap[id],
                                                    id, versionRange, otherId, existingVersion)
            }
          }
          groupMap[id.groupId] = dependency
        }
        dependencyMap[id] = versionRange
        explicitMap[id] = explicitDependency
        moduleMap[id] = fromModule
        repoUrlManager
          .findCompileDependencies(id.groupId, id.artifactId, versionRange.min)
          .forEach { addDependency(it, explicitDependency, fromModule) }
      }
    }
  }

  override val submodules: Collection<Module>
    get() = moduleHierarchyProvider.submodules
}

private fun AndroidFacet.getLibraryManifests(dependencies: List<AndroidFacet>): List<VirtualFile> {
  if (isDisposed) return emptyList()
  val localLibManifests = dependencies.mapNotNull { it.sourceProviders.mainManifestFile }
  fun IdeLibrary.manifestFile(): File? = this.folder?.resolve(this.manifest)

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
