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
package com.android.tools.idea.util

import com.android.ide.common.util.PathString
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileEvent
import com.intellij.openapi.vfs.VirtualFileListener
import com.intellij.openapi.vfs.VirtualFileMoveEvent
import com.intellij.openapi.vfs.VirtualFilePropertyEvent
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.android.facet.AndroidFacet

/**
 * An Android-specific [VirtualFileListener] that does its best to ignore irrelevant VFS events.
 * Such events are filtered out by a two step process:
 *
 *  1. First the listener calls [isPossiblyRelevant] on the affected [VirtualFile] so that we can
 *     fail fast for files that are categorically irrelevant (e.g. because this listener doesn't
 *     apply to files with a particular extension).
 *
 *  2. If the event passes the [isPossiblyRelevant], then the listener actually does the work of
 *     determining which [AndroidFacet] the affected file corresponds to. It then passes the file
 *     and facet to [isRelevant], which definitively determines whether or not the event was relevant.
 *
 * If an event passes through both filters, the listener responds by passing the path of the affected
 * file and corresponding [AndroidFacet] to [fileChanged].
 *
 * For VFS events corresponding to file moves or deletions, it is difficult to determine which [AndroidFacet]
 * the file originally belonged to after the event has happened. [PoliteAndroidVirtualFileListener] addresses
 * this by determining and caching the relevant [AndroidFacet] while such changes are still pending and then
 * passing the cached facet to [fileChanged] once the pending changes have completed.
 */
abstract class PoliteAndroidVirtualFileListener(val project: Project) : VirtualFileListener {
  private val relevantPendingFilesToFacet = ContainerUtil.createWeakValueMap<PathString, AndroidFacet>()

  /**
   * Used to fail fast when we can quickly tell that a file has nothing to do with this listener
   * (usually based on the file name or its extension). [VirtualFileEvent]s whose corresponding
   * [VirtualFile]s fail this test will be ignored before calling [isRelevant].
   *
   * **Note:** When a [VirtualFile] corresponding to a directory is deleted, the VFS only fires
   * events signaling the deletion of the directory. It *does not* fire a separate event for each
   * descendant. This means that you may want to let directories pass through this filter so that
   * the listener can determine if they contain relevant files in [isRelevant].
   *
   * **Note:** For performance reasons, be sure to use [VirtualFile.getExtension] here if you need the
   * file extension, as opposed to [VirtualFile.getFileType], which actually reads from the file.
   */
  protected abstract fun isPossiblyRelevant(file: VirtualFile): Boolean

  /**
   * Used to determine if a file is actually relevant to this listener. [VirtualFileEvent]s whose
   * corresponding [VirtualFile]s fail this test will not be processed by this listener.
   *
   * **Note:** When a [VirtualFile] corresponding to a directory is deleted, the VFS only fires
   * events signaling the deletion of the directory. It *does not* fire a separate event for each
   * descendant. This means that if [file] is a directory, you may want to check to see if it contains
   * any relevant files.
   */
  protected abstract fun isRelevant(file: VirtualFile, facet: AndroidFacet): Boolean

  /**
   * Handles a relevant virtual file change *after* it's already taken place. Note that
   * [facet] is the one that was associated with the affected file *before* the change
   * took place.
   */
  protected abstract fun fileChanged(path: PathString, facet: AndroidFacet)

  /**
   * Handles a virtual file change *after* it's already taken place if the event was relevant.
   * Otherwise, this function will simply ignore the file change.
   */
  protected fun possiblyIrrelevantFileChanged(file: VirtualFile) = runIfRelevant(file, this::fileChanged)

  private inline fun runIfRelevant(file: VirtualFile, block: (PathString, AndroidFacet) -> Unit) {
    if (!file.isValid || !isPossiblyRelevant(file)) return
    val facet = AndroidFacet.getInstance(file, project) ?: return
    if (isRelevant(file, facet)) {
      block(file.toPathString(), facet)
    }
  }

  private fun possiblyIrrelevantFileChangePending(file: VirtualFile) {
    runIfRelevant(file) { path, facet ->
      relevantPendingFilesToFacet[path] = facet
    }
  }

  private fun pendingPossiblyIrrelevantFileChangeComplete(path: PathString) {
    relevantPendingFilesToFacet.remove(path)?.let { facet ->
      fileChanged(path, facet)
    }
  }

  override fun beforeFileMovement(event: VirtualFileMoveEvent) = possiblyIrrelevantFileChangePending(event.file)

  override fun fileMoved(event: VirtualFileMoveEvent) {
    // In case the file was moved *out* of a relevant directory
    pendingPossiblyIrrelevantFileChangeComplete(event.oldParent.toPathString().resolve(event.fileName))
    // In case the file was moved *into* a relevant directory
    possiblyIrrelevantFileChanged(event.file)
  }

  override fun beforeFileDeletion(event: VirtualFileEvent) = possiblyIrrelevantFileChangePending(event.file)

  override fun fileDeleted(event: VirtualFileEvent) = pendingPossiblyIrrelevantFileChangeComplete(event.file.toPathString())

  override fun contentsChanged(event: VirtualFileEvent) = possiblyIrrelevantFileChanged(event.file)

  override fun propertyChanged(event: VirtualFilePropertyEvent) = possiblyIrrelevantFileChanged(event.file)

  override fun fileCreated(event: VirtualFileEvent) = possiblyIrrelevantFileChanged(event.file)
}