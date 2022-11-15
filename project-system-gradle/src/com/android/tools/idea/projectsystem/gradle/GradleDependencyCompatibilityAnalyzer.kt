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
package com.android.tools.idea.projectsystem.gradle

import com.android.SdkConstants
import com.android.ide.common.gradle.Version
import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.repository.GradleVersionRange
import com.android.ide.common.repository.MavenRepositories
import com.android.tools.idea.concurrency.transform
import com.android.tools.idea.gradle.dsl.api.repositories.MavenRepositoryModel
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoryModel
import com.android.tools.idea.gradle.project.ProjectStructure
import com.android.tools.idea.gradle.repositories.RepositoryUrlManager
import com.android.tools.idea.gradle.repositories.search.ArtifactRepository
import com.android.tools.idea.gradle.repositories.search.ArtifactRepositorySearchService
import com.android.tools.idea.gradle.repositories.search.CachingRepositorySearchFactory
import com.android.tools.idea.gradle.repositories.search.FoundArtifact
import com.android.tools.idea.gradle.repositories.search.GoogleRepository
import com.android.tools.idea.gradle.repositories.search.JCenterRepository
import com.android.tools.idea.gradle.repositories.search.LocalMavenRepository
import com.android.tools.idea.gradle.repositories.search.MavenCentralRepository
import com.android.tools.idea.gradle.repositories.search.RepositorySearchFactory
import com.android.tools.idea.gradle.repositories.search.SearchQuery
import com.android.tools.idea.gradle.repositories.search.SearchRequest
import com.android.tools.idea.gradle.repositories.search.SearchResult
import com.android.tools.idea.projectsystem.AndroidModuleSystem
import com.android.tools.idea.projectsystem.getHolderModule
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Multimap
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import org.jetbrains.annotations.TestOnly
import java.util.ArrayDeque

private const val MAX_ARTIFACTS_TO_REQUEST = 50  // Note: we do not expect more than one result per repository.
private val groupsWithVersionIdentifyRequirements = listOf(SdkConstants.SUPPORT_LIB_GROUP_ID)

/**
 * Dependency Analyzer for Gradle projects.
 */
