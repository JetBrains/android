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
//import com.android.tools.analytics.withProjectId
import com.android.tools.idea.concurrency.coroutineScope
import com.android.tools.idea.project.coroutines.runReadActionInSmartModeWithIndexes
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResultListener
import com.android.tools.idea.serverflags.ServerFlagService
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.IntellijProjectSizeStats
import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.ProjectScope
import com.intellij.util.Processor
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.annotations.VisibleForTesting

// Upper limit on the number of files we will count. Only 1% of projects have more files than this
private const val FILE_CAP = 60000

class ProjectSizeUsageTrackerListener(private val project: Project) : SyncResultListener {

  override fun syncEnded(result: SyncResult) {
    if (!result.isSuccessful) {
      return
    }
    if (AnalyticsSettings.optedIn) {
      project.getService(ReportProjectSizeTask::class.java).run()
    }
  }
}

class ReportProjectSizeTask(val project: Project) : Runnable {

  @VisibleForTesting
  val repostStatsJobs = mutableListOf<Job>()

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
    project.coroutineScope.launch {
      withBackgroundProgress(project, "Computing project size", true) {
        //val builder = AndroidStudioEvent
        //  .newBuilder()
        //  .setKind(AndroidStudioEvent.EventKind.INTELLIJ_PROJECT_SIZE_STATS)
        //  .withProjectId(project)
        //
        //FileType.entries.forEach { fileType ->
        //  project.runReadActionInSmartModeWithIndexes {
        //    builder.addIntellijProjectSizeStatsForFileType(fileType)
        //  }
        //}
        //
        //UsageTracker.log(builder)
      }
    }.also {
      if (ApplicationManager.getApplication().isUnitTestMode) {
        repostStatsJobs.removeIf { it.isCompleted }
        repostStatsJobs.add(it)
      }
    }
  }

  private fun AndroidStudioEvent.Builder.addIntellijProjectSizeStatsForFileType(fileType: FileType) {
    addIntellijProjectSizeStats(
      IntellijProjectSizeStats
        .newBuilder()
        .setScope(IntellijProjectSizeStats.Scope.PROJECT)
        .setType(fileType.statsFileType())
        .setCount(fileCount(fileType))
    )
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
      FileTypeIndex.processFiles(fileType.languageFileType(), object : Processor<VirtualFile> {

        override fun process(t: VirtualFile?): Boolean {
          if (numFiles % 100 == 0) {
            // Make sure to check if cancelled to avoid UI freezes - see https://issuetracker.google.com/316496921
            ProgressManager.checkCanceled()
          }
          numFiles++
          return (numFiles < cap)
        }
      }, ProjectScope.getProjectScope(project))
      return numFiles;
    }
  }
}
