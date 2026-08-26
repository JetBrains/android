/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.run.deployinfo

import com.android.tools.idea.run.ApkProvisionException
import com.google.common.collect.ImmutableList
import com.google.common.truth.Expect
import com.google.common.truth.Truth.assertThat
import com.google.idea.blaze.android.manifest.ManifestParser.ParsedManifest
import com.google.idea.blaze.android.run.NativeSymbolFinder
import com.google.idea.blaze.base.BlazeTestCase
import com.google.idea.blaze.base.command.buildresult.BuildResult
import com.google.idea.blaze.base.run.DeployedApplicationTargetStore
import com.google.idea.blaze.base.run.RuntimeArtifactCache
import com.google.idea.blaze.base.run.RuntimeArtifactKind
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.common.artifact.OutputArtifact
import com.google.idea.common.experiments.ExperimentService
import com.google.idea.common.experiments.MockExperimentService
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.project.Project
import java.io.File
import java.nio.file.Path
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class BlazeAndroidDeployInfoTest : BlazeTestCase() {
  @get:Rule var expect: Expect = Expect.create()

  private val mockExperimentService = MockExperimentService()
  private var symbolFinderEp: ExtensionPointImpl<NativeSymbolFinder>? = null

  private fun stubManifest(packageName: String?): ParsedManifest = ParsedManifest(packageName, emptyList(), null)

  private fun stubOutputArtifact(artifactPath: String): OutputArtifact = TestOutputArtifact(artifactPath)

  private val mainAppLabel = Label.of("//mainApp")
  private val testAppLabel = Label.of("//testApp")

  private val appPackageName = "app.package.name"
  private val testPackageName = "test.package.name"

  private val dummyApkPath = Path.of("/local/cache/app.apk")
  private val dummyApkArtifact = stubOutputArtifact("app/app.apk")
  private val dummyBuildOutputs = BlazeBuildOutputs.noOutputs(BuildResult.SUCCESS)
  private val mockArtifactCache = MockRuntimeArtifactCache()
  private val mockTargetStore = MockDeployedApplicationTargetStore()

  override fun initTest(applicationServices: Container, projectServices: Container) {
    super.initTest(applicationServices, projectServices)
    applicationServices.register(ExperimentService::class.java, mockExperimentService)
    projectServices.register(RuntimeArtifactCache::class.java, mockArtifactCache)
    projectServices.register(DeployedApplicationTargetStore::class.java, mockTargetStore)
    symbolFinderEp = registerExtensionPoint(NativeSymbolFinder.EP_NAME, NativeSymbolFinder::class.java)
  }

  private fun registerMockSymbolFinder(symbolFile: File) {
    val mockSymbolFinder =
      object : NativeSymbolFinder {
        override val additionalBuildFlags: String = "--output_groups=+test_ndk_symbols"

        override fun getNativeSymbolsForBuild(
          project: Project,
          context: BlazeContext,
          target: Label,
          buildOutputs: BlazeBuildOutputs,
        ): List<File> = listOf(symbolFile)
      }
    symbolFinderEp!!.registerExtension(mockSymbolFinder)
  }

  @Test
  fun testFetchDeployArtifacts_success_binary() {
    val mainAppPackageName = appPackageName
    val mainAppManifest = stubManifest(mainAppPackageName)
    val mainAppArtifacts = listOf(dummyApkArtifact)
    val mainAppDeployData = DeployData(mainAppLabel, mainAppManifest, mainAppArtifacts)

    // Mock cacheLocally function to return a known path
    val mockCacheLocally: CacheLocallyFunction = { _, _, _, _ -> listOf(dummyApkPath) }

    val deployInfo =
      BlazeAndroidDeployInfo.fetchDeployArtifacts(
        project,
        dummyBuildOutputs,
        mainApp = mainAppDeployData,
        appUnderTest = null,
        fetchNativeSymbols = false,
        context = BlazeContext.create(),
        cacheLocally = mockCacheLocally,
      )

    expect.withMessage("mainAppPackageName").that(deployInfo.mainAppPackageName).isEqualTo(mainAppPackageName)
    expect.withMessage("trackedTargets").that(mockTargetStore.trackedTargets).containsEntry(mainAppPackageName, mainAppLabel)

    val apkInfos = deployInfo.apkInfos
    expect.withMessage("apkInfos size").that(apkInfos).hasSize(1)
    expect.withMessage("apkInfo package name").that(apkInfos.first().applicationId).isEqualTo(mainAppPackageName)

    val apkFileUnit = apkInfos.first().files.first()
    expect.withMessage("apkInfo files size").that(apkInfos.first().files).hasSize(1)
    expect.withMessage("apkInfo file path").that(apkFileUnit.apkFile.toPath()).isEqualTo(dummyApkPath)
  }

  @Test
  fun testFetchDeployArtifacts_success_instrumentation_test() {
    val mainAppManifest = stubManifest(testPackageName)
    val appUnderTestManifest = stubManifest(appPackageName)

    val mainAppArtifacts = listOf(stubOutputArtifact("test/test.apk"))
    val appUnderTestArtifacts = listOf(stubOutputArtifact("app/app_under_test.apk"))

    val mainAppDeployData = DeployData(testAppLabel, mainAppManifest, mainAppArtifacts)
    val appUnderTestDeployData = DeployData(mainAppLabel, appUnderTestManifest, appUnderTestArtifacts)

    val mainAppCachedPath = Path.of("/local/cache/test_app.apk")
    val appUnderTestCachedPath = Path.of("/local/cache/app_under_test_apk.apk")

    // Mock cacheLocally function to return distinct paths based on target label
    val mockCacheLocally: CacheLocallyFunction = { _, label, _, _ ->
      when (label) {
        testAppLabel -> listOf(mainAppCachedPath)
        mainAppLabel -> listOf(appUnderTestCachedPath)
        else -> listOf(dummyApkPath)
      }
    }

    val deployInfo =
      BlazeAndroidDeployInfo.fetchDeployArtifacts(
        project,
        dummyBuildOutputs,
        mainApp = mainAppDeployData,
        appUnderTest = appUnderTestDeployData,
        fetchNativeSymbols = false,
        context = BlazeContext.create(),
        cacheLocally = mockCacheLocally,
      )

    expect.withMessage("mainAppPackageName").that(deployInfo.mainAppPackageName).isEqualTo(testPackageName)
    expect.withMessage("appUnderTestPackageName").that(deployInfo.appUnderTestPackageName).isEqualTo(appPackageName)
    expect.withMessage("main app tracked target").that(mockTargetStore.trackedTargets).containsEntry(testPackageName, testAppLabel)
    expect.withMessage("app under test tracked target").that(mockTargetStore.trackedTargets).containsEntry(appPackageName, mainAppLabel)

    val apkInfos = deployInfo.apkInfos
    expect.withMessage("apkInfos size").that(apkInfos).hasSize(2)

    // First APKInfo is the main app (test app)
    val mainAppApkInfo = apkInfos[0]
    expect.withMessage("main app apkInfo package").that(mainAppApkInfo.applicationId).isEqualTo(testPackageName)
    expect.withMessage("main app apkInfo file path").that(mainAppApkInfo.files.first().apkFile.toPath()).isEqualTo(mainAppCachedPath)

    // Second APKInfo is the app under test
    val appUnderTestApkInfo = apkInfos[1]
    expect.withMessage("app under test apkInfo package").that(appUnderTestApkInfo.applicationId).isEqualTo(appPackageName)
    expect
      .withMessage("app under test apkInfo file path")
      .that(appUnderTestApkInfo.files.first().apkFile.toPath())
      .isEqualTo(appUnderTestCachedPath)
  }

  @Test
  fun testFetchDeployArtifacts_fetchNativeSymbolsTrue_fetchesNativeSymbols() {
    val symbolFile = File("/tmp/libnative.so")
    registerMockSymbolFinder(symbolFile)

    val mainAppManifest = stubManifest(appPackageName)
    val mainAppDeployData = DeployData(mainAppLabel, mainAppManifest, listOf(dummyApkArtifact))
    val mockCacheLocally: CacheLocallyFunction = { _, _, _, _ -> listOf(dummyApkPath) }

    val deployInfo =
      BlazeAndroidDeployInfo.fetchDeployArtifacts(
        project,
        dummyBuildOutputs,
        mainApp = mainAppDeployData,
        appUnderTest = null,
        fetchNativeSymbols = true,
        context = BlazeContext.create(),
        cacheLocally = mockCacheLocally,
      )

    assertThat(deployInfo.symbolFiles).containsExactly(symbolFile)
  }

  @Test
  fun testFetchDeployArtifacts_fetchNativeSymbolsFalse_omitsNativeSymbols() {
    val symbolFile = File("/tmp/libnative.so")
    registerMockSymbolFinder(symbolFile)

    val mainAppManifest = stubManifest(appPackageName)
    val mainAppDeployData = DeployData(mainAppLabel, mainAppManifest, listOf(dummyApkArtifact))
    val mockCacheLocally: CacheLocallyFunction = { _, _, _, _ -> listOf(dummyApkPath) }

    val deployInfo =
      BlazeAndroidDeployInfo.fetchDeployArtifacts(
        project,
        dummyBuildOutputs,
        mainApp = mainAppDeployData,
        appUnderTest = null,
        fetchNativeSymbols = false,
        context = BlazeContext.create(),
        cacheLocally = mockCacheLocally,
      )

    assertThat(deployInfo.symbolFiles).isEmpty()
  }

  @Test
  fun testFetchDeployArtifacts_noPackageName_throwsException() {
    val mainAppManifest = stubManifest(null) // Manifest with null package name
    val mainAppArtifacts = listOf(dummyApkArtifact)
    val mainAppDeployData = DeployData(mainAppLabel, mainAppManifest, mainAppArtifacts)

    // Mock cacheLocally is included for signature matching.
    val mockCacheLocally: CacheLocallyFunction = { _, _, _, _ -> listOf(dummyApkPath) }

    val exception =
      assertThrows(ApkProvisionException::class.java) {
        BlazeAndroidDeployInfo.fetchDeployArtifacts(
          project,
          dummyBuildOutputs,
          mainApp = mainAppDeployData,
          appUnderTest = null,
          fetchNativeSymbols = false,
          context = BlazeContext.create(),
          cacheLocally = mockCacheLocally,
        )
      }

    assertThat(exception).hasMessageThat().contains("Valid manifest must have a package name")
  }
}