class GradleDependencyCompatibilityAnalyzer(
  private val moduleSystem: GradleModuleSystem,
  private val projectBuildModelHandler: ProjectBuildModelHandler,
  @TestOnly private val repoUrlManager: RepositoryUrlManager = RepositoryUrlManager.get()
) {
  private val repositorySearchFactory: RepositorySearchFactory = CachingRepositorySearchFactory()

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
  fun analyzeDependencyCompatibility(
    dependenciesToAdd: List<GradleCoordinate>
  ): ListenableFuture<Triple<List<GradleCoordinate>, List<GradleCoordinate>, String>> =
    findVersions(dependenciesToAdd).transform(MoreExecutors.directExecutor(), { analyzeCompatibility(dependenciesToAdd, it) })

  @Suppress("UnstableApiUsage") // Futures.allAsList
  private fun findVersions(dependencies: List<GradleCoordinate>): ListenableFuture<List<SearchResult>> =
    Futures.allAsList(dependencies.map {
      createSearchService().search(SearchRequest(SearchQuery(it.groupId, it.artifactId), MAX_ARTIFACTS_TO_REQUEST, 0))
    })

  private fun createSearchService(): ArtifactRepositorySearchService {
    val repositories = mutableListOf<ArtifactRepository>()
    projectBuildModelHandler.read {
      getModuleBuildModel(moduleSystem.module)
        ?.repositories()
        ?.repositories()
        .orEmpty()
        .mapNotNullTo(repositories) { repositoryModel -> repositoryModel.toArtifactRepository() }

      if (repositories.isEmpty()) {
        projectSettingsModel
          ?.dependencyResolutionManagement()
          ?.repositories()
          ?.repositories()
          ?.mapNotNullTo(repositories) { repositoryModel -> repositoryModel.toArtifactRepository() }
      }
    }
    if (repositories.isEmpty()) {
      repositories.add(GoogleRepository)
      repositories.add(MavenCentralRepository)
    }
    return repositorySearchFactory.create(repositories)
  }

  private fun analyzeCompatibility(
    dependenciesToAdd: List<GradleCoordinate>,
    searchResults: List<SearchResult>
  ): Triple<List<GradleCoordinate>, List<GradleCoordinate>, String> {
    val dependencies = dependenciesToAdd.associateBy { GradleCoordinateId(it) }
    val versionsMap = searchResults.filter { it.artifactFound() }.associate { it.toGradleCoordinateIdVersionPair(dependencies) }
    if (!versionsMap.keys.containsAll(dependencies.keys) || versionsMap.values.any { it.isEmpty() }) {
      // The new dependencies were not found, just return.
      return createMissingDependenciesResponse(dependencies, versionsMap)
    }

    // First analyze the existing dependency artifacts of all the related modules.
    val found = mutableListOf<GradleCoordinate>()
    val analyzer = AndroidDependencyAnalyzer()
    try {
      projectBuildModelHandler.read {
        for (relatedModule in findRelatedModules()) {
          moduleSystem.getDirectDependencies(relatedModule).forEach { analyzer.addExplicitDependency(it, relatedModule) }
        }
      }
    }
    catch (ex: VersionIncompatibilityException) {
      // The existing dependencies are not compatible.
      // There is no point in trying to find the correct new dependency, just pick the most recent.
      dependencies.keys.forEach { artifact ->
        versionsMap[artifact]?.firstOrNull()?.let { found.add(artifact.toGradleCoordinate(it)) }
      }
      return Triple(found, listOf(), "Inconsistencies in the existing project dependencies found.\n${ex.message}")
    }

    // Then attempt to find a version of each new artifact that would not cause compatibility problems with the existing dependencies.
    val baseAnalyzer = AndroidDependencyAnalyzer(analyzer)
    val warning = StringBuilder()
    for (artifact in dependencies.keys) {
      val versions = versionsMap[artifact] ?: listOf()
      try {
        found.add(findCompatibleVersion(analyzer, baseAnalyzer, artifact, versions.listIterator()))
      }
      catch (ex: VersionIncompatibilityException) {
        warning.append(if (warning.isNotEmpty()) "\n\n" else "").append(ex.message)
        versions.firstOrNull()?.let { found.add(artifact.toGradleCoordinate(it)) }
      }
    }
    return Triple(found, listOf(), warning.toString())
  }

  private fun createMissingDependenciesResponse(
    dependencies: Map<GradleCoordinateId, GradleCoordinate>,
    resultMap: Map<GradleCoordinateId, List<Version>>
  ): Triple<List<GradleCoordinate>, List<GradleCoordinate>, String> {
    val found = dependencies.values.filter { resultMap[GradleCoordinateId(it)]?.isNotEmpty() ?: false }
    val missing = dependencies.values.filter { resultMap[GradleCoordinateId(it)]?.isEmpty() ?: true }
    assert(missing.isNotEmpty())
    val message = when (missing.size) {
      1 -> "The dependency was not found: ${missing.first()}"
      else -> "The dependencies were not found:\n   ${missing.joinToString("\n   ")}"
    }
    return Triple(found, missing, message)
  }

  /**
   * Return the list of modules that are related to the current module.
   *
   * All modules that transitively depends on the current module and all modules that the current module transitively depends on are
   * returned. The returned list will start with the current module followed by its immediate dependents. The iteration order after that is
   * undefined.
   */
  private fun findRelatedModules(): List<Module> {
    val nameLookup = HashMap<String, Module>()
    ModuleManager.getInstance(moduleSystem.module.project)
      .modules
      .filter(ProjectStructure::isAndroidOrJavaHolderModule) // The existing implementation considers only MAIN scope dependencies.
      .forEach { nameLookup[moduleReference(it)] = it }

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
      val moduleReference = moduleReference(module)
      dependencies.putAll(moduleReference, dependentNames.map { moduleReferenceWithinSameIncludedBuild(module, it) })
      dependentNames.forEach { reverseDependencies.put(it, moduleReference) }
    }
  }

  private fun findTransitiveClosure(dependencies: Multimap<String, String>, reverseDependencies: Multimap<String, String>): Set<String> {
    val result = linkedSetOf<String>()
    val stack = ArrayDeque<String>()
    stack.push(moduleReference(moduleSystem.module))
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

  private fun moduleReference(module: Module): String {
    return module.getGradleProjectPath()?.let { "${it.buildRoot}:${it.path}" }.orEmpty()
  }

  private fun moduleReferenceWithinSameIncludedBuild(module: Module, gradlePath: String): String {
    return module.getGradleProjectPath()?.let { "${it.buildRoot}:${gradlePath}" }.orEmpty()
  }

  /**
   * Find a compatible version of an [id] among the possible [versions].
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
  private fun findCompatibleVersion(
    analyzer: AndroidDependencyAnalyzer,
    baseAnalyzer: AndroidDependencyAnalyzer,
    id: GradleCoordinateId,
    versions: Iterator<Version>
  ): GradleCoordinate {
    var found: GradleCoordinate? = null
    val testVersion = analyzer.getVersionIdentityMatch(id.groupId) ?: versions.next()
    var candidate = id.toGradleCoordinate(testVersion)
    var bestError: VersionIncompatibilityException? = null

    while (found == null) {
      try {
        analyzer.addExplicitDependency(candidate, moduleSystem.module.getHolderModule())
        baseAnalyzer.copy(analyzer)
        found = candidate
      }
      catch (ex: VersionIncompatibilityException) {
        analyzer.copy(baseAnalyzer)
        val nextVersionToTest = when {
          ex.problemVersion1.min.major == null || ex.problemVersion2.min.major == null -> versions.nextOrNull()
          // At this point we know that we have created a version incompatibility by adding [candidate].
          // If the incompatibility created is more than 2 major versions off, then trying an older version of the candidate
          // is not likely to solve the problem. So jump to the preview section or stop.
          //
          // Example:
          //   - candidate is androidx:recyclerview:recyclerview:1.1.17
          //   - the problem artifact is androidx:annotation:annotation problemVersion1 is 4.1.2 and problemVersion2 is 1.2.0
          // We know that problemVersion2 was added because of the [candidate], trying an older version of candidate would not help.
          ex.problemVersion1.min.major!! + 2 < ex.problemVersion2.min.major!! ->
            if (!candidate.lowerBoundVersion.isPreview) versions.nextPreviewOrNull() else throw bestError ?: ex

          else -> versions.nextOrNull()
        } ?: throw bestError ?: ex
        candidate = id.toGradleCoordinate(nextVersionToTest)
        bestError = ex
      }
    }
    return found
  }

  private fun SearchResult.artifactFound(): Boolean =
    artifacts.firstOrNull { it.unsortedVersions.isNotEmpty() } != null

  private fun SearchResult.toGradleCoordinateIdVersionPair(
    requestedDependencies: Map<GradleCoordinateId, GradleCoordinate>
  ): Pair<GradleCoordinateId, List<Version>> {
    val id = artifacts.first().let { GradleCoordinateId(it.groupId, it.name) }
    val versionFilter = requestedDependencies[id]?.lowerBoundVersion?.let { versionFilter(it) } ?: { true }
    return Pair(id, selectAndSort(artifacts, versionFilter))
  }

  private fun selectAndSort(artifacts: List<FoundArtifact>, versionFilter: (Version) -> Boolean): List<Version> {
    // Remove duplicates by copying all versions into a Set<Version>...
    val versions = artifacts.flatMapTo(mutableSetOf()) { it.unsortedVersions.filter(versionFilter) }
    return versions.sortedWith(stableFirstComparator)
  }

  private fun versionFilter(requested: Version): (Version) -> Boolean = when {
    // TODO(xof): this rewrite is wrong; requested should not be a Version here (it is a version specifier, and these clauses are
    //  attempting to compute the right thing for prefix matches.
    requested.major == null -> { _ -> true }
    requested.minor == null -> { version -> version.major == requested.major }
    requested.micro == null -> { version -> version.major == requested.major && version.minor == requested.minor }
    else -> { version -> version == requested }
  }

  private fun <T> Iterator<T>.nextOrNull(): T? =
    if (hasNext()) next() else null

  private fun Iterator<Version>.nextPreviewOrNull(): Version? {
    var next: Version? = nextOrNull() ?: return null
    while (next != null && !next.isPreview) {
      next = nextOrNull()
    }
    return next
  }

  private val stableFirstComparator: Comparator<Version> =
    compareBy<Version> { !it.isPreview }.thenBy { it }.reversed()

  private data class GradleCoordinateId(val groupId: String, val artifactId: String) {
    constructor(coordinate: GradleCoordinate) : this(coordinate.groupId, coordinate.artifactId)

    override fun toString() = "$groupId:$artifactId"

    fun isSameAs(coordinate: GradleCoordinate) =
      groupId == coordinate.groupId && artifactId == coordinate.artifactId

    fun toGradleCoordinate(version: Version) =
      GradleCoordinate(groupId, artifactId, version.toString())
  }

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
          max.minor == null && max.micro == null && max.major == version.min.major?.let { it + 1 }) {
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

    fun getVersionIdentityMatch(groupId: String): Version? {
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
        if (groupsWithVersionIdentifyRequirements.contains(id.groupId) && id.artifactId != SdkConstants.ANNOTATIONS_LIB_ARTIFACT_ID) {
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

        //TODO: Find a way to get artifact dependencies from all repositories...
        repoUrlManager
          .findCompileDependencies(id.groupId, id.artifactId, versionRange.min)
          .forEach { addDependency(it, explicitDependency, fromModule) }
      }
    }
  }
}

private fun RepositoryModel.toArtifactRepository(): ArtifactRepository? {
  return when (type) {
    RepositoryModel.RepositoryType.JCENTER_DEFAULT -> JCenterRepository
    RepositoryModel.RepositoryType.MAVEN_CENTRAL -> MavenCentralRepository
    RepositoryModel.RepositoryType.MAVEN ->
      LocalMavenRepository.maybeCreateLocalMavenRepository((this as MavenRepositoryModel).url().forceString(), name().forceString())
    RepositoryModel.RepositoryType.GOOGLE_DEFAULT -> GoogleRepository
    RepositoryModel.RepositoryType.FLAT_DIR -> null
  }
}
