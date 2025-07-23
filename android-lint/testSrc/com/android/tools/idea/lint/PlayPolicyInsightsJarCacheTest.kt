/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.lint

import com.android.ide.common.gradle.Version
import com.android.ide.common.repository.GoogleMavenRepository
import com.android.repository.api.Checksum
import com.android.repository.api.Downloader
import com.android.repository.api.ProgressIndicator
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gservices.DevServicesDeprecationData
import com.android.tools.idea.gservices.DevServicesDeprecationDataProvider
import com.android.tools.idea.gservices.DevServicesDeprecationStatus
import com.android.tools.idea.lint.common.LintIgnoredResult
import com.android.tools.idea.testing.disposable
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.replaceService
import java.io.InputStream
import java.net.URL
import java.nio.file.Path
import kotlin.io.path.extension
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.runBlocking
import org.gradle.internal.impldep.org.junit.rules.TemporaryFolder
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PlayPolicyInsightsJarCacheTest {

  @get:Rule val projectRule = ProjectRule()

  private val unsupportedData: DevServicesDeprecationData =
    DevServicesDeprecationData(
      header = "",
      description = "my description",
      moreInfoUrl = "link",
      showUpdateAction = true,
      status = DevServicesDeprecationStatus.UNSUPPORTED,
    )

  private lateinit var client: AndroidLintIdeClient
  private lateinit var downloader: Downloader
  private val temporaryFolder = TemporaryFolder()
  private lateinit var mockDeprecationService: DevServicesDeprecationDataProvider
  private lateinit var mockRepository: GoogleMavenRepository

  @Before
  fun setUp() {
    temporaryFolder.create()
    client = AndroidLintIdeClient(projectRule.project, LintIgnoredResult())
    downloader =
      object : Downloader {
        override fun downloadAndStream(url: URL, indicator: ProgressIndicator): InputStream? = null

        override fun downloadFully(url: URL, indicator: ProgressIndicator): Path? = null

        override fun downloadFully(
          url: URL,
          target: Path,
          checksum: Checksum?,
          indicator: ProgressIndicator,
        ) {
          val data =
            if (target.extension == "sha256")
              "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"
            else "test"
          target.toFile().writeBytes(data.toByteArray())
        }
      }
    mockRepository = mock<GoogleMavenRepository>()
    doAnswer {
        assertThat(it.arguments[0]).isEqualTo("com.google.play.policy.insights")
        assertThat(it.arguments[1]).isEqualTo("insights-lint")
        val allowPreview = it.arguments[3] as Boolean
        if (allowPreview) {
          Version.parse("7.7.7-alpha1")
        } else {
          Version.parse("7.7.7")
        }
      }
      .whenever(mockRepository)
      .findVersion(
        any<String>(),
        any<String>(),
        anyOrNull<((Version) -> Boolean)>(),
        any<Boolean>(),
      )
  }

  @After
  fun tearDown() {
    temporaryFolder.delete()
    StudioFlags.PLAY_POLICY_INSIGHTS_TARGET_LIBRARY_VERSION.clearOverride()
  }

  private fun configureDeprecationService(isUnsupported: Boolean = true) {
    mockDeprecationService = mock()
    doAnswer { if (isUnsupported) unsupportedData else DevServicesDeprecationData.EMPTY }
      .whenever(mockDeprecationService)
      .getCurrentDeprecationData(any(), any())
    ApplicationManager.getApplication()
      .replaceService(
        DevServicesDeprecationDataProvider::class.java,
        mockDeprecationService,
        projectRule.disposable,
      )
  }

  @Test
  fun testPlayPolicyInsightsDeprecatedWithTargetVersion() = runBlocking {
    configureDeprecationService()
    StudioFlags.PLAY_POLICY_INSIGHTS_TARGET_LIBRARY_VERSION.override("8.8.8")
    val cache = PlayPolicyInsightsJarCache(client, temporaryFolder.root.toPath(), downloader)
    cache.getCustomRuleJars()
    cache.isUpdating.takeWhile { it }.collect()
    val updatedResult = cache.getCustomRuleJars().last()
    assertThat(updatedResult.name).isEqualTo("insights-lint-8.8.8.jar")

    // No more updates after downloading target version of library.
    cache.getCustomRuleJars()
    assertThat(cache.isUpdating.value).isFalse()
  }

  @Test
  fun testPlayPolicyInsightsInCanary() = runBlocking {
    configureDeprecationService(false)
    val cache =
      PlayPolicyInsightsJarCache(
        client,
        temporaryFolder.root.toPath(),
        downloader,
        mockRepository,
        true,
      )
    cache.getCustomRuleJars()
    cache.isUpdating.takeWhile { it }.collect()
    val updatedResult = cache.getCustomRuleJars().last()
    assertThat(updatedResult.name).isEqualTo("insights-lint-7.7.7-alpha1.jar")
  }

  @Test
  fun testPlayPolicyInsightsInStable() = runBlocking {
    configureDeprecationService(false)
    val cache =
      PlayPolicyInsightsJarCache(
        client,
        temporaryFolder.root.toPath(),
        downloader,
        mockRepository,
        false,
      )
    cache.getCustomRuleJars()
    cache.isUpdating.takeWhile { it }.collect()
    val updatedResult = cache.getCustomRuleJars().last()
    assertThat(updatedResult.name).isEqualTo("insights-lint-7.7.7.jar")
  }

  @Test
  fun testPlayPolicyInsightsDeprecatedWithoutTargetVersion() = runBlocking {
    configureDeprecationService()
    StudioFlags.PLAY_POLICY_INSIGHTS_TARGET_LIBRARY_VERSION.override("")
    // Download a library before updating.
    downloader.downloadFully(
      mock(),
      temporaryFolder.root.toPath().resolve("insights-lint-7.7.7.jar"),
      null,
      mock(),
    )
    downloader.downloadFully(
      mock(),
      temporaryFolder.root.toPath().resolve("insights-lint-7.7.7.jar.sha256"),
      null,
      mock(),
    )

    val cache = PlayPolicyInsightsJarCache(client, temporaryFolder.root.toPath(), downloader)
    cache.getCustomRuleJars()
    cache.isUpdating.takeWhile { it }.collect()
    val updatedResult = cache.getCustomRuleJars().last()
    assertThat(updatedResult.name).isEqualTo("insights-lint-7.7.7.jar")

    // No more updates after downloading target version of library.
    cache.getCustomRuleJars()
    assertThat(cache.isUpdating.value).isFalse()
  }
}
