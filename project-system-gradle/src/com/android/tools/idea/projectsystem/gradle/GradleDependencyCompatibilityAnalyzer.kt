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
import com.android.ide.common.gradle.Component
import com.android.ide.common.gradle.Dependency
import com.android.ide.common.gradle.RichVersion
import com.android.ide.common.gradle.RichVersion.Declaration
import com.android.ide.common.gradle.RichVersion.Kind.STRICTLY
import com.android.ide.common.gradle.Version
import com.android.ide.common.gradle.VersionRange
import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.repository.MavenRepositories
import com.android.ide.common.repository.stability
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
import com.android.tools.idea.gradle.repositories.search.SearchRequest
import com.android.tools.idea.gradle.repositories.search.SearchResult
import com.android.tools.idea.gradle.repositories.search.SingleModuleSearchQuery
import com.android.tools.idea.projectsystem.AndroidModuleSystem
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Multimap
import com.google.common.collect.Range
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import org.jetbrains.annotations.TestOnly
import java.util.ArrayDeque
import com.android.ide.common.gradle.Module as ExternalModule

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
    coordinatesToAdd: List<GradleCoordinate>
  ): ListenableFuture<Triple<List<GradleCoordinate>, List<GradleCoordinate>, String>> =
    coordinatesToAdd.associateBy { it.dependency() }.let { dependenciesToCoordinates ->
      val dependenciesToAdd = dependenciesToCoordinates.keys.toList()
      findVersions(dependenciesToAdd).transform(MoreExecutors.directExecutor()) { results ->
        analyzeCompatibility(dependenciesToAdd, results).run {
          val found = first.map { component -> GradleCoordinate(component.group, component.name, component.version.toString()) }
          val missing = second.mapNotNull { dependency -> dependenciesToCoordinates[dependency] }
          val message = third
          Triple(found, missing, message)
        }
      }
    }

  @Suppress("UnstableApiUsage") // Futures.allAsList
  private fun findVersions(dependencies: List<Dependency>): ListenableFuture<List<SearchResult>> =
    Futures.allAsList(dependencies.mapNotNull {
      it.group?.let { group ->
        createSearchService().search(SearchRequest(SingleModuleSearchQuery(group, it.name), MAX_ARTIFACTS_TO_REQUEST, 0))
      }
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
    dependenciesToAdd: List<Dependency>,
    searchResults: List<SearchResult>
  ): Triple<List<Component>, List<Dependency>, String> {
    val dependencies = dependenciesToAdd.mapNotNull { it.externalModule()?.let { m -> m to it } }.associateBy( { it.first }, { it.second })
    val versionsMap = searchResults.filter { it.artifactFound() }.associate { it.toExternalModuleVersionPair(dependencies) }
    if (!versionsMap.keys.containsAll(dependencies.keys) || versionsMap.values.any { it.isEmpty() }) {
      // The new dependencies were not found, just return.
      return createMissingDependenciesResponse(dependencies, versionsMap)
    }

    // First analyze the existing dependency artifacts of all the related modules.
    val found = mutableListOf<Component>()
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
        versionsMap[artifact]?.firstOrNull()?.let { found.add(artifact.toComponent(it)) }
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
        versions.firstOrNull()?.let { found.add(artifact.toComponent(it)) }
      }
    }
    return Triple(found, listOf(), warning.toString())
  }

  private fun createMissingDependenciesResponse(
    dependencies: Map<ExternalModule, Dependency>,
    resultMap: Map<ExternalModule, List<Version>>
  ): Triple<List<Component>, List<Dependency>, String> {
    val found = dependencies.values.filter { resultMap[it.externalModule()]?.isNotEmpty() ?: false }
      .mapNotNull { it.externalModule()?.let let@{ m -> m.toComponent(resultMap[m]?.firstOrNull() ?: return@let null) } }
    val missing = dependencies.values.filter { resultMap[it.externalModule()]?.isEmpty() ?: true }
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
    id: ExternalModule,
    versions: Iterator<Version>
  ): Component {
    var found: Component? = null
    val testVersion = analyzer.getVersionIdentityMatch(id.group) ?: versions.next()
    var candidate = id.toComponent(testVersion)
    var bestError: VersionIncompatibilityException? = null

    while (found == null) {
      try {
        analyzer.addExplicitDependency(candidate.dependency(), moduleSystem.module.getHolderModule())
        baseAnalyzer.copy(analyzer)
        found = candidate
      }
      catch (ex: VersionIncompatibilityException) {
        analyzer.copy(baseAnalyzer)
        val nextVersionToTest = when {
          ex.problemVersion1.lowerEndpoint().major == null || ex.problemVersion2.lowerEndpoint().major == null -> versions.nextOrNull()
          // At this point we know that we have created a version incompatibility by adding [candidate].
          // If the incompatibility created is more than 2 major versions off, then trying an older version of the candidate
          // is not likely to solve the problem. So jump to the preview section or stop.
          //
          // Example:
          //   - candidate is androidx:recyclerview:recyclerview:1.1.17
          //   - the problem artifact is androidx:annotation:annotation problemVersion1 is 4.1.2 and problemVersion2 is 1.2.0
          // We know that problemVersion2 was added because of the [candidate], trying an older version of candidate would not help.
          ex.problemVersion1.lowerEndpoint().major!! + 2 < ex.problemVersion2.lowerEndpoint().major!! ->
            if (!candidate.version.isPreview) versions.nextPreviewOrNull() else throw bestError ?: ex

          else -> versions.nextOrNull()
        } ?: throw bestError ?: ex
        candidate = id.toComponent(nextVersionToTest)
        bestError = ex
      }
    }
    return found
  }

  private fun SearchResult.artifactFound(): Boolean =
    artifacts.firstOrNull { it.unsortedVersions.isNotEmpty() } != null

  private fun SearchResult.toExternalModuleVersionPair(
    requestedDependencies: Map<ExternalModule, Dependency>
  ): Pair<ExternalModule, List<Version>> {
    val id = artifacts.first().let { ExternalModule(it.groupId, it.name) }
    val versionFilter = requestedDependencies[id]?.version?.let { versionFilter(it) } ?: { true }
    val versionComparator = requestedDependencies[id]?.version?.let { versionComparator(it) } ?: stableFirstComparator
    return Pair(id, selectAndSort(artifacts, versionFilter, versionComparator))
  }

  private fun selectAndSort(artifacts: List<FoundArtifact>, versionFilter: (Version) -> Boolean, versionComparator: Comparator<Version>): List<Version> {
    // Remove duplicates by copying all versions into a Set<Version>...
    val versions = artifacts.flatMapTo(mutableSetOf()) { it.unsortedVersions.filter(versionFilter) }
    return versions.sortedWith(versionComparator)
  }

  private fun versionFilter(requested: RichVersion): (Version) -> Boolean = { v: Version -> requested.accepts(v) }

  private fun versionComparator(requested: RichVersion): Comparator<Version> =
    compareBy<Version>{ it == requested.prefer }.thenBy { !it.isPreview }.thenBy { requested.contains(it) }.thenBy { it }.reversed()

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

  /**
   * Specifies a version incompatibility between [conflict1] from [module1] and [conflict2] from [module2].
   * Some incompatibilities are indirect incompatibilities i.e. from the dependencies of [conflict1] and [conflict2].
   * The details are then found in [problemId1] with [problemVersion1] found from [conflict1] and
   * [problemId2] with [problemVersion2] found from [conflict2].
   *
   * This information is gathered such that a meaningful message can be generated for the user.
   */
  private class VersionIncompatibilityException(
    val conflict1: Dependency,
    val module1: Module?,
    val conflict2: Dependency,
    val module2: Module?,
    val problemId1: ExternalModule,
    val problemVersion1: VersionRange,
    val problemId2: ExternalModule,
    val problemVersion2: VersionRange) : RuntimeException() {

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
    private fun formatVersion(id: ExternalModule, version: VersionRange): String {
      val max = version.upperEndpoint()
      if (MavenRepositories.isAndroidX(id.group) && max != null &&
          max.minor == null && max.micro == null && max.major == version.lowerEndpoint().major?.let { it + 1 }) {
        return version.lowerEndpoint().toString()
      }
      return version.toString()
    }
  }

  /**
   * A dependency analyzer that can track which explicit artifact and which module a dependency is coming from.
   * Special handling are included for pre androidX support artifacts which require version identify.
   */
  private inner class AndroidDependencyAnalyzer() {
    private val dependencyMap = mutableMapOf<ExternalModule, VersionRange>()
    private val explicitDependencies = mutableSetOf<ExternalModule>()
    private val explicitMap = mutableMapOf<ExternalModule, Dependency>()
    private val moduleMap = mutableMapOf<ExternalModule, Module>()
    private val groupMap = mutableMapOf<String, Dependency>()

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
      return groupMap[groupId]?.versionRange()?.lowerEndpoint()
    }

    fun addExplicitDependency(dependency: Dependency, fromModule: Module) {
      val id = dependency.externalModule() ?: return
      val existingDependency = explicitMap[id]
      val existingVersion = dependencyMap[id]
      val existingModule = moduleMap[id]
      val dependencyVersion = dependency.versionRange() ?: VersionRange.parse("+")
      if (existingDependency != null && existingVersion != null &&
          (!dependencyVersion.isConnected(existingVersion) || dependencyVersion.intersection(existingVersion).isEmpty())) {
        throw VersionIncompatibilityException(dependency, fromModule, existingDependency, existingModule,
                                              id, dependencyVersion, id, existingVersion)
      }
      addDependency(dependency, dependency, fromModule)
      explicitDependencies.add(id)
    }

    private fun addDependency(dependency: Dependency, explicitDependency: Dependency, fromModule: Module) {
      val id = dependency.externalModule() ?: return
      val versionRange = dependency.versionRange() ?: return
      val existingVersionRange = dependencyMap[id]
      val existingExplicitCoordinate = explicitMap[id]
      if (versionRange != existingVersionRange) {
        if (existingVersionRange != null && existingExplicitCoordinate != null &&
            (!versionRange.isConnected(existingVersionRange) || versionRange.intersection(existingVersionRange).isEmpty())) {
          throw VersionIncompatibilityException(explicitDependency, fromModule, existingExplicitCoordinate, moduleMap[id],
                                                id, versionRange, id, existingVersionRange)
        }

        // Special case for the support annotations. See details here: b/129408604
        if (groupsWithVersionIdentifyRequirements.contains(id.group) && id.name != SdkConstants.ANNOTATIONS_LIB_ARTIFACT_ID) {
          val otherGroupCoordinate = groupMap[id.group]
          if (otherGroupCoordinate != null) {
            val dependencyVersion = dependency.versionRange() ?: VersionRange.parse("+")
            val existingVersion = otherGroupCoordinate.versionRange() ?: VersionRange.parse("+")
            val otherId = otherGroupCoordinate.externalModule() ?: return
            val otherExplicitCoordinate = explicitMap[otherId]
            if (dependencyVersion != existingVersion && otherExplicitCoordinate != null) {
              throw VersionIncompatibilityException(explicitDependency, fromModule, otherExplicitCoordinate, moduleMap[id],
                                                    id, versionRange, otherId, existingVersion)
            }
          }
          groupMap[id.group] = dependency
        }
        dependencyMap[id] = versionRange
        explicitMap[id] = explicitDependency
        moduleMap[id] = fromModule

        //TODO: Find a way to get artifact dependencies from all repositories...
        repoUrlManager
          .findCompileDependencies(id.group, id.name, versionRange.lowerEndpoint())
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

private fun Component.dependency() = Dependency(group, name, RichVersion.parse(version.toString()))
// You might think that
//   private fun GradleCoordinate.dependency() = Dependency(groupId, artifactId, RichVersion.parse(revision))
// was a good definition for converting the GradleCoordinate to a (Gradle) Dependency.  However,
// this analyzer in its implicit assumptions would like to make quite stringent restrictions on the treatment of
// multiple versions: certainly, stricter than Gradle's dependency resolution, which imposes no semantics on the
// structure of version numbers at all, and essentially assumes that infinite forward compatibility is acceptable.
//
// We preserve this behavior at the GradleCoordinate interface, but assume that callers passing in Dependency objects
// are working with Gradle semantics.
private fun GradleCoordinate.dependency() =
  Dependency(groupId, artifactId, RichVersion(Declaration(STRICTLY, versionRange)))
private fun Dependency.externalModule() = group?.let { ExternalModule(it, name) }
private fun Dependency.versionRange() = version?.let {
  when {
    hasExplicitDistinctUpperBound -> it.require ?: it.strictly
    else -> explicitSingletonVersion?.let { v ->
      val stability = group?.let { g -> Component(g, name, v).stability } ?: return null
      VersionRange(Range.closedOpen(v, stability.expiration(v)))
    }
  }
}
private fun ExternalModule.toComponent(version: Version) = Component(this, version)
private fun ExternalModule.isSameAs(dependency: Dependency) =
  group == dependency.group && name == dependency.name
