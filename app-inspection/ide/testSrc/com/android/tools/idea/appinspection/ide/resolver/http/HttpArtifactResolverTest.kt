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

import com.android.repository.api.Downloader
import com.android.repository.api.ProgressIndicator
import com.android.testutils.TestUtils
import com.android.tools.idea.appinspection.ide.resolver.AppInspectorJarPaths
import com.android.tools.idea.appinspection.ide.resolver.TestArtifactResolverRequest
import com.android.tools.idea.appinspection.inspector.api.launch.ArtifactCoordinate
import com.android.tools.idea.appinspection.inspector.api.service.TestFileService
import com.android.tools.idea.appinspection.inspector.ide.resolver.SuccessfulResult
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.InputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path

class HttpArtifactResolverTest {

  @get:Rule
  val androidProjectRule = AndroidProjectRule.inMemory()

  private val testData = TestUtils.getWorkspaceFile("tools/adt/idea/app-inspection/ide/testData/libraries").toPath()

  private val fakeDownloader = object : Downloader {
    override fun downloadAndStream(url: URL, indicator: ProgressIndicator): InputStream? = null
    override fun downloadFully(url: URL, indicator: ProgressIndicator): Path? = null
    override fun downloadFully(url: URL, target: File, checksum: String?, indicator: ProgressIndicator) {}
    override fun setDownloadIntermediatesLocation(intermediatesLocation: File) {}

    override fun downloadFullyWithCaching(url: URL, target: File, checksum: String?, indicator: ProgressIndicator) {
      // Fake download by resolving the URL against the local testData directory.
      val srcFile = testData.resolve(url.path.substringAfter('/'))
      Files.copy(srcFile, target.toPath())
    }
  }

  @Test
  fun downloadAndCacheArtifact() = runBlocking<Unit> {
    val fileService = TestFileService()
    val jarPaths = AppInspectorJarPaths(fileService)


    val resolver = HttpArtifactResolver(fileService, jarPaths, fakeDownloader)
    val request = TestArtifactResolverRequest(
      ArtifactCoordinate("androidx.work", "work-runtime", "2.5.0-beta01", ArtifactCoordinate.Type.AAR))
    val results = resolver.resolveArtifacts(listOf(request), androidProjectRule.project)

    assertThat(results).hasSize(1)

    val result = results[0] as SuccessfulResult<TestArtifactResolverRequest>
    assertThat(result.request).isSameAs(request)
    assertThat(result.jar.name).isEqualTo("inspector.jar")
    assertThat(jarPaths.getInspectorJar(request.artifactCoordinate)).isSameAs(result.jar)
  }
}
