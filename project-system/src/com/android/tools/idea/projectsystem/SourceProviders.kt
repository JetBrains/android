/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.projectsystem

import com.android.tools.idea.util.computeUserDataIfAbsent
import com.android.utils.reflection.qualifiedName
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableList
import com.intellij.openapi.Disposable
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.android.facet.AndroidFacet

/**
 * A project service providing access to various collections of source providers of a project.
 *
 * See [SourceProviderManager] to get instances of [SourceProviders].
 */
interface SourceProviders {

  /**
   * Returns the source provider for all production sources in the currently selected variant in the overlay order.
   */
  val sources: IdeaSourceProvider

  /**
   * Returns the source provider for all unit test sources in the currently selected variant in the overlay order.
   */
  val unitTestSources: IdeaSourceProvider

  /**
   * Returns the source provider for all android test sources in the currently selected variant in the overlay order.
   */
  val androidTestSources: IdeaSourceProvider

  /**
   * Returns the source provider for all test fixtures sources in the currently selected variant in the overlay order.
   */
  val testFixturesSources: IdeaSourceProvider

  /**
   * Returns the source provider for all production sources in the currently selected variant in the overlay order.
   */
  val generatedSources: IdeaSourceProvider

  /**
   * Returns the source provider for all unit test sources in the currently selected variant in the overlay order.
   */
  val generatedUnitTestSources: IdeaSourceProvider

  /**
   * Returns the source provider for all android test sources in the currently selected variant in the overlay order.
   */
  val generatedAndroidTestSources: IdeaSourceProvider

  /**
   * Returns the source provider for all test fixtures sources in the currently selected variant in the overlay order.
   */
  val generatedTestFixturesSources: IdeaSourceProvider

  /**
   * The first in the overlay order [NamedIdeaSourceProvider].
   *
   * Note: This source provider does not necessarily include all the source code required to build the module. Consider using [sources]
   *       source provider which includes all the production source files.
   */
  val mainIdeaSourceProvider: NamedIdeaSourceProvider

  /**
   * The main manifest file of the module.
   *
   * Note: A module may have multiple manifest files which are merged by the build process. Consider using [MergedManifestManager] APIs to
   *       look up data in the merged manifest.
   */
  val mainManifestFile: VirtualFile? get() = mainIdeaSourceProvider.manifestFiles.singleOrNull()

  /**
   * Returns a list of source providers, in the overlay order (meaning that later providers
   * override earlier providers when they redefine resources) for the currently selected variant.
   *
   * The overlay source order is defined by the underlying build system.
   *
   * Note: [sources] source provider represents the same set of source files in a merged form.
   */
  val currentSourceProviders: List<NamedIdeaSourceProvider>

  /**
   * Returns a list of source providers for unit test artifacts (e.g. `test/`source sets), in increasing
   * precedence order.
   *
   * @see currentSourceProviders
   */
  val currentUnitTestSourceProviders: List<NamedIdeaSourceProvider>

  /**
   * Returns a list of source providers for Android test artifacts (e.g. `androidTest/` source sets), in increasing
   * precedence order.
   *
   * @see currentSourceProviders
   */
  val currentAndroidTestSourceProviders: List<NamedIdeaSourceProvider>

  /**
   * Returns a list of source providers for test fixtures artifacts (e.g. `testFixtures/` source sets), in increasing
   * precedence order.
   *
   * @see currentSourceProviders
   */
  val currentTestFixturesSourceProviders: List<NamedIdeaSourceProvider>

  /**
   * NOTE: (In Gradle) Does not return ALL source providers!
   *
   * (In Gradle) Returns a list of all active main scope source providers (i.e. the same as [currentSourceProviders]) and additionally
   * returns frequently used inactive source providers.
   *
   * Note: Inactive source providers are not configured as project source roots and do not necessarily represent directories
   *       under a configured content entry.
   *
   * Note: Does not include test scope source providers.
   *
   * Use this method only if absolutely necessary and consider using [currentSourceProviders] where possible.
   */
  val currentAndSomeFrequentlyUsedInactiveSourceProviders: List<NamedIdeaSourceProvider>

  /**
   * Returns a list of source providers which includes the main source provider and
   * product flavor specific source providers.
   *
   * DEPRECATED: This is method is added here to support android-kotlin-extensions which
   * for compatibility reasons require this particular subset of source providers.
   */
  @Deprecated("Do not use. This is unlikely to be what anybody needs.")
  val mainAndFlavorSourceProviders: List<NamedIdeaSourceProvider>

