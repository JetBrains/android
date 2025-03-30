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

import com.android.annotations.concurrency.UiThread
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.tools.concurrency.AndroidIoManager
import com.android.tools.idea.model.Namespacing
import com.android.tools.idea.util.toPathString
import com.android.utils.TraceUtils.simpleId
import com.android.utils.concurrency.getAndUnwrap
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.collect.ImmutableList
import com.google.common.collect.Sets
import com.intellij.facet.ProjectFacetManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbModeTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiTreeChangeListener
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.Consumer
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.ResourceFolderManager.Companion.getInstance
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.Future
import java.util.function.BiConsumer

/**
 * A project service that manages [ResourceFolderRepository] instances, creating them as necessary
 * and reusing repositories for the same directories when multiple modules need them. For every
 * directory a namespaced and non-namespaced repository may be created, if needed.
 */
class ResourceFolderRegistry(val project: Project) : Disposable {
  private val namespacedCache = buildCache()
  private val nonNamespacedCache = buildCache()
  private val cacheList = ImmutableList.of(namespacedCache, nonNamespacedCache)

  init {
    val moduleRootListener =
      object : ModuleRootListener {
        override fun rootsChanged(event: ModuleRootEvent) {
          removeStaleEntries()
        }
      }
    val vfsListener = ResourceFolderVfsListener(this)
    val fileDocumentManagerListener = ResourceFolderFileDocumentManagerListener(this)

    with(project.messageBus.connect(this)) {
      subscribe(ModuleRootListener.TOPIC, moduleRootListener)
      subscribe(VirtualFileManager.VFS_CHANGES, vfsListener)
      subscribe(FileDocumentManagerListener.TOPIC, fileDocumentManagerListener)
    }

    EditorFactory.getInstance()
      .eventMulticaster
      .addDocumentListener(ResourceFolderDocumentListener(project, this), this)
  }

  operator fun get(facet: AndroidFacet, dir: VirtualFile) =
    get(facet, dir, StudioResourceRepositoryManager.getInstance(facet).namespace)

  @VisibleForTesting
  operator fun get(
    facet: AndroidFacet,
    dir: VirtualFile,
    namespace: ResourceNamespace,
  ): ResourceFolderRepository {
    val cache =
      if (namespace === ResourceNamespace.RES_AUTO) nonNamespacedCache else namespacedCache
    val repository = cache.getAndUnwrap(dir) { createRepository(facet, dir, namespace) }
    assert(repository.namespace == namespace)

    // TODO(b/80179120): figure out why this is not always true.
    // assert repository.getFacet().equals(facet);

    return repository.ensureLoaded()
  }

  /**
   * Returns the resource repository for the given directory, or null if such repository doesn't
   * already exist.
   */
  fun getCached(dir: VirtualFile, namespacing: Namespacing): ResourceFolderRepository? {
    val cache = if (namespacing === Namespacing.REQUIRED) namespacedCache else nonNamespacedCache
    return cache.getIfPresent(dir)
  }

  fun reset(facet: AndroidFacet) {
    ResourceUpdateTracer.logDirect { "$simpleId.reset()" }
    reset(namespacedCache, facet)
    reset(nonNamespacedCache, facet)
  }

  private fun reset(cache: Cache<VirtualFile, ResourceFolderRepository>, facet: AndroidFacet) {
    val cacheAsMap = cache.asMap()
    if (cacheAsMap.isEmpty()) {
      ResourceUpdateTracer.logDirect { "$simpleId.reset: cache is empty" }
      return
    }

    val keysToRemove =
      cacheAsMap.entries.mapNotNull { (virtualFile, repository) ->
        virtualFile.takeIf { repository.facet == facet }
      }

    keysToRemove.forEach(cache::invalidate)
  }

  private fun removeStaleEntries() {
    // TODO(namespaces): listen to changes in modules' namespacing modes and dispose repositories
    // which are no longer needed.
    ResourceUpdateTracer.logDirect { "$simpleId.removeStaleEntries()" }
    removeStaleEntries(namespacedCache)
    removeStaleEntries(nonNamespacedCache)
  }

  private fun removeStaleEntries(cache: Cache<VirtualFile, ResourceFolderRepository>) {
    val cacheAsMap = cache.asMap()
    if (cacheAsMap.isEmpty()) {
      ResourceUpdateTracer.logDirect { "$simpleId.removeStaleEntries: cache is empty" }
      return
    }
    val facets: MutableSet<AndroidFacet> = Sets.newHashSetWithExpectedSize(cacheAsMap.size)
    val newResourceFolders: MutableSet<VirtualFile> =
      Sets.newHashSetWithExpectedSize(cacheAsMap.size)
    for (repository in cacheAsMap.values) {
      val facet = repository.facet
      if (!facet.isDisposed && facets.add(facet)) {
        val folderManager = getInstance(facet)
        newResourceFolders.addAll(folderManager.folders)
      }
    }
    ResourceUpdateTracer.logDirect {
      "$simpleId.removeStaleEntries retained ${ResourceUpdateTracer.getInstance().pathsForLogging(newResourceFolders, project)}"
    }
    cacheAsMap.keys.retainAll(newResourceFolders)
  }

