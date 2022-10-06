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
package com.android.tools.idea.stats

import com.android.tools.analytics.AnalyticsSettings
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResultListener
import com.android.tools.idea.serverflags.ServerFlagService
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.IntellijProjectSizeStats
import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.progress.PerformInBackgroundOption
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.ProjectScope
import java.util.concurrent.Callable

// Upper limit on the number of files we will count. Only 1% of projects have more files than this
private const val FILE_CAP = 60000

class ProjectSizeUsageTrackerListener(private val project: Project) : SyncResultListener {
  override fun syncEnded(result: SyncResult) {
    if (!result.isSuccessful && result != SyncResult.PARTIAL_SUCCESS) {
      return
    }
    if (AnalyticsSettings.optedIn) {
      object : Task.Backgroundable(project, "Computing project size", true, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
        override fun run(indicator: ProgressIndicator) {
          ReportProjectSizeTask(project).run()
        }

      }.queue()
    }
  }
}

class ReportProjectSizeTask(val project: Project) : Runnable {
  private enum class FileType(private val fileType: com.intellij.openapi.fileTypes.FileType,
                              private val statsFileType: IntellijProjectSizeStats.FileType) {
    JAVA(JavaFileType.INSTANCE, IntellijProjectSizeStats.FileType.JAVA),
    XML(XmlFileType.INSTANCE, IntellijProjectSizeStats.FileType.XML),
    JAVA_CLASS(JavaClassFileType.INSTANCE, IntellijProjectSizeStats.FileType.DOT_CLASS),
    KOTLIN(FileTypeRegistry.getInstance().findFileTypeByName("Kotlin") ?: PlainTextFileType.INSTANCE,
           IntellijProjectSizeStats.FileType.KOTLIN),
    NATIVE(FileTypeRegistry.getInstance().findFileTypeByName("ObjectiveC") ?: PlainTextFileType.INSTANCE,
           IntellijProjectSizeStats.FileType.NATIVE);

    fun languageFileType(): com.intellij.openapi.fileTypes.FileType {
      return fileType
    }

    fun statsFileType(): IntellijProjectSizeStats.FileType {
      return statsFileType
    }
  }

  override fun run() {
    val builder = AndroidStudioEvent
      .newBuilder()
      .setKind(AndroidStudioEvent.EventKind.INTELLIJ_PROJECT_SIZE_STATS)
      .withProjectId(project)

    for (fileType in FileType.values()) {
      val fileCount =
        try {
          ReadAction.nonBlocking(Callable {
            fileCount(fileType)
          })
            .inSmartMode(project)
            .expireWith(project)
            .executeSynchronously()
        } catch (e: Exception) {
          // in the case of any exception (project disposed, or ProcessCanceledException, etc)
          // we just send an impossible value so that we can track how often such scenarios
          // occur in the backend
          -1
        }
      builder.addIntellijProjectSizeStats(
        IntellijProjectSizeStats
          .newBuilder()
          .setScope(IntellijProjectSizeStats.Scope.PROJECT)
          .setType(fileType.statsFileType())
          .setCount(fileCount)
      )
    }

    UsageTracker.log(builder)
  }

  private fun fileCount(fileType: FileType): Int {
    if (fileType.languageFileType() is PlainTextFileType) {
      // If kotlin plugin is not enabled, we will get PlainTextFileType. In such case, we do not want to collect kotlin
      // file count since it will include so many unrelated plain text file
      return 0
    }
    else {
      val cap = ServerFlagService.instance.getInt("analytics/projectsize/filecap", FILE_CAP)
      var numFiles = 0
      FileTypeIndex.processFiles(
        fileType.languageFileType(),
        { numFiles++; numFiles < cap },
        ProjectScope.getProjectScope(project)
      )
      return numFiles;
    }
  }
}