  companion object {
    @JvmStatic
    fun getInstance(facet: AndroidFacet) = facet.sourceProviders

    /**
     * Replaces the instances of SourceProviderManager for the given [facet] with a test stub based on a single source set [sourceSet].
     *
     * NOTE: The test instance is automatically discarded on any relevant change to the [facet].
     */
    @JvmStatic
    fun replaceForTest(facet: AndroidFacet, disposable: Disposable, sourceSet: NamedIdeaSourceProvider) {
      facet.putUserData(KEY_FOR_TEST, object : SourceProviders {
        override val sources: IdeaSourceProvider
          get() = sourceSet
        override val unitTestSources: IdeaSourceProvider
          get() = throw UnsupportedOperationException()
        override val androidTestSources: IdeaSourceProvider
          get() = throw UnsupportedOperationException()
        override val testFixturesSources: IdeaSourceProvider
          get() = throw UnsupportedOperationException()
        override val generatedSources: IdeaSourceProvider =
          createMergedSourceProvider(ScopeType.MAIN, emptyList())
        override val generatedUnitTestSources: IdeaSourceProvider
          get() = throw UnsupportedOperationException()
        override val generatedAndroidTestSources: IdeaSourceProvider
          get() = throw UnsupportedOperationException()
        override val generatedTestFixturesSources: IdeaSourceProvider
          get() = throw UnsupportedOperationException()
        override val currentSourceProviders: List<NamedIdeaSourceProvider>
          get() = ImmutableList.of(sourceSet)
        override val currentUnitTestSourceProviders: List<NamedIdeaSourceProvider>
          get() = throw UnsupportedOperationException()
        override val currentAndroidTestSourceProviders: List<NamedIdeaSourceProvider>
          get() = throw UnsupportedOperationException()
        override val currentTestFixturesSourceProviders: List<NamedIdeaSourceProvider>
          get() = throw UnsupportedOperationException()
        override val currentAndSomeFrequentlyUsedInactiveSourceProviders: List<NamedIdeaSourceProvider>
          get() = ImmutableList.of(sourceSet)
        @Suppress("OverridingDeprecatedMember")
        override val mainAndFlavorSourceProviders: List<NamedIdeaSourceProvider>
          get() = throw UnsupportedOperationException()
        override val mainIdeaSourceProvider: NamedIdeaSourceProvider
          get() = sourceSet
        override val mainManifestFile: VirtualFile?
          get() = sourceSet.manifestFiles.single()
      })
      Disposer.register(disposable, Disposable { facet.putUserData(KEY_FOR_TEST, null) })
    }

    /**
     * Replaces the instances of SourceProviderManager for the given [facet] with a test stub based on a [manifestFile] only.
     *
     * NOTE: The test instance is automatically discarded on any relevant change to the [facet].
     */
    @JvmStatic
    fun replaceForTest(facet: AndroidFacet, disposable: Disposable, manifestFile: VirtualFile?) {
      facet.putUserData(KEY_FOR_TEST, object : SourceProviders {
        override val sources: IdeaSourceProvider
          get() = throw UnsupportedOperationException()
        override val unitTestSources: IdeaSourceProvider
          get() = throw UnsupportedOperationException()
        override val androidTestSources: IdeaSourceProvider
          get() = throw UnsupportedOperationException()
        override val testFixturesSources: IdeaSourceProvider
          get() = throw UnsupportedOperationException()
        override val generatedSources: IdeaSourceProvider
          get() = throw UnsupportedOperationException()
        override val generatedUnitTestSources: IdeaSourceProvider
          get() = throw UnsupportedOperationException()
        override val generatedAndroidTestSources: IdeaSourceProvider
          get() = throw UnsupportedOperationException()
        override val generatedTestFixturesSources: IdeaSourceProvider
          get() = throw UnsupportedOperationException()
        override val currentSourceProviders: List<NamedIdeaSourceProvider>
          get() = throw UnsupportedOperationException()
        override val currentUnitTestSourceProviders: List<NamedIdeaSourceProvider>
          get() = throw UnsupportedOperationException()
        override val currentAndroidTestSourceProviders: List<NamedIdeaSourceProvider>
          get() = throw UnsupportedOperationException()
        override val currentTestFixturesSourceProviders: List<NamedIdeaSourceProvider>
          get() = throw UnsupportedOperationException()
        override val currentAndSomeFrequentlyUsedInactiveSourceProviders: List<NamedIdeaSourceProvider>
          get() = throw UnsupportedOperationException()
        @Suppress("OverridingDeprecatedMember")
        override val mainAndFlavorSourceProviders: List<NamedIdeaSourceProvider>
          get() = throw UnsupportedOperationException()
        override val mainIdeaSourceProvider: NamedIdeaSourceProvider
          get() = throw UnsupportedOperationException()
        override val mainManifestFile: VirtualFile?
          get() = manifestFile
      })
      Disposer.register(disposable, Disposable { facet.putUserData(KEY_FOR_TEST, null) })
    }
  }
}