  override fun dispose() {
    namespacedCache.invalidateAll()
    nonNamespacedCache.invalidateAll()
  }

  fun dispatchToRepositories(
    file: VirtualFile,
    handler: BiConsumer<ResourceFolderRepository, VirtualFile>,
  ) {
    ResourceUpdateTracer.log {
      "ResourceFolderRegistry.dispatchToRepositories(${ResourceUpdateTracer.getInstance().pathForLogging(file)}, ...) VFS change"
    }
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    var dir = if (file.isDirectory) file else file.parent
    while (dir != null) {
      for (cache in cacheList) {
        cache.getIfPresent(dir)?.let { handler.accept(it, file) }
      }
      dir = dir.parent
    }
  }

  fun dispatchToRepositories(file: VirtualFile, invokeCallback: Consumer<PsiTreeChangeListener>) {
    ResourceUpdateTracer.log {
      "ResourceFolderRegistry.dispatchToRepositories(${ResourceUpdateTracer.getInstance().pathForLogging(file)}, ...) PSI change"
    }
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    var dir = if (file.isDirectory) file else file.parent
    while (dir != null) {
      for (cache in cacheList) {
        cache.getIfPresent(dir)?.let { invokeCallback.consume(it.psiListener) }
      }
      dir = dir.parent
    }
  }

  private fun buildCache(): Cache<VirtualFile, ResourceFolderRepository> {
    return CacheBuilder.newBuilder().build()
  }

  private fun createRepository(
    facet: AndroidFacet,
    dir: VirtualFile,
    namespace: ResourceNamespace,
  ): ResourceFolderRepository {
    // Don't create a persistent cache in tests to avoid unnecessary overhead.
    val executor =
      if (ApplicationManager.getApplication().isUnitTestMode) Executor { _: Runnable? -> }
      else AndroidIoManager.getInstance().getBackgroundDiskIoExecutor()
    val cachingData =
      ResourceFolderRepositoryFileCacheService.get()
        .getCachingData(facet.module.project, dir, executor)
    return ResourceFolderRepository.create(facet, dir, namespace, cachingData)
  }

  companion object {

    @JvmStatic fun getInstance(project: Project): ResourceFolderRegistry = project.service()
  }

  /** Populate the registry's in-memory ResourceFolderRepository caches (if not already cached). */
  class PopulateCachesTask(private val myProject: Project) : DumbModeTask() {

    override fun tryMergeWith(taskFromQueue: DumbModeTask): DumbModeTask? =
      if (taskFromQueue is PopulateCachesTask && taskFromQueue.myProject == myProject) this
      else null

    override fun performInDumbMode(indicator: ProgressIndicator) {
      val facets = ProjectFacetManager.getInstance(myProject).getFacets(AndroidFacet.ID)
      if (facets.isEmpty()) {
        return
      }
      // Some directories in the registry may already be populated by this point, so filter them
      // out.
      indicator.text = "Indexing resources"
      indicator.isIndeterminate = false
      val resDirectories = getResourceDirectoriesForFacets(facets)
      // Might already be done, as there can be a race for filling the memory caches.
      if (resDirectories.isEmpty()) {
        return
      }

      // Make sure the cache root is created before parallel execution to avoid racing to create the
      // root.
      try {
        ResourceFolderRepositoryFileCacheService.get().createDirForProject(myProject)
      } catch (e: IOException) {
        return
      }
      val application = ApplicationManager.getApplication()
      assert(!application.isWriteAccessAllowed)
      var numDone = 0
      val parallelExecutor = AndroidIoManager.getInstance().getBackgroundDiskIoExecutor()
      val repositoryJobs: MutableList<Future<ResourceFolderRepository>> = ArrayList()
      for ((dir, facet) in resDirectories) {
        val registry = getInstance(myProject)
        repositoryJobs.add(
          parallelExecutor.submit<ResourceFolderRepository> { registry[facet, dir] }
        )
      }
      for (job in repositoryJobs) {
        if (indicator.isCanceled) {
          break
        }
        indicator.fraction = numDone.toDouble() / resDirectories.size
        try {
          job.get()
        } catch (e: ExecutionException) {
          // If we get an exception, that's okay -- we stop pre-populating the cache, which is just
          // for performance.
        } catch (e: InterruptedException) {
          Thread.currentThread().interrupt()
        }
        ++numDone
      }
    }
  }
}

