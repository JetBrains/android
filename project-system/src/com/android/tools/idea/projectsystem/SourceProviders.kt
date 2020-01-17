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

import com.android.utils.reflection.qualifiedName
import com.intellij.ProjectTopics
import com.intellij.facet.Facet
import com.intellij.facet.FacetManager
import com.intellij.facet.FacetManagerAdapter
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
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
  val allSourceProviders: List<NamedIdeaSourceProvider>

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
      facet.putUserData(KEY, object: SourceProviders {
        override val sources: IdeaSourceProvider
          get() = sourceSet
        override val unitTestSources: IdeaSourceProvider
          get() = throw UnsupportedOperationException()
        override val androidTestSources: IdeaSourceProvider
          get() = throw UnsupportedOperationException()
        override val currentSourceProviders: List<NamedIdeaSourceProvider>
          get() = throw UnsupportedOperationException()
        override val currentUnitTestSourceProviders: List<NamedIdeaSourceProvider>
          get() = throw UnsupportedOperationException()
        override val currentAndroidTestSourceProviders: List<NamedIdeaSourceProvider>
          get() = throw UnsupportedOperationException()
        override val allSourceProviders: List<NamedIdeaSourceProvider>
          get() = throw UnsupportedOperationException()
        @Suppress("OverridingDeprecatedMember")
        override val mainAndFlavorSourceProviders: List<NamedIdeaSourceProvider>
          get() = throw UnsupportedOperationException()
        override val mainIdeaSourceProvider: NamedIdeaSourceProvider
          get() = sourceSet
        override val mainManifestFile: VirtualFile?
          get() = sourceSet.manifestFiles.single()
      })
      Disposer.register(disposable, Disposable { facet.putUserData(KEY, null) })
    }

    /**
     * Replaces the instances of SourceProviderManager for the given [facet] with a test stub based on a [manifestFile] only.
     *
     * NOTE: The test instance is automatically discarded on any relevant change to the [facet].
     */
    @JvmStatic
    fun replaceForTest(facet: AndroidFacet, disposable: Disposable, manifestFile: VirtualFile?) {
      facet.putUserData(KEY, object: SourceProviders {
        override val sources: IdeaSourceProvider
          get() = throw UnsupportedOperationException()
        override val unitTestSources: IdeaSourceProvider
          get() = throw UnsupportedOperationException()
        override val androidTestSources: IdeaSourceProvider
          get() = throw UnsupportedOperationException()
        override val currentSourceProviders: List<NamedIdeaSourceProvider>
          get() = throw UnsupportedOperationException()
        override val currentUnitTestSourceProviders: List<NamedIdeaSourceProvider>
          get() = throw UnsupportedOperationException()
        override val currentAndroidTestSourceProviders: List<NamedIdeaSourceProvider>
          get() = throw UnsupportedOperationException()
        override val allSourceProviders: List<NamedIdeaSourceProvider>
          get() = throw UnsupportedOperationException()
        @Suppress("OverridingDeprecatedMember")
        override val mainAndFlavorSourceProviders: List<NamedIdeaSourceProvider>
          get() = throw UnsupportedOperationException()
        override val mainIdeaSourceProvider: NamedIdeaSourceProvider
          get() = throw UnsupportedOperationException()
        override val mainManifestFile: VirtualFile?
          get() = manifestFile
      })
      Disposer.register(disposable, Disposable { facet.putUserData(KEY, null) })
    }
  }
}

val AndroidFacet.sourceProviders: SourceProviders get() = getUserData(KEY) ?: createSourceProviderFor(this)

private val KEY: Key<SourceProviders> = Key.create(::KEY.qualifiedName)

private fun createSourceProviderFor(facet: AndroidFacet): SourceProviders {
  return facet.module.project.getProjectSystem().getSourceProvidersFactory().createSourceProvidersFor(facet)
         ?: createSourceProvidersForLegacyModule(facet)
}

private fun onChanged(facet: AndroidFacet) {
  facet.putUserData(KEY, createSourceProviderFor(facet))
}

private class SourceProviderManagerComponent(val project: Project) : ProjectComponent {
  private val connection = project.messageBus.connect()

  init {
    var subscribedToRootsChangedEvents = false

    @Synchronized
    fun ensureSubscribed() {
      if (!subscribedToRootsChangedEvents) {
        connection.subscribe(ProjectTopics.PROJECT_ROOTS, object : ModuleRootListener {
          override fun rootsChanged(event: ModuleRootEvent) {
            ModuleManager.getInstance(project)
              .modules.asIterable()
              .mapNotNull { it -> FacetManager.getInstance(it).getFacetByType(AndroidFacet.ID) }.forEach { facet ->
                onChanged(facet)
              }
          }
        })
        subscribedToRootsChangedEvents = true
      }
    }

    // Temporarily subscribe the component to notifications when the ProjectSystemService is available only.  Many tests are still
    // configured to run without ProjectSystemService.
    if (ServiceManager.getService(project, ProjectSystemService::class.java) != null) {
      connection.subscribe(FacetManager.FACETS_TOPIC, object : FacetManagerAdapter() {
        override fun facetConfigurationChanged(facet: Facet<*>) {
          if (facet is AndroidFacet) {
            ensureSubscribed()
            onChanged(facet)
          }
        }

        override fun facetAdded(facet: Facet<*>) {
          if (facet is AndroidFacet) {
            ensureSubscribed()
            onChanged(facet)
          }
        }
      })
    }
  }

  override fun projectClosed() {
    connection.disconnect()
  }
}

fun createMergedSourceProvider(scopeType: ScopeType, providers: List<NamedIdeaSourceProvider>): IdeaSourceProvider {
  // Note: In non-Gradle project systems the list of merged source providers may consist of source providers of different types.
  //       This is because they may be re-used between main and tests scopes.
  return IdeaSourceProviderImpl(
    scopeType,
    manifestFileUrls = providers.flatMap { it.manifestFileUrls },
    manifestDirectoryUrls = providers.flatMap { it.manifestDirectoryUrls },
    javaDirectoryUrls = providers.flatMap { it.javaDirectoryUrls },
    resourcesDirectoryUrls = providers.flatMap { it.resourcesDirectoryUrls },
    aidlDirectoryUrls = providers.flatMap { it.aidlDirectoryUrls },
    renderscriptDirectoryUrls = providers.flatMap { it.renderscriptDirectoryUrls },
    jniDirectoryUrls = providers.flatMap { it.jniDirectoryUrls },
    jniLibsDirectoryUrls = providers.flatMap { it.jniLibsDirectoryUrls },
    resDirectoryUrls = providers.flatMap { it.resDirectoryUrls },
    assetsDirectoryUrls = providers.flatMap { it.assetsDirectoryUrls },
    shadersDirectoryUrls = providers.flatMap { it.shadersDirectoryUrls }
  )
}
