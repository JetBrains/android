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
import com.android.tools.idea.res.SampleDataListener.Companion.ensureSubscribed
import com.android.tools.idea.util.LazyFileListenerSubscriber
import com.android.tools.idea.util.PoliteAndroidVirtualFileListener
import com.android.tools.idea.util.toPathString
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileEvent
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.PsiTreeChangeListener
import org.jetbrains.android.AndroidPluginDisposable
import org.jetbrains.android.facet.AndroidFacet

/**
 * Project-wide listener which invalidates the [SampleDataResourceRepository] corresponding to any
 * module whose sample data directory has been modified (if such a repository exists).
 *
 * A project's [SampleDataListener] is instantiated the first time a [SampleDataResourceRepository]
 * is created for one of project's modules (e.g. when the user opens a resource file or activity for
 * the first time). The listener remains for the lifetime of the project.
 *
 * When a [SampleDataResourceRepository] is created, it calls [ensureSubscribed] to make sure that
 * the project's [SampleDataListener] is tracking VFS and PSI events.
 */
class SampleDataListener(project: Project) :
  PoliteAndroidVirtualFileListener(project), PsiTreeChangeListener {

  companion object {
    private val LOG = Logger.getInstance(SampleDataListener::class.java)

    @JvmStatic
    fun ensureSubscribed(project: Project) {
      project.getService(Subscriber::class.java)!!.ensureSubscribed()
    }
  }

  /**
   * Project service responsible for subscribing a new [SampleDataListener] to listen for both VFS
   * and PSI changes.
   */
  class Subscriber(val project: Project) :
    LazyFileListenerSubscriber<SampleDataListener>(
      SampleDataListener(project),
      AndroidPluginDisposable.getProjectInstance(project)
    ) {

    override fun subscribe() {
      VirtualFileManager.getInstance().addVirtualFileListener(listener, parent)
      AndroidFileChangeListener.getInstance(project).setSampleDataListener(listener)
    }
  }

  /**
   * A file is relevant to this listener if
   * 1. There's a SampleDataResourceRepository instance corresponding to the module the file belongs
   *    to that needs to be invalidated.
   * 2. The file is actually in the module's sample data directory (as opposed to just having
   *    FD_SAMPLE_DATA in its path somewhere).
   */
  override fun isRelevant(file: VirtualFile, facet: AndroidFacet): Boolean {
    return !facet.isDisposed &&
      StudioResourceRepositoryManager.getInstance(facet).cachedSampleDataResources != null &&
      facet.module.isSampleDataFile(file)
  }

  /** Java and XML files have nothing to do with sample data. */
  override fun isPossiblyRelevant(file: VirtualFile) =
    file.extension.let { it != "java" && it != "xml" }

  override fun fileChanged(path: PathString, facet: AndroidFacet) {
    LOG.info("Invalidating SampleDataResourceRepository because $path was modified.")
    StudioResourceRepositoryManager.getInstance(facet).reloadSampleResources()
  }

  // We don't need to respond to VirtualFile content changes, as these will
  // have already been picked up by the PsiTreeChangeListener methods.
  override fun contentsChanged(event: VirtualFileEvent) {}

  private fun psiFileChanged(event: PsiTreeChangeEvent) {
    event.file?.virtualFile?.let { possiblyIrrelevantFileChanged(it) }
  }

  override fun childAdded(event: PsiTreeChangeEvent) = psiFileChanged(event)

  override fun childMoved(event: PsiTreeChangeEvent) = psiFileChanged(event)

  override fun childRemoved(event: PsiTreeChangeEvent) = psiFileChanged(event)

  override fun childReplaced(event: PsiTreeChangeEvent) = psiFileChanged(event)

  override fun childrenChanged(event: PsiTreeChangeEvent) = psiFileChanged(event)

  override fun beforeChildAddition(event: PsiTreeChangeEvent) {}

  override fun beforeChildRemoval(event: PsiTreeChangeEvent) {}

  override fun beforeChildReplacement(event: PsiTreeChangeEvent) {}

  override fun beforeChildMovement(event: PsiTreeChangeEvent) {}

  override fun beforeChildrenChange(event: PsiTreeChangeEvent) {}

  override fun beforePropertyChange(event: PsiTreeChangeEvent) {}

  override fun propertyChanged(event: PsiTreeChangeEvent) {}
}

/**
 * Returns true if the given [VirtualFile] is part of the sample data directory associated with this
 * [Module] (or if the file is the sample data directory itself).
 */
private fun Module.isSampleDataFile(file: VirtualFile): Boolean {
  val sampleDataDir = getModuleSystem().getSampleDataDirectory() ?: return false
  return file.toPathString().startsWith(sampleDataDir)
}