private class ResourceFolderDocumentListener(
  private val project: Project,
  private val registry: ResourceFolderRegistry,
) : DocumentListener {

  override fun documentChanged(event: DocumentEvent) {
    // Note that event may arrive from any project, not only from the project parameter.
    // The project parameter can be temporarily disposed in light tests.
    if (project.isDisposed) return

    val document = event.document
    if (PsiDocumentManager.getInstance(project).getCachedPsiFile(document) == null) {
      val virtualFile = FileDocumentManager.getInstance().getFile(document) ?: return
      if (virtualFile is LightVirtualFile || !isRelevantFile(virtualFile)) return

      runInWriteAction {
        registry.dispatchToRepositories(virtualFile) { repo, f -> repo.scheduleScan(f) }
      }
    }
  }

  private fun runInWriteAction(runnable: Runnable) {
    val application = ApplicationManager.getApplication()
    if (application.isWriteAccessAllowed) runnable.run()
    else application.invokeLater { application.runWriteAction(runnable) }
  }
}

/**
 * [BulkFileListener] which handles [VFileEvent]s for resource folder. When an event happens on a
 * file within a folder with a corresponding [ResourceFolderRepository], the event is delegated to
 * it.
 */
private class ResourceFolderVfsListener(private val registry: ResourceFolderRegistry) :
  BulkFileListener {
  @UiThread
  override fun before(events: List<VFileEvent>) {
    for (event in events) {
      when (event) {
        is VFileMoveEvent -> onFileOrDirectoryRemoved(event.file)
        is VFileDeleteEvent -> onFileOrDirectoryRemoved(event.file)
        is VFilePropertyChangeEvent -> if (event.isRename) onFileOrDirectoryRemoved(event.file)
      }
    }
  }

  override fun after(events: List<VFileEvent>) {
    for (event in events) {
      when (event) {
        is VFileCreateEvent -> onFileOrDirectoryCreated(event.parent, event.childName)
        is VFileCopyEvent -> onFileOrDirectoryCreated(event.newParent, event.newChildName)
        is VFileMoveEvent -> onFileOrDirectoryCreated(event.newParent, event.file.name)
        is VFilePropertyChangeEvent ->
          if (event.isRename) {
            event.file.parent?.let { onFileOrDirectoryCreated(it, event.newValue as String) }
          }
      // VFileContentChangeEvent changes are not handled at the VFS level, but either in
      // fileWithNoDocumentChanged, documentChanged or MyPsiListener.
      }
    }
  }

  private fun onFileOrDirectoryCreated(parent: VirtualFile?, childName: String) {
    ResourceUpdateTracer.log {
      val pathToLog =
        if (parent == null) childName
        else
          ResourceUpdateTracer.getInstance()
            .pathForLogging(parent.toPathString().resolve(childName), registry.project)
      "ResourceFolderVfsListener.onFileOrDirectoryCreated($pathToLog)"
    }
    val created = parent?.takeIf(VirtualFile::exists)?.findChild(childName) ?: return
    val resDir = if (created.isDirectory) parent else parent.parent ?: return

    registry.dispatchToRepositories(resDir) { repo, _ -> onFileOrDirectoryCreated(created, repo) }
  }

  private fun onFileOrDirectoryRemoved(file: VirtualFile) {
    registry.dispatchToRepositories(file) { repo, f -> repo.onFileOrDirectoryRemoved(f) }
  }

  companion object {
    private fun onFileOrDirectoryCreated(
      created: VirtualFile,
      repository: ResourceFolderRepository?,
    ) {
      if (repository == null) return

      ResourceUpdateTracer.log {
        "ResourceFolderVfsListener.onFileOrDirectoryCreated($created, ${repository.displayName})"
      }
      if (!created.isDirectory) {
        repository.onFileCreated(created)
      } else {
        // ResourceFolderRepository doesn't handle event on a whole folder, so we pass all the
        // children.
        for (child in created.children) {
          if (!child.isDirectory) {
            // There is no need to visit subdirectories because Android does not support them.
            // If a base resource directory is created (e.g res/), a whole
            // ResourceFolderRepository will be created separately, so we don't need to handle
            // this case here.
            repository.onFileCreated(child)
          }
        }
      }
    }
  }
}

private class ResourceFolderFileDocumentManagerListener(
  private val registry: ResourceFolderRegistry
) : FileDocumentManagerListener {
  override fun fileWithNoDocumentChanged(file: VirtualFile) {
    registry.dispatchToRepositories(file) { repo, f -> repo.scheduleScan(f) }
  }
}
