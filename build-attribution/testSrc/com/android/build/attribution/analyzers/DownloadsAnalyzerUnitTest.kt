/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.build.attribution.analyzers

import com.android.build.attribution.data.BuildInvocationType
import com.android.build.attribution.data.GradlePluginsData
import com.android.build.attribution.data.PluginContainer
import com.android.build.attribution.data.StudioProvidedInfo
import com.android.build.attribution.data.TaskContainer
import com.android.ide.common.attribution.AndroidGradlePluginAttributionData
import com.android.ide.common.repository.GradleVersion
import com.android.testutils.MockitoKt
import com.google.common.truth.Truth
import org.gradle.tooling.Failure
import org.gradle.tooling.events.FailureResult
import org.gradle.tooling.events.OperationDescriptor
import org.gradle.tooling.events.download.FileDownloadFinishEvent
import org.gradle.tooling.events.download.FileDownloadOperationDescriptor
import org.gradle.tooling.events.download.FileDownloadResult
import org.junit.Test
import org.mockito.Mockito
import java.net.URI

class DownloadsAnalyzerUnitTest {

  @Test
  fun testDownloadsAnalyzerReceivingEvents() {
    val result = testDownloadsAnalyzer()
    Truth.assertThat((result as DownloadsAnalyzer.ActiveResult).repositoryResults.map { it.toExpectedRepositoryResult() }).containsExactly(
        ExpectedRepositoryResult(
          repository = DownloadsAnalyzer.KnownRepository.GOOGLE,
          successRequestsCount = 5,
          successRequestsTimeMs = 450,
          successRequestsBytesDownloaded = 287000,
          missedRequestsCount = 0,
          missedRequestsTimeMs = 0,
          failedRequestsCount = 0,
          failedRequestsTimeMs = 0,
          failedRequestsBytesDownloaded = 0
        ),
        ExpectedRepositoryResult(
          repository = DownloadsAnalyzer.KnownRepository.MAVEN_CENTRAL,
          successRequestsCount = 0,
          successRequestsTimeMs = 0,
          successRequestsBytesDownloaded = 0,
          missedRequestsCount = 1,
          missedRequestsTimeMs = 10,
          failedRequestsCount = 0,
          failedRequestsTimeMs = 0,
          failedRequestsBytesDownloaded = 0
        ),
        ExpectedRepositoryResult(
          repository = DownloadsAnalyzer.OtherRepository("bad.repo.one"),
          successRequestsCount = 0,
          successRequestsTimeMs = 0,
          successRequestsBytesDownloaded = 0,
          missedRequestsCount = 0,
          missedRequestsTimeMs = 0,
          failedRequestsCount = 1,
          failedRequestsTimeMs = 20,
          failedRequestsBytesDownloaded = 0
        ),
        ExpectedRepositoryResult(
          repository = DownloadsAnalyzer.OtherRepository("bad.repo.two"),
          successRequestsCount = 0,
          successRequestsTimeMs = 0,
          successRequestsBytesDownloaded = 0,
          missedRequestsCount = 0,
          missedRequestsTimeMs = 0,
          failedRequestsCount = 1,
          failedRequestsTimeMs = 10,
          failedRequestsBytesDownloaded = 0
        )
    )
  }

