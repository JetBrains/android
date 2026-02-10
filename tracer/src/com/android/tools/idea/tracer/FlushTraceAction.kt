/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.tracer

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.tracer.TracingService
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vfs.LocalFileSystem
import kotlin.io.path.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FlushTraceAction : DumbAwareAction("Flush Perfetto Trace") {

  override fun update(e: AnActionEvent) {
    // TODO(b/467364934): Use the feature flag to control the feature, not enablement.
    val enabled = StudioFlags.STUDIO_TRACE_LIBRARY_ENABLED.get()
    e.presentation.isEnabledAndVisible = enabled
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project
    val service = TracingService.getInstance() ?: return

    CoroutineScope(Dispatchers.Default).launch {
      val virtualFile =
        withContext(Dispatchers.IO) {
          val pathString = service.flush()
          LocalFileSystem.getInstance().refreshAndFindFileByNioFile(Path(pathString))
        }

      withContext(Dispatchers.Main) {
        if (virtualFile != null) {
          // Open the file in the Profiler.
          //
          // It is expected that we get a warning with an IllegalStateException stack trace
          // in the logs. It complains about an issue with the trace header when opening the file.
          // The Profiler uses a try-and-error approach to opening various files.
          if (project != null) {
            FileEditorManager.getInstance(project).openFile(virtualFile, true)
          }

          // TODO(b/467364934): Decide on the best UX for opening/displaying the file.
          val notification =
            Notification(
              "Android",
              "Trace flushed",
              "A Perfetto trace has been created at: ${virtualFile.path}",
              NotificationType.INFORMATION,
            )
          Notifications.Bus.notify(notification, project)
        } else {
          val notification = Notification("Android", "Trace error", "Could not obtain trace file.", NotificationType.ERROR)
          Notifications.Bus.notify(notification, project)
        }
      }
    }
  }
}
