/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.ide.common.util.PathString
import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import org.jetbrains.android.facet.AndroidFacet

import com.android.tools.idea.res.SampleDataResourceRepository.SampleDataRepositoryManager
import com.android.tools.idea.util.toPathString
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.*
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.util.containers.ContainerUtil
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Project-wide listener which invalidates the [SampleDataResourceRepository] corresponding to
 * any module whose sample data directory has been modified (if such a repository exists).
 *
 * A project's [SampleDataListener] is instantiated the first time a [SampleDataResourceRepository]
 * is created for one of project's modules (e.g. when the user opens a resource file or activity
 * for the first time). The listener remains for the lifetime of the project.
 *
 * When a [SampleDataResourceRepository] is created, it calls [ensureSubscribed] to make sure that
 * the project's [SampleDataListener] is tracking VFS and PSI events.
 */
internal class SampleDataListener(val project: Project) : PsiTreeChangeAdapter(), VirtualFileListener {
  private val reposToInvalidate = ContainerUtil.createWeakValueMap<PathString, SampleDataResourceRepository>()
  private var subscribed = AtomicBoolean(false)

  companion object {
    private val LOG = Logger.getInstance(SampleDataListener::class.java)

    @JvmStatic fun getInstance(project: Project) = ServiceManager.getService(project, SampleDataListener::class.java)!!

    @JvmStatic fun ensureSubscribed(project: Project) = getInstance(project).ensureSubscribed()

    @JvmStatic
    private fun SampleDataResourceRepository.invalidateBecauseOf(modifiedPath: PathString) {
      LOG.info("Invalidating SampleDataResourceRepository because $modifiedPath was modified.")
      invalidate()
    }
  }

  /**
   * Ensures that this [SampleDataListener] is listening for VFS changes and PSI changes.
   */
  fun ensureSubscribed() {
    if (subscribed.compareAndSet(false, true)) {
      VirtualFileManager.getInstance().addVirtualFileListener(this, project)
      PsiProjectListener.getInstance(project).setSampleDataListener(this)
    }
  }

  /**
   * A file is relevant to this listener if
   *   1. There's a SampleDataResourceRepository instance corresponding to the
   *      module the file belongs to that needs to be invalidated.
   *   2. The file is actually in the module's sample data directory (as opposed
   *      to just having FD_SAMPLE_DATA in its path somewhere).
   */
  private fun isRelevant(file: VirtualFile, facet: AndroidFacet) =
    !facet.isDisposed && SampleDataRepositoryManager.getInstance(facet).hasRepository() && facet.module.isSampleDataFile(file)

  /**
   * Used to fail fast when we can quickly tell that a file has nothing to do with sample data.
   */
  private fun isPossiblyRelevant(file: VirtualFile) = file.extension.let { it != "java" && it != "xml" }

  /**
   * If a modification of the given [file] means that a [SampleDataResourceRepository] should
   * be invalidated, this function returns that repository. Otherwise, it returns null.
   */
  private fun findRepoToInvalidate(file: VirtualFile): SampleDataResourceRepository? {
    if (!isPossiblyRelevant(file)) return null
    val facet = AndroidFacet.getInstance(file, project) ?: return null

    return if (isRelevant(file, facet)) {
      SampleDataResourceRepository.getInstance(facet)
    } else {
      null
    }
  }

  /**
   * Used to handle virtual file changes *after* they've already taken place.
   */
  private fun fileChanged(file: VirtualFile) {
    findRepoToInvalidate(file)?.invalidateBecauseOf(file.toPathString())
  }

  private fun psiFileChanged(event: PsiTreeChangeEvent) {
    event.file?.virtualFile?.let { fileChanged(it) }
  }

  /**
   * To be called *before* a virtual file changes. If the pending change would
   * mean that a [SampleDataResourceRepository] should be invalidated, then this
   * function marks that repository for invalidation. Once the file change actually
   * happens, callers should then call [pendingFileChangeComplete] to ensure that
   * the repository is invalidated.
   *
   * This function is useful for situations where it would be difficult to determine
   * the relevance of a virtual file event after it's already taken place. For example,
   * a virtual file will no longer be associated with an [AndroidFacet] after it's been
   * deleted, making it difficult to obtain the corresponding [SampleDataResourceRepository]
   * after the fact.
   */
  private fun fileChangePending(file: VirtualFile) {
    findRepoToInvalidate(file)?.let { repo ->
      reposToInvalidate[file.toPathString()] = repo
    }
  }

  /**
   * To be called *after* a virtual file change whose relevance was determined beforehand
   * with a call to [fileChangePending]. If the virtual file change was deemed relevant,
   * this function will invalidate the appropriate repository.
   */
  private fun pendingFileChangeComplete(path: PathString) {
    reposToInvalidate.remove(path)?.invalidateBecauseOf(path)
  }

  override fun beforeFileMovement(event: VirtualFileMoveEvent) = fileChangePending(event.file)

  override fun fileMoved(event: VirtualFileMoveEvent) {
    // In case the file was moved *out* of the sample data directory
    pendingFileChangeComplete(event.oldParent.toPathString().resolve(event.fileName))
    // In case the file was moved *into* the sample data directory
    fileChanged(event.file)
  }

  override fun beforeFileDeletion(event: VirtualFileEvent) = fileChangePending(event.file)

  override fun fileDeleted(event: VirtualFileEvent) = pendingFileChangeComplete(event.file.toPathString())

  override fun propertyChanged(event: VirtualFilePropertyEvent) = fileChanged(event.file)
  override fun fileCreated(event: VirtualFileEvent) = fileChanged(event.file)
  override fun childAdded(event: PsiTreeChangeEvent) = psiFileChanged(event)
  override fun childMoved(event: PsiTreeChangeEvent) = psiFileChanged(event)
  override fun childRemoved(event: PsiTreeChangeEvent) = psiFileChanged(event)
  override fun childReplaced(event: PsiTreeChangeEvent) = psiFileChanged(event)
  override fun childrenChanged(event: PsiTreeChangeEvent) = psiFileChanged(event)
}

/**
 * Returns true if the given [VirtualFile] is part of the sample data directory associated with this
 * [Module] (or if the file is the sample data directory itself).
 */
private fun Module.isSampleDataFile(file: VirtualFile): Boolean {
  val sampleDataDir = getModuleSystem().getSampleDataDirectory() ?: return false
  return file.toPathString().startsWith(sampleDataDir)
}