  private fun testDownloadsAnalyzer(): DownloadsAnalyzer.Result {
    val pluginContainer = PluginContainer()
    val taskContainer = TaskContainer()
    val analyzer = DownloadsAnalyzer()
    val wrapper = BuildAnalyzersWrapper(listOf(analyzer), taskContainer, pluginContainer)
    val attributionData = AndroidGradlePluginAttributionData(
      buildInfo = AndroidGradlePluginAttributionData.BuildInfo(
        agpVersion = "7.3",
        configurationCacheIsOn = false
      )
    )

    val projectConfigurationDescriptor = createProjectConfigurationOperationDescriptor(":")
    val pluginA = createBinaryPluginIdentifierStub("pluginA", "my.gradle.plugin.PluginA")
    val sampleTaskDescriptor = createTaskOperationDescriptorStub(":sampleTask", pluginA, emptyList())

    wrapper.onBuildStart()
    // Below is the sample download events sequence based on events from a real build.
    // 1.1) during configuration request agp artifact from google repo. First pom file is requested.
    wrapper.receiveEvent(downloadFinishEventStub(
      downloadOperationDescriptorStub(
        url = "https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/7.3.0-alpha05/gradle-7.3.0-alpha05.pom",
        parent = projectConfigurationDescriptor
      ),
      downloadSuccessStub(0, 100, 10000) // time: 100, totalRepoTime: 100, totalRepoBytes: 10000
    ))
    // 1.2) Second module file is requested.
    wrapper.receiveEvent(downloadFinishEventStub(
      downloadOperationDescriptorStub(
        url = "https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/7.3.0-alpha05/gradle-7.3.0-alpha05.module",
        parent = projectConfigurationDescriptor
      ),
      downloadSuccessStub(110, 210, 15000) // time: 100, totalRepoTime: 200, totalRepoBytes: 25000
    ))
    // 1.3) Jar file downloaded.
    wrapper.receiveEvent(downloadFinishEventStub(
      downloadOperationDescriptorStub(
        url = "https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/7.3.0-alpha05/gradle-7.3.0-alpha05.jar",
        parent = projectConfigurationDescriptor
      ),
      downloadSuccessStub(220, 320, 200000) // time: 100, totalRepoTime: 300, totalRepoBytes: 225000
    ))
    // 2) During task execution try to download lint-gradle.
    // 2.1) Request badly configured repo, download fails.
    wrapper.receiveEvent(downloadFinishEventStub(
      downloadOperationDescriptorStub(
        url = "https://bad.repo.one/snapshot/com/android/tools/lint/lint-gradle/30.3.0-alpha05/lint-gradle-30.3.0-alpha05.pom",
        parent = sampleTaskDescriptor
      ),
      downloadFailureStub(200, 220, 0, listOf(failureStub("Failed request 1", listOf(failureStub("Caused by 1", emptyList()))))) // time: 20, totalRepoTime: 20, totalRepoBytes: 0
    ))
    // 2.2) Request another badly configured repo, download fails.
    wrapper.receiveEvent(downloadFinishEventStub(
      downloadOperationDescriptorStub(
        url = "https://bad.repo.two/snapshot/com/android/tools/lint/lint-gradle/30.3.0-alpha05/lint-gradle-30.3.0-alpha05.pom",
        parent = sampleTaskDescriptor
      ),
      downloadFailureStub(230, 240, 0, listOf(failureStub("Failed request 2", emptyList()))) // time: 10, totalRepoTime: 10, totalRepoBytes: 0
    ))
    // 2.3) Request maven central, but it could not be found there.
    wrapper.receiveEvent(downloadFinishEventStub(
      downloadOperationDescriptorStub(
        url = "https://repo.maven.apache.org/maven2/com/android/tools/lint/lint-gradle/30.3.0-alpha05/lint-gradle-30.3.0-alpha05.pom",
        parent = sampleTaskDescriptor
      ),
      downloadSuccessStub(240, 250, 0) // time: 10, totalRepoTime: 10, totalRepoBytes: 0
    ))
    // 2.4) Request google repo, downloaded.
    wrapper.receiveEvent(downloadFinishEventStub(
      downloadOperationDescriptorStub(
        url = "https://dl.google.com/dl/android/maven2/com/android/tools/lint/lint-gradle/30.3.0-alpha05/lint-gradle-30.3.0-alpha05.pom",
        parent = sampleTaskDescriptor
      ),
      downloadSuccessStub(250, 300, 2000) // time: 50, totalRepoTime: 350, totalRepoBytes: 227000
    ))
    // 2.5) Jar file downloaded.
    wrapper.receiveEvent(downloadFinishEventStub(
      downloadOperationDescriptorStub(
        url = "https://dl.google.com/dl/android/maven2/com/android/tools/lint/lint-gradle/30.3.0-alpha05/lint-gradle-30.3.0-alpha05.jar",
        parent = sampleTaskDescriptor
      ),
      downloadSuccessStub(310, 410, 60000) // time: 100, totalRepoTime: 450, totalRepoBytes: 287000
    ))

    // When the build is finished successfully and the analyzer is run
    wrapper.onBuildSuccess(
      attributionData,
      GradlePluginsData.emptyData,
      Mockito.mock(BuildEventsAnalyzersProxy::class.java),
      StudioProvidedInfo(
        agpVersion = null,
        gradleVersion = GradleVersion.parse("7.3"),
        configurationCachingGradlePropertyState = null,
        buildInvocationType = BuildInvocationType.REGULAR_BUILD,
        enableJetifierPropertyState = false,
        useAndroidXPropertyState = false,
        buildRequestHolder = MockitoKt.mock()
      )
    )

    return analyzer.result
  }

  @Test
  fun testDownloadsAnalyzerInactiveWithOldGradleAndAgpVersions() = runTestWithNoEventsForAgpAndGradleVersions(
    agpVersionFromBuild = "4.3",
    gradleVersion = "7.2",
    expectAnalyzerResult = DownloadsAnalyzer.GradleDoesNotProvideEvents
  )

  @Test
  fun testDownloadsAnalyzerInactiveWithOldGradleAndMissingAgpVersions() = runTestWithNoEventsForAgpAndGradleVersions(
    // This would mean some real old AGP as we added it at least in 4.3
    agpVersionFromBuild = null,
    gradleVersion = "7.2",
    expectAnalyzerResult = DownloadsAnalyzer.GradleDoesNotProvideEvents
  )

