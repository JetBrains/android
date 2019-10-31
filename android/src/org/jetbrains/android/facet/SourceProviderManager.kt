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
package org.jetbrains.android.facet

import com.android.builder.model.SourceProvider
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.projectsystem.IdeaSourceProvider
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.projectsystem.SourceProviders
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.getProjectSystem
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

interface SourceProviderManager {
  companion object {
    @JvmStatic
    fun getInstance(facet: AndroidFacet) = facet.sourceProviderManager

    /**
     * Replaces the instances of SourceProviderManager for the given [facet] with a test stub based on a single source set [sourceSet].
     *
     * NOTE: The test instance is automatically discarded on any relevant change to the [facet].
     */
    @JvmStatic
    fun replaceForTest(facet: AndroidFacet, disposable: Disposable, sourceSet: IdeaSourceProvider) {
      facet.putUserData(KEY, object: SourceProviders {
        override val currentSourceProviders: List<IdeaSourceProvider>
          get() = throw UnsupportedOperationException()
        override val currentTestSourceProviders: List<IdeaSourceProvider>
          get() = throw UnsupportedOperationException()
        override val allSourceProviders: List<IdeaSourceProvider>
          get() = throw UnsupportedOperationException()
        @Suppress("OverridingDeprecatedMember")
        override val mainAndFlavorSourceProviders: List<IdeaSourceProvider>
          get() = throw UnsupportedOperationException()
        override val mainIdeaSourceProvider: IdeaSourceProvider
          get() = sourceSet
        override val mainManifestFile: VirtualFile?
          get() = sourceSet.manifestFile
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
        override val currentSourceProviders: List<IdeaSourceProvider>
          get() = throw UnsupportedOperationException()
        override val currentTestSourceProviders: List<IdeaSourceProvider>
          get() = throw UnsupportedOperationException()
        override val allSourceProviders: List<IdeaSourceProvider>
          get() = throw UnsupportedOperationException()
        @Suppress("OverridingDeprecatedMember")
        override val mainAndFlavorSourceProviders: List<IdeaSourceProvider>
          get() = throw UnsupportedOperationException()
        override val mainIdeaSourceProvider: IdeaSourceProvider
          get() = throw UnsupportedOperationException()
        override val mainManifestFile: VirtualFile?
          get() = manifestFile
      })
      Disposer.register(disposable, Disposable { facet.putUserData(KEY, null) })
    }
  }
}

val AndroidFacet.sourceProviderManager: SourceProviders get() = getUserData(KEY) ?: createSourceProviderFor(this)

class SourceProvidersImpl(
  override val mainIdeaSourceProvider: IdeaSourceProvider,
  override val currentSourceProviders: List<IdeaSourceProvider>,
  override val currentTestSourceProviders: List<IdeaSourceProvider>,
  override val allSourceProviders: List<IdeaSourceProvider>,

  @Suppress("OverridingDeprecatedMember")
  override val mainAndFlavorSourceProviders: List<IdeaSourceProvider>
) : SourceProviders

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
