/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.repositories

import com.android.ide.common.gradle.Component
import com.android.ide.common.gradle.Dependency
import com.android.ide.common.gradle.Version
import com.android.ide.common.repository.GoogleMavenArtifactId
import com.android.ide.common.repository.GoogleMavenRepository
import com.android.ide.common.repository.MavenRepositories
import com.android.ide.common.repository.SdkMavenRepository
import com.android.io.CancellableFileIo
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths
import com.android.tools.idea.gradle.util.GradleLocalCache
import com.android.tools.idea.lint.common.LintIdeSupport
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.lint.checks.GradleDetector.Companion.getLatestVersionFromRemoteRepo
import com.android.tools.lint.client.api.LintClient
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableList
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.serviceContainer.NonInjectable
import java.io.File
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Predicate

/**
 * Helper class to aid in generating Maven URLs for various internal repository files (Support Library, AppCompat, etc).
 */
class RepositoryUrlManager @NonInjectable @VisibleForTesting constructor(
  private val googleMavenRepository: GoogleMavenRepository,
  private val cachedGoogleMavenRepository: GoogleMavenRepository,
  private val forceRepositoryChecksInTests: Boolean,
  private val useEmbeddedStudioRepo: Boolean = true) {
  private val pendingNetworkRequests: MutableSet<String> = ConcurrentHashMap.newKeySet()

  internal constructor() : this(IdeGoogleMavenRepository,
                                OfflineIdeGoogleMavenRepository, false)

  fun getArtifactComponentIdentifier(artifactId: GoogleMavenArtifactId, preview: Boolean): String? =
    getArtifactComponentIdentifier(artifactId, null, preview)

  fun getArtifactComponentIdentifier(artifactId: GoogleMavenArtifactId, filter: Predicate<Version>?, preview: Boolean): String? {
    val revision = getLibraryRevision(
      artifactId.mavenGroupId, artifactId.mavenArtifactId, filter, preview, FileSystems.getDefault()
    ) ?: return null
    return artifactId.getComponent(revision).toIdentifier()
  }

  /**
   * A helper function which wraps [findVersion]?.toString().
   */
  fun getLibraryRevision(
    groupId: String, artifactId: String, filter: Predicate<Version>?, includePreviews: Boolean,
    // TODO: remove when EmbeddedDistributionPaths uses Path rather than File
    fileSystem: FileSystem
  ): String? = findVersion(groupId, artifactId, filter, includePreviews, fileSystem)?.toString()

  /**
   * Returns the string for the specific version number of the most recent version of the given library
   * (matching the given prefix filter, if any)
   *
   * @param groupId         the group id
   * @param artifactId      the artifact id
   * @param filter          the optional filter constraining acceptable versions
   * @param includePreviews whether to include preview versions of libraries
   */
  fun findVersion(
    groupId: String, artifactId: String, filter: Predicate<Version>?, includePreviews: Boolean, fileSystem: FileSystem
  ): Version? {
    val version: Version?
    // First check the Google maven repository, which has most versions.
    if (ApplicationManager.getApplication().isDispatchThread) {
      version = cachedGoogleMavenRepository.findVersion(groupId, artifactId, filter, includePreviews)
      refreshCacheInBackground(groupId, artifactId)
    }
    else {
      version = googleMavenRepository.findVersion(groupId, artifactId, filter, includePreviews)
    }
    if (version != null) {
      return version
    }

    if (useEmbeddedStudioRepo) {
      // Try the repo embedded in AS.
      return EmbeddedDistributionPaths.getInstance().findAndroidStudioLocalMavenRepoPaths()
        .filter { it?.isDirectory == true }
        .firstNotNullOfOrNull {
          MavenRepositories.getHighestInstalledVersion(groupId, artifactId, fileSystem.getPath(it.path), filter, includePreviews)
        }?.version
    }
    return null
  }

  fun findCompileDependencies(groupId: String, artifactId: String, version: Version): List<Dependency> {
    // First check the Google maven repository, which has most versions.
    val result: List<Dependency>
    if (ApplicationManager.getApplication().isDispatchThread) {
      result = cachedGoogleMavenRepository.findCompileDependencies(groupId, artifactId, version)
      refreshCacheInBackground(groupId, artifactId)
    }
    else {
      result = googleMavenRepository.findCompileDependencies(groupId, artifactId, version)
    }
    return result
  }

  /**
   * Gets the file on the local filesystem that corresponds to the given [Component].
   *
   * @param component        the [Component] to retrieve an archive file for
   * @param sdkLocation      SDK to use
   * @param fileSystem       the [FileSystem] to work in
   * @return a file pointing at the archive for the given coordinate or null if no SDK is configured
   */
  fun getArchiveForComponent(
    component: Component, sdkLocation: File,
    // TODO: remove when EmbeddedDistributionPaths uses Path rather than File
    fileSystem: FileSystem
  ): File? {
    val group = component.group
    val name = component.name
    val sdkPath = fileSystem.getPath(sdkLocation.path)
    val repository = SdkMavenRepository.find(sdkPath, group, name) ?: return null
    val repositoryLocation = repository.getRepositoryLocation(sdkPath, true) ?: return null
    val artifactDirectory: Path? = MavenRepositories.getArtifactDirectory(repositoryLocation, component)
    if (!CancellableFileIo.isDirectory(artifactDirectory!!)) {
      return null
    }
    for (artifactType in ImmutableList.of("jar", "aar")) {
      val archive = artifactDirectory.resolve("$name-${component.version}.$artifactType")
      if (CancellableFileIo.isRegularFile(archive)) {
        return archive.toFile()
      }
    }
    return null
  }

  /**
   * Checks the given [Dependency], and if it is not an explicit singleton, returns
   * a [Component] with the rich dependency replaced with a specific version.
   * This tries looking at local caches, to pick the best version that Gradle would use without hitting the network,
   * but (if a [Project] is provided) it can also fall back to querying the network for the latest version.
   * Works not just for a completely generic dynamic version (e.g. `+`), but for more specific Rich Versions like `23.+` and `23.1.+`,
   * `[23,24]`, and rich versions including strict and preferred versions.
   *
   * Note that in some cases the method may return null -- such as the case for unknown artifacts not found on disk or on the network,
   * or for valid artifacts but where there is no local cache and the network query is not successful.
   *
   * @param dependency the [Dependency]
   * @param project    the current project, if known. This is required if you want to perform a network lookup of
   * the current best version if we can't find a locally cached version of the library
   * @return the corresponding [Component], or null if not successful
   */
  fun resolveDependency(
    dependency: Dependency, project: Project?, sdkHandler: AndroidSdkHandler?
  ): Component? {
    val version = resolveDependencyRichVersion( // sdkHandler is nullable for Mockito support, should have default value instead
      dependency, project, sdkHandler ?: AndroidSdks.getInstance().tryToChooseSdkHandler()) ?: return null
    return Component(dependency.group ?: return null, dependency.name, Version.parse(version))
  }

  fun resolveDependencyRichVersion(dependency: Dependency, project: Project?, sdkHandler: AndroidSdkHandler?): String? {
    @Suppress("NAME_SHADOWING")
    val sdkHandler = sdkHandler ?: AndroidSdks.getInstance().tryToChooseSdkHandler()

    dependency.explicitSingletonVersion?.let { return it.toString() }
    val filter = Predicate { version: Version -> dependency.version?.contains(version) ?: true } // TODO(xof): accepts?
    val module = dependency.module ?: return null
    val bestAvailableGoogleMavenRepo: GoogleMavenRepository

    // First check the Google maven repository, which has most versions.
    if (ApplicationManager.getApplication().isDispatchThread) {
      bestAvailableGoogleMavenRepo = cachedGoogleMavenRepository
      refreshCacheInBackground(module.group, module.name)
    }
    else {
      bestAvailableGoogleMavenRepo = googleMavenRepository
    }

    val stable = bestAvailableGoogleMavenRepo.findVersion(module.group, module.name, filter, false)
    if (stable != null) {
      return stable.toString()
    }
    val version = bestAvailableGoogleMavenRepo.findVersion(module.group, module.name, filter, true)
    if (version != null) {
      // Only had preview version; use that (for example, artifacts that haven't been released as stable yet).
      return version.toString()
    }
    val sdkLocation = sdkHandler.location
    if (sdkLocation != null) {
      // If this coordinate points to an artifact in one of our repositories, mark it with a comment if they don't
      // have that repository available.
      var libraryCoordinate = getLibraryRevision(module.group, module.name, filter, false,
                                                 sdkHandler.location?.fileSystem ?: FileSystems.getDefault())
      if (libraryCoordinate != null) {
        return libraryCoordinate
      }

      // If that didn't yield any matches, try again, this time allowing preview platforms.
      // This is necessary if the artifact prefix includes enough of a version where there are only preview matches.
      libraryCoordinate = getLibraryRevision(module.group, module.name, filter, true,
                                             sdkHandler.location?.fileSystem ?: FileSystems.getDefault())
      if (libraryCoordinate != null) {
        return libraryCoordinate
      }
    }

    // Regular Gradle dependency? Look in Gradle cache.
    val versionFound = GradleLocalCache.getInstance().findLatestArtifactVersion(dependency, project)
    if (versionFound != null) {
      return versionFound.toString()
    }

    // Maybe it's available for download as an SDK component.
    val progress = StudioLoggerProgressIndicator(javaClass)
    val allowPreview = dependency.explicitlyIncludesPreview
    val sdkPackage = SdkMavenRepository.findLatestRemoteVersion(module, allowPreview, sdkHandler, filter, progress)
    if (sdkPackage != null) {
      val found = SdkMavenRepository.getComponentFromSdkPath(sdkPackage.path)
      if (found != null) {
        return found.version.toString()
      }
    }

    // Perform network lookup to resolve current best version, if possible.
    project ?: return null
    val client: LintClient = LintIdeSupport.get().createClient(project)
    return getLatestVersionFromRemoteRepo(client, dependency, filter, allowPreview)?.toString()
  }

  private fun refreshCacheInBackground(groupId: String, artifactId: String) {
    val searchKey = ("$groupId:$artifactId")
    if (pendingNetworkRequests.add(searchKey)) {
      ApplicationManager.getApplication().executeOnPooledThread {
        try {
          // We don't care about the result, just the side effect of updating the cache.
          // This will only make a network request if there is no cache or it has expired.
          googleMavenRepository.findVersion(groupId, artifactId, { true }, true)
        }
        finally {
          pendingNetworkRequests.remove(searchKey)
        }
      }
    }
  }

  companion object {
    @JvmStatic
    fun get(): RepositoryUrlManager = ApplicationManager.getApplication().getService(RepositoryUrlManager::class.java)
  }
}
