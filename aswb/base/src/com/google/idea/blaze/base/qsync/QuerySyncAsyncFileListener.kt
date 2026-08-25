/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.qsync

import com.google.common.annotations.VisibleForTesting
import com.google.idea.blaze.base.lang.buildfile.language.BuildFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.AsyncFileListener.ChangeApplier
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.ui.EditorNotifications
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.jvm.optionals.getOrNull

/** [AsyncFileListener] for monitoring project changes requiring a re-sync. */
open class QuerySyncAsyncFileListener @VisibleForTesting constructor(private val project: Project) : AsyncFileListener {

  private val changeCounter = AtomicInteger(0)
  private val lastCompletedSyncStamp = AtomicInteger(0)

  /** Returns true if [absolutePath] is in a directory included by the project. */
  open fun isPathIncludedInProject(absolutePath: Path): Boolean =
    QuerySyncManager.getInstance(project).getLoadedProject().getOrNull()?.containsPath(absolutePath) ?: false

  fun hasModifiedBuildFiles(): Boolean = lastCompletedSyncStamp.get() < changeCounter.get()

  fun clearState(lastCompletedSyncStamp: Int) {
    this.lastCompletedSyncStamp.set(lastCompletedSyncStamp)
  }

  override fun prepareChange(events: List<VFileEvent>): ChangeApplier? {
    val eventsRequiringSync = events.filter(::requiresSync)
    if (eventsRequiringSync.isEmpty()) {
      return null
    }

    val buildFileModified = eventsRequiringSync.any { it.file?.fileType is BuildFileType }

    return object : ChangeApplier {
      override fun afterVfsChange() {
        ApplicationManager.getApplication().invokeLater {
          val notifyOnChange = UnsyncedFileEditorNotificationProvider.NOTIFY_ON_BUILD_FILE_CHANGES.value
          if (notifyOnChange && buildFileModified) {
            changeCounter.incrementAndGet()
          }

          EditorNotifications.getInstance(project).updateAllNotifications()
        }
      }
    }
  }

  private fun requiresSync(event: VFileEvent): Boolean {
    if (!isPathIncludedInProject(Path.of(event.path))) {
      return false
    }
    return when {
      event is VFileCreateEvent || event is VFileMoveEvent -> true
      event is VFilePropertyChangeEvent && event.propertyName == VirtualFile.PROP_NAME -> true
      event.file?.fileType is BuildFileType -> true
      else -> false
    }
  }

  fun syncStarted(): Runnable {
    val syncStartedStamp = changeCounter.get()
    return Runnable { clearState(syncStartedStamp) }
  }

  companion object {
    @JvmStatic
    fun createAndListen(
      project: Project,
      parentDisposable: Disposable,
    ): QuerySyncAsyncFileListener {
      val fileListener = QuerySyncAsyncFileListener(project)
      VirtualFileManager.getInstance().addAsyncFileListener(fileListener, parentDisposable)
      return fileListener
    }
  }
}
