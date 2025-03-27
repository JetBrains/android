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

import com.android.resources.ResourceFolderType
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.PsiTreeChangeListener
import com.intellij.util.Consumer

/**
 * Project component that tracks changes to the PSI tree.
 *
 * It dispatches these events to other services and components.
 *
 * Information is forwarded to:
 * * [ResourceFolderRegistry] and from there to [ResourceFolderRepository] and [LayoutLibrary]
 * * [SampleDataListener] and from there to [SampleDataResourceRepository]
 * * [ResourceNotificationManager]
 */
@Service(Service.Level.PROJECT)
class AndroidPsiTreeChangeListener(private val project: Project) :
  PsiTreeChangeListener, Disposable {
  override fun dispose() {}

  private val sampleDataListener
    get() = SampleDataListener.getInstanceIfCreated(project)

  override fun beforeChildAddition(event: PsiTreeChangeEvent) {}

  override fun childAdded(event: PsiTreeChangeEvent) {
    val psiFile = event.file
    when {
      psiFile == null -> {
        when (val child = event.child) {
          is PsiFile ->
            child.virtualFile?.let { if (isRelevantFile(it)) dispatchChildAdded(event, it) }
          is PsiDirectory -> dispatchChildAdded(event, child.virtualFile)
        }
      }
      isRelevantFile(psiFile) -> dispatchChildAdded(event, psiFile.virtualFile)
    }

    sampleDataListener?.childAdded(event)
  }

  private fun dispatchChildAdded(event: PsiTreeChangeEvent, virtualFile: VirtualFile?) {
    dispatch(virtualFile) { it?.childAdded(event) }
  }

  override fun beforeChildRemoval(event: PsiTreeChangeEvent) {}

  override fun childRemoved(event: PsiTreeChangeEvent) {
    val psiFile = event.file

    when {
      psiFile == null -> {
        when (val child = event.child) {
          is PsiFile ->
            child.virtualFile?.takeIf(::isRelevantFile)?.let { dispatchChildRemoved(event, it) }
          is PsiDirectory ->
            if (ResourceFolderType.getFolderType(child.name) != null) {
              dispatchChildRemoved(event, child.virtualFile)
            }
        }
      }
      isRelevantFile(psiFile) -> dispatchChildRemoved(event, psiFile.virtualFile)
    }

    sampleDataListener?.childRemoved(event)
  }

  private fun dispatchChildRemoved(event: PsiTreeChangeEvent, virtualFile: VirtualFile?) {
    dispatch(virtualFile) { it?.childRemoved(event) }
  }

  override fun beforeChildReplacement(event: PsiTreeChangeEvent) {}

  override fun childReplaced(event: PsiTreeChangeEvent) {
    val psiFile = event.file
    if (psiFile == null) {
      val parent = event.parent as? PsiDirectory ?: return
      dispatchChildReplaced(event, parent.virtualFile)
      return
    }
    val file = psiFile.virtualFile

    when {
      isRelevantFile(psiFile) -> dispatchChildReplaced(event, file)
    }

    sampleDataListener?.childReplaced(event)
  }

  private fun dispatchChildReplaced(event: PsiTreeChangeEvent, virtualFile: VirtualFile?) {
    dispatch(virtualFile) { it?.childReplaced(event) }
  }

  override fun beforeChildrenChange(event: PsiTreeChangeEvent) {
    event.file?.takeIf(::isRelevantFile)?.let {
      dispatchBeforeChildrenChange(event, it.virtualFile)
    }
  }

  private fun dispatchBeforeChildrenChange(event: PsiTreeChangeEvent, virtualFile: VirtualFile?) {
    dispatch(virtualFile) { it?.beforeChildrenChange(event) }
  }

  override fun childrenChanged(event: PsiTreeChangeEvent) {
    val psiFile = event.file ?: return
    val file = psiFile.virtualFile

    if (isRelevantFile(psiFile)) dispatchChildrenChanged(event, file)

    sampleDataListener?.childrenChanged(event)
  }

  private fun dispatchChildrenChanged(event: PsiTreeChangeEvent, virtualFile: VirtualFile?) {
    dispatch(virtualFile) { it?.childrenChanged(event) }
  }

  override fun beforeChildMovement(event: PsiTreeChangeEvent) {}

  override fun childMoved(event: PsiTreeChangeEvent) {
    val psiFile = event.file
    if (psiFile == null) {
      val child = (event.child as? PsiFile)?.takeIf(::isRelevantFile) ?: return
      child.virtualFile?.let {
        dispatchChildMoved(event, it)
        return
      }

      (event.oldParent as? PsiDirectory)?.let { dispatchChildMoved(event, it.virtualFile) }
    } else {
      // Change inside a file
      val file = psiFile.virtualFile ?: return
      if (isRelevantFile(file)) dispatchChildMoved(event, file)

      sampleDataListener?.childMoved(event)
    }
  }

  private fun dispatchChildMoved(event: PsiTreeChangeEvent, virtualFile: VirtualFile?) {
    dispatch(virtualFile) { it?.childMoved(event) }

    // If you moved the file between resource directories, potentially notify that previous
    // repository as well
    if (event.file == null) {
      val oldParent = event.oldParent as? PsiDirectory ?: return
      dispatch(oldParent.virtualFile) { it?.childMoved(event) }
    }
  }

  override fun beforePropertyChange(event: PsiTreeChangeEvent) {
    if (PsiTreeChangeEvent.PROP_FILE_NAME == event.propertyName) {
      (event.child as? PsiFile)?.takeIf(::isRelevantFile)?.let {
        dispatchBeforePropertyChange(event, it.virtualFile)
      }
    }
  }

  private fun dispatchBeforePropertyChange(event: PsiTreeChangeEvent, virtualFile: VirtualFile?) {
    dispatch(virtualFile) { it?.beforePropertyChange(event) }
  }

  override fun propertyChanged(event: PsiTreeChangeEvent) {
    if (PsiTreeChangeEvent.PROP_FILE_NAME == event.propertyName) {
      (event.element as? PsiFile)?.takeIf(::isRelevantFile)?.let {
        dispatchPropertyChanged(event, it.virtualFile)
      }
    }

    // TODO: Do we need to handle PROP_DIRECTORY_NAME for users renaming any of the resource
    // folders? And what about PROP_FILE_TYPES -- can users change the type of an XML File to
    // something else?
  }

  private fun dispatchPropertyChanged(event: PsiTreeChangeEvent, virtualFile: VirtualFile?) {
    dispatch(virtualFile) { it?.propertyChanged(event) }
  }

  private fun dispatch(file: VirtualFile?, invokeCallback: Consumer<PsiTreeChangeListener>) {
    if (file != null)
      ResourceFolderRegistry.getInstance(project).dispatchToRepositories(file, invokeCallback)
    ResourceNotificationManager.getInstance(project).psiListener?.let(invokeCallback::consume)
  }

  class MyProjectActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
      val listener = getInstance(project)
      PsiManager.getInstance(project).addPsiTreeChangeListener(listener, listener)
    }
  }

  companion object {
    fun getInstance(project: Project): AndroidPsiTreeChangeListener = project.service()
  }
}
