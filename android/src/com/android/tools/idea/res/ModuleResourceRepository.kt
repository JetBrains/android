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
import com.android.tools.idea.res.ResourceFolderRegistry.Companion.getInstance
import com.android.tools.res.LocalResourceRepository
import com.android.utils.TraceUtils
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.MoreObjects
import com.google.common.collect.ImmutableList
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.ResourceFolderManager
import org.jetbrains.android.facet.ResourceFolderManager.Companion.getInstance
import org.jetbrains.android.facet.ResourceFolderManager.ResourceFolderListener
import org.jetbrains.annotations.TestOnly
import java.util.function.Consumer

/**
 * @see StudioResourceRepositoryManager.getModuleResources
 */
internal class ModuleResourceRepository private constructor(
  private val myFacet: AndroidFacet,
  private val myNamespace: ResourceNamespace,
  delegates: List<LocalResourceRepository<VirtualFile>>
) : MemoryTrackingMultiResourceRepository(
  myFacet.module.name
), SingleNamespaceResourceRepository {
  private val myRegistry = getInstance(myFacet.module.project)

  init {
    setChildren(delegates, ImmutableList.of(), ImmutableList.of())

    val resourceFolderListener: ResourceFolderListener = object : ResourceFolderListener {
      override fun foldersChanged(facet: AndroidFacet, folders: List<VirtualFile>) {
        if (facet.module === myFacet.module) {
          updateRoots(folders)
        }
      }
    }
    myFacet.module.project.messageBus.connect(this).subscribe(ResourceFolderManager.TOPIC, resourceFolderListener)
  }

  @VisibleForTesting
  fun updateRoots(resourceDirectories: List<VirtualFile>) {
    ResourceUpdateTracer.logDirect {
      TraceUtils.getSimpleId(this) + ".updateRoots(" + ResourceUpdateTracer.pathsForLogging(
        resourceDirectories,
        myFacet.module.project
      ) + ")"
    }
    // Non-folder repositories to put in front of the list.
    var other: MutableList<LocalResourceRepository<VirtualFile?>?>? = null

    // Compute current roots.
    val map: MutableMap<VirtualFile, ResourceFolderRepository> = HashMap()
    val children = localResources
    for (repository in children) {
      if (repository is ResourceFolderRepository) {
        val folderRepository = repository
        val resourceDir = folderRepository.resourceDir
        map[resourceDir] = folderRepository
      } else {
        assert(repository is DynamicValueResourceRepository)
        if (other == null) {
          other = ArrayList()
        }
        other.add(repository)
      }
    }

    // Compute new resource directories (it's possible for just the order to differ, or
    // for resource dirs to have been added and/or removed).
    val newDirs: Set<VirtualFile> = HashSet(resourceDirectories)
    val resources: MutableList<LocalResourceRepository<VirtualFile?>?> = ArrayList(newDirs.size + (other?.size ?: 0))
    if (other != null) {
      resources.addAll(other)
    }

    for (dir in resourceDirectories) {
      var repository = map[dir]
      if (repository == null) {
        repository = myRegistry[myFacet, dir]
      } else {
        map.remove(dir)
      }
      resources.add(repository)
    }

    if (resources == children) {
      // Nothing changed (including order); nothing to do
      assert(
        map.isEmpty() // shouldn't have created any new ones
      )
      return
    }

    for (removed in map.values) {
      removed.removeParent(this)
    }

    setChildren(resources, ImmutableList.of(), ImmutableList.of())
  }

  override fun getNamespace(): ResourceNamespace {
    return myNamespace
  }

  override fun getPackageName(): String? {
    return ResourceRepositoryImplUtil.getPackageName(myNamespace, myFacet)
  }

  override fun toString(): String {
    return MoreObjects.toStringHelper(this)
      .toString()
  }

  companion object {
    /**
     * Creates a new resource repository for the given module, **not** including its dependent modules.
     *
     *
     * The returned repository needs to be registered with a [com.intellij.openapi.Disposable] parent.
     *
     * @param facet the facet for the module
     * @param namespace the namespace for the repository
     * @return the resource repository
     */
    @JvmStatic
    fun forMainResources(facet: AndroidFacet, namespace: ResourceNamespace): LocalResourceRepository<VirtualFile> {
      val resourceFolderRegistry = getInstance(facet.module.project)
      val folderManager = getInstance(facet)
      val resourceDirectories = folderManager.folders

      if (!AndroidModel.isRequired(facet)) {
        if (resourceDirectories.isEmpty()) {
          return EmptyRepository(namespace)
        }
        val childRepositories: MutableList<LocalResourceRepository<VirtualFile>> = ArrayList(resourceDirectories.size)
        addRepositoriesInReverseOverlayOrder(resourceDirectories, childRepositories, facet, resourceFolderRegistry)
        return ModuleResourceRepository(
          facet,
          namespace,
          childRepositories
        )
      }


      val dynamicResources = DynamicValueResourceRepository.create(facet, namespace)
      val moduleRepository: ModuleResourceRepository

      try {
        val childRepositories: MutableList<LocalResourceRepository<VirtualFile>> = ArrayList(1 + resourceDirectories.size)
        childRepositories.add(dynamicResources)
        addRepositoriesInReverseOverlayOrder(resourceDirectories, childRepositories, facet, resourceFolderRegistry)
        moduleRepository = ModuleResourceRepository(facet, namespace, childRepositories)
      } catch (t: Throwable) {
        Disposer.dispose(dynamicResources)
        throw t
      }

      Disposer.register(moduleRepository, dynamicResources)
      return moduleRepository
    }

    /**
     * Creates a new resource repository for the given module, **not** including its dependent modules.
     *
     *
     * The returned repository needs to be registered with a [com.intellij.openapi.Disposable] parent.
     *
     * @param facet the facet for the module
     * @param namespace the namespace for the repository
     * @return the resource repository
     */
    @JvmStatic
    fun forTestResources(facet: AndroidFacet, namespace: ResourceNamespace): LocalResourceRepository<VirtualFile> {
      val resourceFolderRegistry = getInstance(facet.module.project)
      val folderManager = getInstance(facet)
      val resourceDirectories = folderManager.folders

      if (!AndroidModel.isRequired(facet) && resourceDirectories.isEmpty()) {
        return EmptyRepository(namespace)
      }

      val childRepositories: MutableList<LocalResourceRepository<VirtualFile>> = ArrayList(resourceDirectories.size)
      addRepositoriesInReverseOverlayOrder(resourceDirectories, childRepositories, facet, resourceFolderRegistry)

      return ModuleResourceRepository(facet, namespace, childRepositories)
    }

    /**
     * Inserts repositories for the given `resourceDirectories` into `childRepositories`, in the right order.
     *
     *
     * `resourceDirectories` is assumed to be in the order returned from
     * [SourceProviderManager.getCurrentSourceProviders], which is the inverse of what we need. The code in
     * [MultiResourceRepository.getMap] gives priority to child repositories which are earlier
     * in the list, so after creating repositories for every folder, we add them in reverse to the list.
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
      var i = resourceDirectories.size
      while (--i >= 0) {
        val resourceDirectory = resourceDirectories[i]
        val repository = resourceFolderRegistry[facet, resourceDirectory]
        childRepositories.add(repository)
      }
    }

    @TestOnly
    fun createForTest(
      facet: AndroidFacet,
      resourceDirectories: Collection<VirtualFile?>,
      namespace: ResourceNamespace,
      dynamicResourceValueRepository: DynamicValueResourceRepository?
    ): ModuleResourceRepository {
      assert(ApplicationManager.getApplication().isUnitTestMode)
      val delegates: MutableList<LocalResourceRepository<VirtualFile>> =
        ArrayList(resourceDirectories.size + (if (dynamicResourceValueRepository == null) 0 else 1))

      if (dynamicResourceValueRepository != null) {
        delegates.add(dynamicResourceValueRepository)
      }

      val resourceFolderRegistry = getInstance(facet.module.project)
      resourceDirectories.forEach(Consumer { dir: VirtualFile? ->
        delegates.add(
          resourceFolderRegistry[facet, dir!!, namespace]
        )
      })

      val repository = ModuleResourceRepository(facet, namespace, delegates)
      Disposer.register(facet, repository)
      return repository
    }
  }
}
