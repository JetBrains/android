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
package com.android.tools.idea.appinspection.ide.resolver.gradle

import com.android.annotations.concurrency.WorkerThread
import com.android.tools.idea.appinspection.ide.resolver.AppInspectorJarPaths
import com.android.tools.idea.appinspection.ide.resolver.GradleArtifactDownloader
import com.android.tools.idea.appinspection.inspector.api.AppInspectorJar
import com.android.tools.idea.appinspection.inspector.api.launch.ArtifactCoordinate
import com.android.tools.idea.appinspection.inspector.api.service.FileService
import com.android.tools.idea.appinspection.inspector.ide.resolver.AppInspectionArtifactResolver
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager

/**
 * Implementation of [AppInspectionArtifactResolver] using gradle as the tool to resolve remote artifacts.
 *
 * TODO(b/169794015): write an integration test for this.
 */
class AppInspectionGradleArtifactResolver(fileService: FileService) : AppInspectionArtifactResolver {
  private val jarPaths = AppInspectorJarPaths(fileService)
  private val taskManager = GradleTaskManager()
  private val downloader = GradleArtifactDownloader(taskManager)

  @WorkerThread
  override suspend fun resolveArtifacts(
    artifactIdList: List<ArtifactCoordinate>,
    project: Project
  ): Map<ArtifactCoordinate, AppInspectorJar> {
    val inspectorsToDownload = artifactIdList.filter { jarPaths.getInspectorJar(it) == null }
    if (inspectorsToDownload.isNotEmpty()) {
      val results = downloader.resolve(inspectorsToDownload, project)
      jarPaths.populateJars(
        results.filter { it.status == GradleArtifactDownloader.DownloadResult.Status.SUCCESS }
          .associate { it.target to it.artifactPath!! }
      )
    }
    return artifactIdList
      .filter { jarPaths.getInspectorJar(it) != null }
      .associateWith { jarPaths.getInspectorJar(it)!! }
  }
}