val AndroidFacet.sourceProviders: SourceProviders get() = getUserData(KEY_FOR_TEST) ?: getSourceProviderFor(this).value

private val KEY: Key<CachedValue<SourceProviders>> = Key.create(::KEY.qualifiedName)
private val KEY_FOR_TEST: Key<SourceProviders> = Key.create(::KEY_FOR_TEST.qualifiedName)

private fun getSourceProviderFor(facet: AndroidFacet): CachedValue<SourceProviders> {
  return facet.computeUserDataIfAbsent(KEY) {
    val project = facet.module.project
    CachedValuesManager.getManager(project).createCachedValue {
      val value = project.getProjectSystem().getSourceProvidersFactory().createSourceProvidersFor(facet)
        ?: createSourceProvidersForLegacyModule(facet)
      CachedValueProvider.Result.create(
        value,
        ProjectRootManager.getInstance(project),
        ProjectSyncModificationTracker.getInstance(project)
      )
    }
  }
}

fun createMergedSourceProvider(scopeType: ScopeType, providers: List<NamedIdeaSourceProvider>): IdeaSourceProvider {
  // Note: In non-Gradle project systems the list of merged source providers may consist of source providers of different types.
  //       This is because they may be re-used between main and tests scopes.
  return IdeaSourceProviderImpl(
    scopeType,
    core = object : IdeaSourceProviderImpl.Core {
      override val manifestFileUrls get() = providers.asSequence().map { it.manifestFileUrls.asSequence() }.flatten()
      override val manifestDirectoryUrls get() = providers.asSequence().map { it.manifestDirectoryUrls.asSequence() }.flatten()
      override val javaDirectoryUrls get() = providers.asSequence().map { it.javaDirectoryUrls.asSequence() }.flatten()
      override val kotlinDirectoryUrls get() = providers.asSequence().map { it.kotlinDirectoryUrls.asSequence() }.flatten()
      override val resourcesDirectoryUrls get() = providers.asSequence().map { it.resourcesDirectoryUrls.asSequence() }.flatten()
      override val aidlDirectoryUrls get() = providers.asSequence().map { it.aidlDirectoryUrls.asSequence() }.flatten()
      override val renderscriptDirectoryUrls get() = providers.asSequence().map { it.renderscriptDirectoryUrls.asSequence() }.flatten()
      override val jniLibsDirectoryUrls get() = providers.asSequence().map { it.jniLibsDirectoryUrls.asSequence() }.flatten()
      override val resDirectoryUrls get() = providers.asSequence().map { it.resDirectoryUrls.asSequence() }.flatten()
      override val assetsDirectoryUrls get() = providers.asSequence().map { it.assetsDirectoryUrls.asSequence() }.flatten()
      override val shadersDirectoryUrls get() = providers.asSequence().map { it.shadersDirectoryUrls.asSequence() }.flatten()
      override val mlModelsDirectoryUrls get() = providers.asSequence().map { it.mlModelsDirectoryUrls.asSequence() }.flatten()
      override val customSourceDirectories: Map<String, Sequence<String>> =
        providers.asSequence().flatMap { it.custom.keys }.toSet().associateWith { customKey ->
          providers.asSequence().map { it.custom[customKey]!!.directoryUrls.asSequence() }.flatten()
        }
      override val baselineProfileDirectoryUrls: Sequence<String> get() = providers.asSequence().map { it.baselineProfileDirectoryUrls.asSequence() }.flatten()
    }
  )
}

fun emptySourceProvider(scopeType: ScopeType): IdeaSourceProvider {
  return object : IdeaSourceProvider {
    override val scopeType: ScopeType = scopeType
    override val manifestFileUrls: Iterable<String> = emptyList()
    override val manifestDirectoryUrls: Iterable<String> = emptyList()
    override val javaDirectoryUrls: Iterable<String> = emptyList()
    override val kotlinDirectoryUrls: Iterable<String> = emptyList()
    override val resourcesDirectoryUrls: Iterable<String> = emptyList()
    override val aidlDirectoryUrls: Iterable<String> = emptyList()
    override val renderscriptDirectoryUrls: Iterable<String> = emptyList()
    override val jniLibsDirectoryUrls: Iterable<String> = emptyList()
    override val resDirectoryUrls: Iterable<String> = emptyList()
    override val assetsDirectoryUrls: Iterable<String> = emptyList()
    override val shadersDirectoryUrls: Iterable<String> = emptyList()
    override val mlModelsDirectoryUrls: Iterable<String> = emptyList()
    override val baselineProfileDirectoryUrls: Iterable<String> = emptyList()
    override val manifestFiles: Iterable<VirtualFile> = emptyList()
    override val manifestDirectories: Iterable<VirtualFile> = emptyList()
    override val javaDirectories: Iterable<VirtualFile> = emptyList()
    override val kotlinDirectories: Iterable<VirtualFile> = emptyList()
    override val resourcesDirectories: Iterable<VirtualFile> = emptyList()
    override val aidlDirectories: Iterable<VirtualFile> = emptyList()
    override val renderscriptDirectories: Iterable<VirtualFile> = emptyList()
    override val jniLibsDirectories: Iterable<VirtualFile> = emptyList()
    override val resDirectories: Iterable<VirtualFile> = emptyList()
    override val assetsDirectories: Iterable<VirtualFile> = emptyList()
    override val shadersDirectories: Iterable<VirtualFile> = emptyList()
    override val mlModelsDirectories: Iterable<VirtualFile> = emptyList()
    override val custom: Map<String, IdeaSourceProvider.Custom> get() = emptyMap()
    override val baselineProfileDirectories: Iterable<VirtualFile> = emptyList()
  }
}

