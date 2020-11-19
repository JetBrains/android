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
package com.android.tools.idea.appinspection.ide.resolver.http

import com.android.repository.api.ConsoleProgressIndicator
import com.android.repository.api.Downloader
import com.android.tools.idea.appinspection.ide.resolver.AppInspectorJarPaths
import com.android.tools.idea.appinspection.inspector.api.io.FileService
import com.android.tools.idea.appinspection.inspector.api.launch.ArtifactCoordinate
import com.android.tools.idea.appinspection.inspector.ide.resolver.ArtifactResolver
import com.android.tools.idea.appinspection.inspector.ide.resolver.ArtifactResolverRequest
import com.android.tools.idea.appinspection.inspector.ide.resolver.ArtifactResolverResult
import com.android.tools.idea.appinspection.inspector.ide.resolver.FailureResult
import com.android.tools.idea.appinspection.inspector.ide.resolver.SuccessfulResult
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.sdk.StudioDownloader
import com.intellij.openapi.project.Project
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.IOException
import java.net.URL

class HttpArtifactResolver(
  fileService: FileService,
  private val jarPaths: AppInspectorJarPaths,
  private val downloader: Downloader = StudioDownloader()
) : ArtifactResolver {
  private val tmpDir = fileService.getOrCreateTempDir("http-tmp")
  override suspend fun <T : ArtifactResolverRequest> resolveArtifacts(
    requests: List<T>,
    project: Project
  ): List<ArtifactResolverResult<T>> = coroutineScope {
    requests
      .filter { jarPaths.getInspectorJar(it.artifactCoordinate) == null }
      .map { request ->
        async(AndroidDispatchers.ioThread) {
          try {
            downloader.downloadFullyWithCaching(request.artifactCoordinate.toGMavenUrl(), request.artifactCoordinate.getTmpFile(), null,
                                                ConsoleProgressIndicator())
            request
          }
          catch (e: IOException) {
            null
          }
        }
      }
      .awaitAll()
      .filterNotNull()
      .associate { it.artifactCoordinate to it.artifactCoordinate.getTmpFile().toPath() }
      .let { if (it.isNotEmpty()) jarPaths.populateJars(it) }

    requests.map { request ->
      jarPaths.getInspectorJar(request.artifactCoordinate)?.let {
        SuccessfulResult(request, it)
      } ?: FailureResult(request)
    }
  }

  private fun ArtifactCoordinate.getTmpFile() = tmpDir.resolve(fileName).toFile()

  private fun ArtifactCoordinate.toGMavenUrl() = URL(
    "http://maven.google.com/${groupId.replace('.', '/')}/${artifactId}/${version}/${fileName}")
}