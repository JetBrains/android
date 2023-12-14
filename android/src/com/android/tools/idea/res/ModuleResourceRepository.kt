/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.res

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.SingleNamespaceResourceRepository
import com.android.tools.idea.model.AndroidModel
import com.android.tools.res.LocalResourceRepository
import com.android.utils.TraceUtils.simpleId
import com.google.common.base.MoreObjects
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.ResourceFolderManager
import org.jetbrains.android.facet.ResourceFolderManager.ResourceFolderListener
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting

/** @see StudioResourceRepositoryManager.getModuleResources */
@VisibleForTesting
class ModuleResourceRepository
private constructor(
  private val facet: AndroidFacet,
  parentDisposable: Disposable,
  private val namespace: ResourceNamespace,
  delegates: List<LocalResourceRepository<VirtualFile>>
) :
  MemoryTrackingMultiResourceRepository(parentDisposable, facet.module.name),
  SingleNamespaceResourceRepository {

  private val registry = ResourceFolderRegistry.getInstance(facet.module.project)

  init {
    setChildren(delegates, emptyList(), emptyList())

    val resourceFolderListener = ResourceFolderListener { facet, folders ->
      if (facet.module === this.facet.module) {
        refreshChildren(folders)
      }
    }

    facet.module.project.messageBus
      .connect(this)
      .subscribe(ResourceFolderManager.TOPIC, resourceFolderListener)
  }

  override fun refreshChildren() {
    // There are no current scenarios where this function will be called on
    // ModuleResourceRepository.
    // If such a scenario needs to be implemented in the future, it is possible to fetch the current
    // list of folders from `ResourceFolderManager`.
    throw NotImplementedError()
  }

  @VisibleForTesting
  fun refreshChildren(resourceDirectories: List<VirtualFile>) {
    ResourceUpdateTracer.logDirect {
      "$simpleId.refreshChildren(${ResourceUpdateTracer.pathsForLogging(resourceDirectories, facet.module.project)})"
    }

    // Non-folder repositories to put in front of the list.
    val other: MutableList<LocalResourceRepository<VirtualFile>> = mutableListOf()

    // Compute current roots.
    val map: MutableMap<VirtualFile, ResourceFolderRepository> = mutableMapOf()
    for (repository in localResources) {
      when (repository) {
        is ResourceFolderRepository -> map[repository.resourceDir] = repository
        is DynamicValueResourceRepository -> other.add(repository)
        else -> throw IllegalStateException("Unrecognized repository: $repository")
      }
    }

    // Compute new resource directories (it's possible for just the order to differ, or
    // for resource dirs to have been added and/or removed).
    val resources: MutableList<LocalResourceRepository<VirtualFile>> =
      ArrayList(resourceDirectories.size + other.size)
    resources.addAll(other)

    for (dir in resourceDirectories) {
      val repository = map.remove(dir) ?: registry[facet, dir]
      resources.add(repository)
    }

    if (resources == children) {
      // Nothing changed (including order); nothing to do
      assert(map.isEmpty()) // shouldn't have created any new ones
      return
    }

    for (removed in map.values) {
      removed.removeParent(this)
    }

    setChildren(resources, emptyList(), emptyList())
  }

  override fun getNamespace() = namespace

  override fun getPackageName() = ResourceRepositoryImplUtil.getPackageName(namespace, facet)

  override fun toString() = MoreObjects.toStringHelper(this).toString()

  override fun getNamespaces(): MutableSet<ResourceNamespace> =
    super<MemoryTrackingMultiResourceRepository>.getNamespaces()

  override fun getLeafResourceRepositories(): MutableCollection<SingleNamespaceResourceRepository> =
    super<MemoryTrackingMultiResourceRepository>.getLeafResourceRepositories()

  companion object {
    /**
     * Creates a new resource repository for the given module, **not** including its dependent
     * modules.
     *
     * The returned repository needs to be registered with a [com.intellij.openapi.Disposable]
     * parent.
     *
     * @param facet the facet for the module
     * @param namespace the namespace for the repository
     * @return the resource repository
     */
    @JvmStatic
    fun forMainResources(
      facet: AndroidFacet,
      parentDisposable: Disposable,
      namespace: ResourceNamespace
    ): LocalResourceRepository<VirtualFile> {
      val resourceFolderRegistry = ResourceFolderRegistry.getInstance(facet.module.project)
      val resourceDirectories = ResourceFolderManager.getInstance(facet).folders

      if (!AndroidModel.isRequired(facet)) {
        if (resourceDirectories.isEmpty()) {
          return EmptyRepository(namespace)
        }
        val childRepositories: MutableList<LocalResourceRepository<VirtualFile>> =
          ArrayList(resourceDirectories.size)
        addRepositoriesInReverseOverlayOrder(
          resourceDirectories,
          childRepositories,
          facet,
          resourceFolderRegistry
        )
        return ModuleResourceRepository(facet, parentDisposable, namespace, childRepositories)
      }

      val dynamicResources = DynamicValueResourceRepository.create(facet, namespace)
      val moduleRepository: ModuleResourceRepository =
        try {
          val childRepositories: MutableList<LocalResourceRepository<VirtualFile>> =
            ArrayList(1 + resourceDirectories.size)
          childRepositories.add(dynamicResources)
          addRepositoriesInReverseOverlayOrder(
            resourceDirectories,
            childRepositories,
            facet,
            resourceFolderRegistry
          )
          ModuleResourceRepository(facet, parentDisposable, namespace, childRepositories)
        } catch (t: Throwable) {
          Disposer.dispose(dynamicResources)
          throw t
        }

      Disposer.register(moduleRepository, dynamicResources)
      return moduleRepository
    }

    /**
     * Creates a new resource repository for the given module, **not** including its dependent
     * modules.
     *
     * The returned repository needs to be registered with a [com.intellij.openapi.Disposable]
     * parent.
     *
     * @param facet the facet for the module
     * @param namespace the namespace for the repository
     * @return the resource repository
     */
    @JvmStatic
    fun forTestResources(
      facet: AndroidFacet,
      parentDisposable: Disposable,
      namespace: ResourceNamespace
    ): LocalResourceRepository<VirtualFile> {
      val resourceFolderRegistry = ResourceFolderRegistry.getInstance(facet.module.project)
      val folderManager = ResourceFolderManager.getInstance(facet)
      val resourceDirectories = folderManager.folders

      if (!AndroidModel.isRequired(facet) && resourceDirectories.isEmpty()) {
        return EmptyRepository(namespace)
      }

      val childRepositories: MutableList<LocalResourceRepository<VirtualFile>> =
        ArrayList(resourceDirectories.size)
      addRepositoriesInReverseOverlayOrder(
        resourceDirectories,
        childRepositories,
        facet,
        resourceFolderRegistry
      )

      return ModuleResourceRepository(facet, parentDisposable, namespace, childRepositories)
    }

    /**
     * Inserts repositories for the given `resourceDirectories` into `childRepositories`, in the
     * right order.
     *
     * `resourceDirectories` is assumed to be in the order returned from
     * [SourceProviderManager.getCurrentSourceProviders], which is the inverse of what we need. The
     * code in [MultiResourceRepository.getMap] gives priority to child repositories which are
     * earlier in the list, so after creating repositories for every folder, we add them in reverse
     * to the list.
     *
     * @param resourceDirectories directories for which repositories should be constructed
     * @param childRepositories the list of repositories to which new repositories will be added
     * @param facet [AndroidFacet] that repositories will correspond to
     * @param resourceFolderRegistry [ResourceFolderRegistry] used to construct the repositories
     */
    private fun addRepositoriesInReverseOverlayOrder(
      resourceDirectories: List<VirtualFile>,
      childRepositories: MutableList<LocalResourceRepository<VirtualFile>>,
      facet: AndroidFacet,
      resourceFolderRegistry: ResourceFolderRegistry
    ) {
      for (resourceDirectory in resourceDirectories.asReversed()) {
        val repository = resourceFolderRegistry[facet, resourceDirectory]
        childRepositories.add(repository)
      }
    }

    @TestOnly
    @JvmStatic
    fun createForTest(
      facet: AndroidFacet,
      resourceDirectories: Collection<VirtualFile>,
      namespace: ResourceNamespace,
      dynamicResourceValueRepository: DynamicValueResourceRepository?
    ): ModuleResourceRepository {
      assert(ApplicationManager.getApplication().isUnitTestMode)
      val delegates: MutableList<LocalResourceRepository<VirtualFile>> = mutableListOf()

      if (dynamicResourceValueRepository != null) delegates.add(dynamicResourceValueRepository)

      val resourceFolderRegistry = ResourceFolderRegistry.getInstance(facet.module.project)
      for (dir in resourceDirectories) {
        delegates.add(resourceFolderRegistry[facet, dir, namespace])
      }

      return ModuleResourceRepository(facet, parentDisposable = facet, namespace, delegates)
    }
  }
}
