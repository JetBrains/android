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
 * If an event passes through both filters, the listener responds by passing the affected file and
 * corresponding [AndroidFacet] to [fileChanged].
 *
 * For VFS events corresponding to file moves or deletions, it can be difficult to determine information
 * about the event after the fact (e.g. if a [VirtualFile] has already been deleted, it's difficult to
 * obtain the [AndroidFacet] it was associated with before the deletion). Instead of calling [fileChanged]
 * in such cases, the listener will call [fileChangePending] before the relevant event takes place to give
 * implementing classes the opportunity to compute and cache any information they need, and then calls
 * [pendingFileChangeComplete] to respond to the pending event once it has completed.
 */
abstract class PoliteAndroidVirtualFileListener(val project: Project) : VirtualFileListener {
  private val relevantPendingFiles = hashSetOf<PathString>()

  /**
   * Used to fail fast when we can quickly tell that a file has nothing to do with this listener
   * (usually based on the file name or its extension). [VirtualFileEvent]s whose corresponding
   * [VirtualFile]s fail this test will be ignored before calling [isRelevant].
   *
   * **Note:** For performance reasons, be sure to use [VirtualFile.getExtension] here if you need the
   * file extension, as opposed to [VirtualFile.getFileType], which actually reads from the file.
   */
  protected abstract fun isPossiblyRelevant(file: VirtualFile): Boolean

  /**
   * Used to determine if a file is actually relevant to this listener. [VirtualFileEvent]s whose
   * corresponding [VirtualFile]s fail this test will not be processed by this listener.
   */
  protected abstract fun isRelevant(file: VirtualFile, facet: AndroidFacet): Boolean

  /** Handles a relevant virtual file change *after* it's already taken place.*/
  protected abstract fun fileChanged(file: VirtualFile, facet: AndroidFacet)

  /**
   * Prepares the listener for a relevant virtual file change *before* the change happens.
   * This function is called in situations where it would be difficult to determine information
   * about the changed [VirtualFile] after the [VirtualFileEvent] has already taken place. For example,
   * a [VirtualFile] will no longer be associated with an [AndroidFacet] after it's been deleted,
   * making it difficult to determine the affected [AndroidFacet] after the fact.
   *
   * Implementing classes can use this function to compute and cache such information so that it's
   * available in [pendingFileChangeComplete] once the virtual file has actually changed.
   */
  protected abstract fun fileChangePending(path: PathString, facet: AndroidFacet)

  /**
   * Called *after* a virtual file change if that change was determined to be relevant before it
   * actually took place. This function is always preceded by a call to [fileChangePending] for
   * the same [path].
   */
  protected abstract fun pendingFileChangeComplete(path: PathString)

  /**
   * Handles a virtual file change *after* it's already taken place if the event was relevant.
   * Otherwise, this function will simply ignore the file change.
   */
  protected fun possiblyIrrelevantFileChanged(file: VirtualFile) = runIfRelevant(file, this::fileChanged)

  private inline fun runIfRelevant(file: VirtualFile, block: (VirtualFile, AndroidFacet) -> Unit) {
    if (!isPossiblyRelevant(file)) return
    val facet = AndroidFacet.getInstance(file, project) ?: return
    if (isRelevant(file, facet)) {
      block(file, facet)
    }
  }

  private fun possiblyIrrelevantFileChangePending(file: VirtualFile) {
    runIfRelevant(file) { _, facet ->
      val path = file.toPathString()
      relevantPendingFiles.add(path)
      fileChangePending(path, facet)
    }
  }

  private fun pendingPossiblyIrrelevantFileChangeComplete(path: PathString) {
    if (relevantPendingFiles.remove(path)) {
      pendingFileChangeComplete(path)
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

  override fun propertyChanged(event: VirtualFilePropertyEvent) = possiblyIrrelevantFileChanged(event.file)

  override fun fileCreated(event: VirtualFileEvent) = possiblyIrrelevantFileChanged(event.file)
}