  @Test
  fun testDownloadsAnalyzerInactiveWithOldAgpAndMissingGradleVersions() = runTestWithNoEventsForAgpAndGradleVersions(
    agpVersionFromBuild = "4.3",
    gradleVersion = null,
    expectAnalyzerResult = DownloadsAnalyzer.GradleDoesNotProvideEvents
  )

  @Test
  fun testDownloadsAnalyzerWithRecentAGP() = runTestWithNoEventsForAgpAndGradleVersions(
    // Case for when we assume Gradle version base on AGP version received from build.
    agpVersionFromBuild = "7.3",
    gradleVersion = null,
    expectAnalyzerResult = DownloadsAnalyzer.ActiveResult(emptyList())
  )

  @Test
  fun testDownloadsAnalyzerWithOldAGPButRecentGradle() = runTestWithNoEventsForAgpAndGradleVersions(
    agpVersionFromBuild = "4.3",
    gradleVersion = "7.3",
    expectAnalyzerResult = DownloadsAnalyzer.ActiveResult(emptyList())
  )

  private fun runTestWithNoEventsForAgpAndGradleVersions(agpVersionFromBuild: String?, gradleVersion: String?, expectAnalyzerResult: DownloadsAnalyzer.Result) {
    val pluginContainer = PluginContainer()
    val taskContainer = TaskContainer()
    val analyzer = DownloadsAnalyzer()
    val wrapper = BuildAnalyzersWrapper(listOf(analyzer), taskContainer, pluginContainer)
    val attributionData = AndroidGradlePluginAttributionData(
      buildInfo = AndroidGradlePluginAttributionData.BuildInfo(
        agpVersion = agpVersionFromBuild,
        configurationCacheIsOn = false
      )
    )

    wrapper.onBuildStart()
    // When the build is finished successfully and the analyzer is run
    wrapper.onBuildSuccess(
      attributionData,
      GradlePluginsData.emptyData,
      Mockito.mock(BuildEventsAnalyzersProxy::class.java),
      StudioProvidedInfo(
        agpVersion = null,
        gradleVersion = gradleVersion?.let{ GradleVersion.parse(it) },
        configurationCachingGradlePropertyState = null,
        buildInvocationType = BuildInvocationType.REGULAR_BUILD,
        enableJetifierPropertyState = false,
        useAndroidXPropertyState = false,
        buildRequestHolder = MockitoKt.mock()
      )
    )

    Truth.assertThat(analyzer.result).isEqualTo(expectAnalyzerResult)
  }
}

private fun downloadFinishEventStub(descriptor: FileDownloadOperationDescriptor, result: FileDownloadResult) =
  Mockito.mock(FileDownloadFinishEvent::class.java).apply {
    Mockito.`when`(this.descriptor).thenReturn(descriptor)
    Mockito.`when`(this.result).thenReturn(result)
  }

private fun downloadOperationDescriptorStub(url: String, parent: OperationDescriptor?) = Mockito.mock(
  FileDownloadOperationDescriptor::class.java).apply {
  Mockito.`when`(this.uri).thenReturn(URI(url))
  Mockito.`when`(this.parent).thenReturn(parent)
}

private fun downloadSuccessStub(start: Long, end: Long, bytes: Long) = object : FileDownloadResult {
  override fun getStartTime(): Long = start
  override fun getEndTime(): Long = end
  override fun getBytesDownloaded(): Long = bytes
}

private interface FailedDownloadResult : FileDownloadResult, FailureResult

private fun downloadFailureStub(start: Long, end: Long, bytes: Long, failures: List<Failure>) = object : FailedDownloadResult {
  override fun getStartTime(): Long = start
  override fun getEndTime(): Long = end
  override fun getBytesDownloaded(): Long = bytes
  override fun getFailures(): List<Failure> = failures
}

private fun failureStub(message: String, causes: List<Failure>) = object : Failure {
  override fun getMessage(): String = message
  override fun getCauses(): List<Failure> = causes
  override fun getDescription(): String? {
    throw UnsupportedOperationException("Not expected to be used.")
  }
}

private data class ExpectedRepositoryResult(
  val repository: DownloadsAnalyzer.Repository,
  val successRequestsCount: Int,
  val successRequestsTimeMs: Long,
  val successRequestsBytesDownloaded: Long,
  val missedRequestsCount: Int,
  val missedRequestsTimeMs: Long,
  val failedRequestsCount: Int,
  val failedRequestsTimeMs: Long,
  val failedRequestsBytesDownloaded: Long,
)

private fun DownloadsAnalyzer.RepositoryResult.toExpectedRepositoryResult() = ExpectedRepositoryResult(
  repository,
  successRequestsCount,
  successRequestsTimeMs,
  successRequestsBytesDownloaded,
  missedRequestsCount,
  missedRequestsTimeMs,
  failedRequestsCount,
  failedRequestsTimeMs,
  failedRequestsBytesDownloaded
)
