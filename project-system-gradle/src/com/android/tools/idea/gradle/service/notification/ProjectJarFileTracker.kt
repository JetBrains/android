/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.gradle.service.notification

import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_SYNC_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.android.tools.idea.util.toIoFile
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import org.jetbrains.annotations.VisibleForTesting
import java.util.function.Function


class ProjectJarFileTracker(val project: Project) {
  val editorNotifications:EditorNotifications = EditorNotifications.getInstance(project)
  @VisibleForTesting
  var jarFilesChanged = false
    private set

  private fun resetFlag(){
    jarFilesChanged = false
  }

  init {
    project.messageBus.connect(project).subscribe<BulkFileListener>(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      private val JAR_EXT = ".jar"

      override fun before(events: List<VFileEvent>) {
        for (event in events) {
          if (event is VFileMoveEvent) {
            onChanged(event.file)
          } else if (event is VFileDeleteEvent) {
            onChanged(event.file)
          }
        }
      }

      override fun after(events: List<VFileEvent>) {
        for (event in events) {
          when (event) {
            is VFileCreateEvent, is VFileCopyEvent, is VFileMoveEvent -> onChanged(event.file)
          }
        }
      }

      private fun existInsideExcludedDirectory(file: VirtualFile): Boolean {
        val filePath = file.toIoFile().toPath()
        project.modules.forEach { module ->
          val excludedPaths = ExternalSystemApiUtil.getExternalProjectContentRoots(module, ExternalSystemSourceType.EXCLUDED)
          excludedPaths?.forEach { contentRoot ->
            if (filePath.normalize().startsWith(contentRoot.path.normalize())) {
              return true
            }
          }
        }
        return false
      }

      private fun onChanged(file: VirtualFile?) {
        file?.let {
          if (it.name.endsWith(JAR_EXT) && !existInsideExcludedDirectory(it)) {
            jarFilesChanged = true
            editorNotifications.updateAllNotifications()
          }
        }
      }
    })

    project.messageBus.connect().subscribe(PROJECT_SYSTEM_SYNC_TOPIC,
                                           ProjectSystemSyncManager.SyncResultListener { result ->
                                             if (result == ProjectSystemSyncManager.SyncResult.SUCCESS) {
                                               resetFlag()
                                             }
                                           })
  }


  class JarFileNotificationProvider: EditorNotificationProvider {

    override fun collectNotificationData(project: Project, file: VirtualFile): Function<FileEditor, EditorNotificationPanel?>? {
      if (project.getProjectSystem() !is GradleProjectSystem) {
        return null
      }
      val tracker = getInstance(project)
      if(tracker.jarFilesChanged){
        return Function { fileEditor -> JarFileSyncNotificationPanel(project, fileEditor) }
      }
      return null
    }
  }

  internal class JarFileSyncNotificationPanel(project: Project, editor: FileEditor) : EditorNotificationPanel(editor) {
    init {
      text("Jar files have been added/removed since last project sync. Sync may be necessary for the IDE to work properly.")
      createActionLabel("Sync Now") {
                          GradleSyncInvoker.getInstance()
                            .requestProjectSync(
                              project,
                              GradleSyncInvoker.Request(GradleSyncStats.Trigger.TRIGGER_USER_STALE_CHANGES),
                              null
                            )
                        }
      createActionLabel("Ignore these changes") {
        getInstance(project).resetFlag()
        this.isVisible = false
      }
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): ProjectJarFileTracker {
      return project.getService(ProjectJarFileTracker::class.java)
    }
  }
}
