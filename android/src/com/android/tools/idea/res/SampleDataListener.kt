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
  private var subscribed = AtomicBoolean(false)

  companion object {
    private val LOG = Logger.getInstance(SampleDataListener::class.java)

    @JvmStatic fun getInstance(project: Project) = ServiceManager.getService(project, SampleDataListener::class.java)!!

    @JvmStatic fun ensureSubscribed(project: Project) = getInstance(project).ensureSubscribed()
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
  private fun isRelevant(file: VirtualFile, facet: AndroidFacet): Boolean {
    return !facet.isDisposed && SampleDataRepositoryManager.getInstance(facet).hasRepository() && facet.module.isSampleDataFile(file)
  }

  /**
   * Used to fail fast when we can quickly tell that a file has nothing to do with sample data.
   */
  private fun isPossiblyRelevant(file: VirtualFile) = file.extension.let { it != "java" && it != "xml" }

  private fun virtualFileChanged(file: VirtualFile) {
    if (!isPossiblyRelevant(file)) return

    // We need the module that the file belongs to in order to get the facet, but the module
    // won't be available in the event that the file was just deleted. We use the file's parent
    // to get the facet instead.
    val facet = file.parent?.let { AndroidFacet.getInstance(it, project) } ?: return

    if (isRelevant(file, facet)) {
      LOG.info("Invalidating SampleDataResourceRepository because ${file.path} was modified.")
      SampleDataResourceRepository.getInstance(facet).invalidate()
    }
  }

  override fun propertyChanged(event: VirtualFilePropertyEvent) = virtualFileChanged(event.file)
  override fun fileCreated(event: VirtualFileEvent) = virtualFileChanged(event.file)
  override fun fileDeleted(event: VirtualFileEvent) = virtualFileChanged(event.file)
  override fun fileMoved(event: VirtualFileMoveEvent) = virtualFileChanged(event.file)

  private fun psiFileChanged(event: PsiTreeChangeEvent) {
    event.file?.virtualFile?.let { virtualFileChanged(it) }
  }

  override fun childAdded(event: PsiTreeChangeEvent) = psiFileChanged(event)
  override fun childRemoved(event: PsiTreeChangeEvent) = psiFileChanged(event)
  override fun childReplaced(event: PsiTreeChangeEvent) = psiFileChanged(event)
  override fun childMoved(event: PsiTreeChangeEvent) = psiFileChanged(event)
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