/**
 * Returns a list of all source providers that contain, or are contained by, the given file.
 * For example, with the file structure:
 *
 * ```
 * src
 *   main
 *     aidl
 *       myfile.aidl
 *   free
 *     aidl
 *       myoverlay.aidl
 * ```
 *
 * With target file == "myoverlay.aidl" the returned list would be ['free'], but if target file == "src",
 * the returned list would be ['main', 'free'] since both of those source providers have source folders which
 * are descendants of "src."
 *
 * Returns `null` if none found.
 */
fun SourceProviders.getForFile(targetFolder: VirtualFile?): List<NamedIdeaSourceProvider>? {
  return if (targetFolder != null) {
    // Add source providers that contain the file (if any) and any that have files under the given folder
    currentAndSomeFrequentlyUsedInactiveSourceProviders
      .filter { provider -> provider.containsFile(targetFolder) || provider.isContainedBy(targetFolder) }
      .takeUnless { it.isEmpty() }
  }
  else null
}

@VisibleForTesting
fun IdeaSourceProvider.isContainedBy(targetFolder: VirtualFile): Boolean {
  return manifestFileUrls.any { manifestFileUrl -> VfsUtilCore.isEqualOrAncestor(targetFolder.url, manifestFileUrl) } ||
         allSourceFolderUrls.any { sourceFolderUrl -> VfsUtilCore.isEqualOrAncestor(targetFolder.url, sourceFolderUrl) }
}


private val IdeaSourceProvider.allSourceFolderUrls: Sequence<String>
  get() =
    arrayOf(
      javaDirectoryUrls,
      resDirectoryUrls,
      aidlDirectoryUrls,
      renderscriptDirectoryUrls,
      assetsDirectoryUrls,
      jniLibsDirectoryUrls
    )
      .asSequence()
      .flatten()

/**
 * Returns true if this SourceProvider has one or more source folders contained by (or equal to)
 * the given folder.
 */
fun IdeaSourceProvider.containsFile(file: VirtualFile): Boolean = findSourceRoot(file) != null

/**
 * Returns the source root as a VirualFile that includes the given [file]. If the [file] is not present in
 * the [IdeaSourceProvider] then this method will return null.
 */
fun IdeaSourceProvider.findSourceRoot(file: VirtualFile): VirtualFile? {
  if (manifestFiles.contains(file) || manifestDirectories.contains(file)) {
    return file
  }

  for (container in allSourceFolders) {
    // Don't do ancestry checking if this file doesn't exist
    if (!container.exists()) {
      continue
    }

    if (VfsUtilCore.isAncestor(container, file, false /* allow them to be the same */)) {
      return container
    }
  }
  return null
}

fun <T : IdeaSourceProvider> Iterable<T>.findByFile(file: VirtualFile): T? = firstOrNull { it.containsFile(file) }

fun isTestFile(facet: AndroidFacet, candidate: VirtualFile): Boolean {
  return SourceProviders.getInstance(facet).unitTestSources.containsFile(candidate) ||
         SourceProviders.getInstance(facet).androidTestSources.containsFile(candidate)
}

/** Returns true if the given candidate file is a manifest file in the given module  */
fun AndroidFacet.isManifestFile(candidate: VirtualFile): Boolean {
  return SourceProviders.getInstance(this).sources.manifestFiles.contains(candidate)
}

/** Returns the manifest files in the given module  */
fun AndroidFacet.getManifestFiles(): List<VirtualFile> = SourceProviders.getInstance(this).sources.manifestFiles.toList()

val IdeaSourceProvider.allSourceFolders: Sequence<VirtualFile>
  get() =
    arrayOf(
      javaDirectories,
      kotlinDirectories,
      resDirectories,
      aidlDirectories,
      renderscriptDirectories,
      assetsDirectories,
      jniLibsDirectories
    )
      .asSequence()
      .flatten()

