/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.appinspection.ide.resolver

import com.android.annotations.concurrency.WorkerThread
import com.android.tools.idea.appinspection.inspector.api.launch.ArtifactCoordinate
import com.android.tools.idea.gradle.util.GradleUtil
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Path

/**
 * A downloader that executes a gradle task to download maven artifacts.
 *
 * The downloaded artifacts are located in gradle cache.
 */
internal class GradleArtifactDownloader(private val taskManager: GradleTaskManager) {

  /**
   * The result of the download attempt.
   *
   * It contains information about the status and artifact that was targeted. And the artifact path if it was downloaded.
   */
  class DownloadResult(
    val status: Status,
    val target: ArtifactCoordinate,
    val artifactPath: Path?
  ) {
    enum class Status {
      SUCCESS,
      FAILURE
    }
  }

  /**
   * Start executing the gradle task to download maven artifacts.
   *
   * Returns for each artifact, a [DownloadResult] object containing information about the status as well the location of the downloaded
   * artifact if it was successful.
   */
  @WorkerThread
  fun resolve(artifacts: List<ArtifactCoordinate>, project: Project): List<DownloadResult> {
    val scriptBuilder = GradleScriptBuilder()
    artifacts.forEach { scriptBuilder.addArtifact(it.toString()) }
    val scriptFile = writeGradleScriptToTempDirectory(scriptBuilder.build())
    val id = ExternalSystemTaskId.create(GradleUtil.GRADLE_SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, project)
    val settings = GradleUtil.getOrCreateGradleExecutionSettings(project)
    val downloaded = mutableMapOf<String, Path>()
    taskManager.executeTasks(
      id, listOf(TASK_ID), scriptFile.path, settings, null,
      object : ExternalSystemTaskNotificationListenerAdapter() {
        override fun onTaskOutput(id: ExternalSystemTaskId, text: String, stdOut: Boolean) {
          if (stdOut && text.startsWith(ARTIFACT_PREFIX)) {
            val file = File(text.substring(ARTIFACT_PREFIX.length))
            if (file.exists()) {
              downloaded[file.name] = file.toPath()
            }
          }
        }
      })
    return artifacts.map { artifact ->
      downloaded[artifact.fileName]?.let { jar ->
        DownloadResult(DownloadResult.Status.SUCCESS, artifact, jar)
      } ?: DownloadResult(DownloadResult.Status.FAILURE, artifact, null)
    }
  }

  private fun writeGradleScriptToTempDirectory(content: String): File {
    val contentBytes = content.toByteArray(StandardCharsets.UTF_8)
    val contentLength = contentBytes.size
    return FileUtil.findSequentFile(File(FileUtil.getTempDirectory()), "build", GradleConstants.EXTENSION) { file ->
      try {
        if (!file.exists()) {
          FileUtil.writeToFile(file, contentBytes, false)
          file.deleteOnExit()
          true
        }
        else if (contentLength.toLong() != file.length()) {
          false
        }
        else {
          content == FileUtil.loadFile(file, StandardCharsets.UTF_8)
        }
      }
      catch (ignore: IOException) {
        // Skip file with access issues. Will attempt to check the next file
        false
      }
    }
  }
}