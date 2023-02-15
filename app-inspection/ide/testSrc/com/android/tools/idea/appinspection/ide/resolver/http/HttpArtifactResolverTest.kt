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

import com.android.repository.api.Checksum
import com.android.repository.api.Downloader
import com.android.repository.api.ProgressIndicator
import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.idea.appinspection.ide.resolver.AppInspectorArtifactPaths
import com.android.tools.idea.appinspection.inspector.api.launch.ArtifactCoordinate
import com.android.tools.idea.appinspection.inspector.api.service.TestFileService
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import java.io.InputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class HttpArtifactResolverTest {

  @get:Rule val androidProjectRule = AndroidProjectRule.inMemory()

  private val testData =
    resolveWorkspacePath("tools/adt/idea/app-inspection/ide/testData/libraries")

  private val fakeDownloader =
    object : Downloader {
      override fun downloadAndStream(url: URL, indicator: ProgressIndicator): InputStream? = null
      override fun downloadFully(url: URL, indicator: ProgressIndicator): Path? = null
      override fun downloadFully(
        url: URL,
        target: Path,
        checksum: Checksum?,
        indicator: ProgressIndicator
      ) {}
      override fun setDownloadIntermediatesLocation(intermediatesLocation: Path) {}

      override fun downloadFullyWithCaching(
        url: URL,
        target: Path,
        checksum: Checksum?,
        indicator: ProgressIndicator
      ) {
        // Fake download by resolving the URL against the local testData directory.
        val srcFile = testData.resolve(url.path.substringAfter('/'))
        Files.copy(srcFile, target)
      }
    }

  @Test
  fun downloadAndCacheArtifact() =
    runBlocking<Unit> {
      val fileService = TestFileService()

      val resolver =
        HttpArtifactResolver(fileService, AppInspectorArtifactPaths(fileService), fakeDownloader)
      val request =
        ArtifactCoordinate(
          "androidx.work",
          "work-runtime",
          "2.5.0-beta01",
          ArtifactCoordinate.Type.AAR
        )
      val jar = resolver.resolveArtifact(request)

      assertThat(jar).isNotNull()
      assertThat(jar.fileName.toString()).isEqualTo("inspector.jar")
    }
}
