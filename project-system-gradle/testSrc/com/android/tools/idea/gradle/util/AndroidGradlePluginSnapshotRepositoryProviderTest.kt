/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.util

import com.android.ide.common.repository.AgpVersion
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.diagnostic.DefaultLogger
import com.intellij.openapi.diagnostic.Logger
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.TemporaryFolder
import java.net.URL
import java.nio.file.Path

class AndroidGradlePluginSnapshotRepositoryProviderTest {

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  @get:Rule
  val testLogCollector = TestLogCollector()

  inner class TestLogCollector : ExternalResource() {
    val logger = WarningCollector()
    private lateinit var previousLogger: Logger.Factory

    override fun before() {
      previousLogger = Logger.getFactory()
      Logger.setFactory { logger }
    }

    override fun after() {
      Logger.setFactory(previousLogger)
    }
  }

  class WarningCollector : DefaultLogger("") {
    val warnings: List<String>
      get() = _warnings
    private val _warnings = mutableListOf<String>()
    override fun warn(message: String?, t: Throwable?) {
      _warnings += message ?: t.toString()
    }
  }

  class TestAndroidGradlePluginSnapshotRepositoryProvider(
    cacheDir: Path,
    private val content: Map<String, String>
  ) : AndroidGradlePluginSnapshotRepositoryProvider(cacheDir) {
    override fun readUrlData(url: String, timeout: Int, lastModified: Long): ReadUrlDataResult {
      return ReadUrlDataResult(content[url.removePrefix("https://androidx.dev/")]?.toByteArray(Charsets.UTF_8), true)
    }
  }

  @Test
  fun testGetLatestSnapshot() {
    val cacheDir = temporaryFolder.newFolder().toPath()
    val setupRepository = TestAndroidGradlePluginSnapshotRepositoryProvider(
      cacheDir,
      mapOf(
        "studio/builds" to """
        <!DOCTYPE html>
        <html>
          <body class="mdl-layout mdl-js-layout">
                    <h3>Studio Snapshots<a href="/studio/builds"><i class="material-icons mdl-list__item-icon artifact-icon-list-item">link</i></a></h3>
                    <h5>Maven Repository:</h5>
                    <pre>https://androidx.dev/studio/builds/[buildId]/artifacts/repository</pre>
                    <ul class="mdl-list">
                      <li class="mdl-list__item"><span class="mdl-list__item-primary-content"><i class="material-icons mdl-list__item-icon build-icon-list-item">build</i><a href="/studio/builds/11833616/artifacts">11833616</a></span></li>
                      <li class="mdl-list__item"><span class="mdl-list__item-primary-content"><i class="material-icons mdl-list__item-icon build-icon-list-item">build</i><a href="/studio/builds/11833579/artifacts">11833579</a></span></li>
                      <li class="mdl-list__item"><span class="mdl-list__item-primary-content"><i class="material-icons mdl-list__item-icon build-icon-list-item">build</i><a href="/studio/builds/11833478/artifacts">11833478</a></span></li>
        """.trimIndent(),
        "studio/builds/11833616/artifacts/artifacts/repository/com/android/application/com.android.application.gradle.plugin/maven-metadata.xml" to """
          <?xml version="1.0" encoding="UTF-8"?>
          <metadata>
            <groupId>com.android.application</groupId>
            <artifactId>com.android.application.gradle.plugin</artifactId>
            <versioning>
              <latest>8.6.0-dev</latest>
              <release>8.6.0-dev</release>
              <versions>
                <version>8.6.0-dev</version>
              </versions>
              <lastUpdated>20240515231712</lastUpdated>
            </versioning>
          </metadata>
      """.trimIndent()
      )
    )
    assertThat(setupRepository.getLatestSnapshot()).isEqualTo(
      AndroidGradlePluginSnapshotRepositoryProvider.SnapshotRepository(setOf(AgpVersion.parse("8.6.0-dev")), URL(
        "https://androidx.dev/studio/builds/11833616/artifacts/artifacts/repository")))
  }


  @Test
  fun testNoResponse() {
    val cacheDir = temporaryFolder.newFolder().toPath()
    val setupRepository = TestAndroidGradlePluginSnapshotRepositoryProvider(cacheDir, mapOf())
    assertThat(setupRepository.getLatestSnapshot()).isNull()
  }


  @Test
  fun testInvalidPage() {
    val cacheDir = temporaryFolder.newFolder().toPath()
    val setupRepository = TestAndroidGradlePluginSnapshotRepositoryProvider(cacheDir, mapOf("studio/builds" to "\u0000"))
    assertThat(setupRepository.getLatestSnapshot()).isNull()
    assertThat(testLogCollector.logger.warnings).containsExactly(
      "Unable to find latest AGP snapshot build. Failed to parse index page content from https://androidx.dev/studio/builds"
    )
  }

  @Test
  fun testMissingMavenMetadataPath() {
    val cacheDir = temporaryFolder.newFolder().toPath()
    val setupRepository = TestAndroidGradlePluginSnapshotRepositoryProvider(cacheDir, mapOf("studio/builds" to "/studio/builds/11833616/artifacts"))
    assertThat(setupRepository.getLatestSnapshot()).isNull()
    assertThat(testLogCollector.logger.warnings).isEmpty()
  }

  @Test
  fun testInvalidMavenMetadata() {
    val cacheDir = temporaryFolder.newFolder().toPath()
    val setupRepository = TestAndroidGradlePluginSnapshotRepositoryProvider(
      cacheDir,
      mapOf(
        "studio/builds" to "/studio/builds/11833616/artifacts",
        "studio/builds/11833616/artifacts/artifacts/repository/com/android/application/com.android.application.gradle.plugin/maven-metadata.xml" to "\u0000",
      )
    )
    assertThat(setupRepository.getLatestSnapshot()).isNull()
    assertThat(testLogCollector.logger.warnings).containsExactly(
      "Unable to find latest AGP snapshot build. Failed to parse content of https://androidx.dev/studio/builds/11833616/artifacts/artifacts/repository/com/android/application/com.android.application.gradle.plugin/maven-metadata.xml"
    )
  }

  @Test
  fun testInvalidVersionString() {
    val cacheDir = temporaryFolder.newFolder().toPath()
    val setupRepository = TestAndroidGradlePluginSnapshotRepositoryProvider(
      cacheDir,
      mapOf(
        "studio/builds" to "/studio/builds/11833616/artifacts",
        "studio/builds/11833616/artifacts/artifacts/repository/com/android/application/com.android.application.gradle.plugin/maven-metadata.xml" to """
             <?xml version="1.0" encoding="UTF-8"?>
              <metadata>
                <groupId>com.android.application</groupId>
                <artifactId>com.android.application.gradle.plugin</artifactId>
                <versioning>
                  <latest>invalid</latest>
                  <release>invalid</release>
                  <versions>
                    <version>invalid</version>
                  </versions>
                  <lastUpdated>20240515231712</lastUpdated>
                </versioning>
              </metadata>
              """.trimIndent(),
      )
    )
    assertThat(setupRepository.getLatestSnapshot()).isNull()
    assertThat(testLogCollector.logger.warnings).containsExactly(
      "Unable to find latest AGP snapshot build. Invalid AGP version 'invalid' in https://androidx.dev/studio/builds/11833616/artifacts/artifacts/repository/com/android/application/com.android.application.gradle.plugin/maven-metadata.xml"
    )
  }

}