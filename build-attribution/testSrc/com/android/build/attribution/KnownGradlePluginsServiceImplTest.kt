/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.build.attribution

import com.android.build.attribution.data.GradlePluginsData
import com.android.testutils.MockitoKt.whenever
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.Pair
import com.intellij.testFramework.ApplicationRule
import com.intellij.util.download.FileDownloader
import com.intellij.util.download.impl.DownloadableFileDescriptionImpl
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import java.io.File
import java.io.IOException

class KnownGradlePluginsServiceImplTest {
  @get:Rule
  val applicationRule = ApplicationRule()

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  @Test
  fun testLocalGradlePluginsServiceParsesFileCorrectly() {
    val data = LocalKnownGradlePluginsServiceImpl().gradlePluginsData

    assertThat(data).isNotEqualTo(GradlePluginsData.emptyData)
    assertThat(data.pluginsInfo).hasSize(40)
    assertThat(data.pluginsInfo.filter { it.configurationCachingCompatibleFrom == null }).hasSize(8)
    assertThat(data.pluginsInfo.filter { it.pluginArtifact == null }).isEmpty()
  }

  @Test
  fun testGetsDownloadedFile() {
    val fileContent = """
{
  "pluginsInfo": [
    {
      "pluginClassPrefixes": [
        "my.plugin.pluginA",
        "my.plugin.pluginB"
      ],
      "name": "MyPlugin",
      "pluginDescription": "Fake test plugin description.<br/>",
      "pluginContactInstructions": "<a href='linkToPluginRepo'>Plugin repository</a>",
      "pluginArtifact": "org.my:gradle-plugin",
      "configurationCachingCompatibleFrom": "1.0.0"
    }
  ]
}
    """.trimIndent()
    val outputDir = temporaryFolder.newFolder()
    val fileName = "plugins_data.json"
    val distributionFile = FileUtils.join(outputDir, fileName)
    val localCache = FileUtils.join(outputDir, "cache")

    distributionFile.parentFile.mkdirs()
    distributionFile.writeText(fileContent)

    val distributionUrl = distributionFile.toURI().toURL()
    val downloadableFileDescription = DownloadableFileDescriptionImpl(distributionUrl.toString(), fileName, "json")

    val downloader = Mockito.mock(FileDownloader::class.java)
    whenever(downloader.download(ArgumentMatchers.any(File::class.java)))
      .thenReturn(listOf(Pair(distributionFile, downloadableFileDescription)))

    val service = KnownGradlePluginsServiceImpl(downloader, localCache)
    service.refreshSynchronously()
    assertThat(service.gradlePluginsData).isNotEqualTo(GradlePluginsData.emptyData)
  }

  @Test
  fun testDownloadFailure() {
    val outputDir = temporaryFolder.newFolder()
    val localCache = FileUtils.join(outputDir, "cache")
    val downloader = Mockito.mock(FileDownloader::class.java)
    whenever(downloader.download(ArgumentMatchers.any(File::class.java)))
      .thenThrow(IOException())

    val service = KnownGradlePluginsServiceImpl(downloader, localCache)
    service.refreshSynchronously()
    assertThat(service.gradlePluginsData).isNotEqualTo(GradlePluginsData.emptyData)
  }
}