private class MockDeployedApplicationTargetStore : DeployedApplicationTargetStore {
  val trackedTargets = mutableMapOf<String, Label>()

  override fun trackTargetForApplication(applicationId: String, target: Label) {
    trackedTargets[applicationId] = target
  }

  override fun getTargetForApplication(applicationId: String): Label? = trackedTargets[applicationId]

  override fun getAllApplicationIds(): Set<String> = trackedTargets.keys
}

private class MockRuntimeArtifactCache : RuntimeArtifactCache {
  override fun fetchArtifacts(
    target: Label,
    artifacts: List<OutputArtifact>,
    context: BlazeContext,
    artifactKind: RuntimeArtifactKind,
  ): ImmutableList<Path> {
    return ImmutableList.of()
  }

  override fun getCachedArtifacts(
    target: Label,
    artifactKind: RuntimeArtifactKind,
  ): ImmutableList<Path> {
    return ImmutableList.of()
  }
}

private class TestOutputArtifact(val artifact: String) : OutputArtifact {
  override val artifactPath: Path
    get() = Path.of("bazel-out/k8/bin").resolve(artifact)

  override val artifactPathPrefixLength: Int
    get() = 3

  override val digest: String
    get() = "HASH OF: $artifact"

  override val length: Long
    get() = 123
}
