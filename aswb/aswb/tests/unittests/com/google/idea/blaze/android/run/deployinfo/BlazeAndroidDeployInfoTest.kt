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

import com.android.tools.idea.run.ApkFileUnit
import com.android.tools.idea.run.ApkInfo
import com.android.tools.idea.run.ApkProvisionException
import com.google.common.truth.Expect
import com.google.common.truth.Truth.assertThat
import com.google.idea.blaze.android.manifest.ManifestParser.ParsedManifest
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo.ManifestWithApkInfo
import com.google.idea.blaze.android.run.deployinfo.DeployData.Companion.fetchApks
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.common.artifact.OutputArtifact
import com.google.idea.blaze.base.scope.BlazeContext
import com.intellij.mock.MockProject
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import java.io.File
import java.nio.file.Path
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class BlazeAndroidDeployInfoTest {
  @get:Rule var expect: Expect = Expect.create()

  private fun stubManifest(packageName: String?): ParsedManifest =
      ParsedManifest(packageName, emptyList(), null)

  private fun stubFile(name: String): File = File(name)

  private fun stubOutputArtifact(artifactPath: String): OutputArtifact = TestOutputArtifact(artifactPath)

  private val mainAppLabel = Label.of("//mainApp")
  private val testAppLabel = Label.of("//testApp")
  private val nativeSymbolsPath = "symbols.so"
  private val appApkPath = "app.apk"
  private val testApkPath = "test.apk"
  private val appUnderTestApkPath = "app_under_test.apk"

  private val appPackageName = "app.package.name"
  private val testPackageName = "test.package.name"

  private val dummyProject = MockProject(null, Disposer.newDisposable())
  private val dummyApkPath = Path.of("/local/cache/app.apk")
  private val dummyApkArtifact = stubOutputArtifact("app/app.apk")

  private val nativeSymbols: List<File> = listOf(stubFile(nativeSymbolsPath))
  private val appApkInfo = ApkInfo(listOf(ApkFileUnit("", File(appApkPath))), appPackageName)
  private val testAppApkInfo = ApkInfo(listOf(ApkFileUnit("", File(testApkPath))), testPackageName)
  private val appUnderTestApkInfo = ApkInfo(listOf(ApkFileUnit("", File(appUnderTestApkPath))), appPackageName)


  @Test
  fun testBinaryDeployment_success() {
    val mainManifest = stubManifest(appPackageName)

    val deployInfo =
        BlazeAndroidDeployInfo.createBlazeAndroidDeployInfo(
          ManifestWithApkInfo(mainAppLabel, mainManifest, appApkInfo),
          /* testTargetMergedManifestAndApks= */ null,
          nativeSymbols)

    expect.withMessage("getMergedManifest()").that(deployInfo.mainAppMergedManifest).isEqualTo(mainManifest)
    expect.withMessage("getTestTargetMergedManifest()")
        .that(deployInfo.appUnderTestMergedManifest)
        .isNull()
    expect.withMessage("getPackageName()").that(deployInfo.mainAppPackageName).isEqualTo(appPackageName)
    // For binary deployment, app under test package should be null
    expect.withMessage("getAppUnderTestPackageName()")
        .that(deployInfo.appUnderTestPackageName)
        .isNull()

    val apkInfos = deployInfo.apkInfos
    expect.withMessage("apkInfos size").that(apkInfos).hasSize(1)
    expect.withMessage("apkInfo package").that(apkInfos[0].applicationId).isEqualTo(appPackageName)
    expect.withMessage("apkInfo files").that(apkInfos[0]).isEqualTo(appApkInfo)
  }

  @Test
  fun testTestDeployment_success() {
    val testManifest = stubManifest(testPackageName)
    val appManifest = stubManifest(appPackageName)

    val deployInfo =
        BlazeAndroidDeployInfo.createBlazeAndroidDeployInfo(
          ManifestWithApkInfo(testAppLabel, testManifest, testAppApkInfo),
          ManifestWithApkInfo(mainAppLabel, appManifest, appUnderTestApkInfo),
          nativeSymbols)

    expect.withMessage("getMergedManifest()").that(deployInfo.mainAppMergedManifest).isEqualTo(testManifest)
    expect.withMessage("getTestTargetMergedManifest()")
        .that(deployInfo.appUnderTestMergedManifest)
        .isEqualTo(appManifest)
    expect.withMessage("getPackageName()").that(deployInfo.mainAppPackageName).isEqualTo(testPackageName)
    expect.withMessage("getAppUnderTestPackageName()")
        .that(deployInfo.appUnderTestPackageName)
        .isEqualTo(appPackageName)

    val apkInfos = deployInfo.apkInfos
    expect.withMessage("apkInfos size").that(apkInfos).hasSize(2)
    // First APKInfo is the test app
    expect.withMessage("test apkInfo package").that(apkInfos[0].applicationId).isEqualTo(testPackageName)
    expect.withMessage("test apkInfo file").that(apkInfos[0]).isEqualTo(testAppApkInfo)
    // Second APKInfo is the app under test
    expect.withMessage("app under test apkInfo package")
        .that(apkInfos[1].applicationId)
        .isEqualTo(appPackageName)
    expect.withMessage("app under test apkInfo file")
        .that(apkInfos[1])
        .isEqualTo(appUnderTestApkInfo)
  }

  @Test
  fun testDeployData_fetchApks_success() {
    val packageName = appPackageName
    val manifest = stubManifest(packageName)
    val artifacts = listOf(dummyApkArtifact)
    val deployData = DeployData(mainAppLabel, manifest, artifacts)

    // Mock cacheLocally function to return a known path
    val mockCacheLocally: (Project, Label, List<OutputArtifact>, BlazeContext) -> List<Path> =
        { _, _, _, _ -> listOf(dummyApkPath) }

    val result =
        deployData.fetchApks(
            dummyProject!!,
            BlazeContext.create(), // Use a stub context
            mockCacheLocally
        )

    expect.withMessage("label").that(result.label).isEqualTo(mainAppLabel)
    expect.withMessage("manifest").that(result.manifest).isEqualTo(manifest)

    val apkInfo = result.apkInfo
    expect.withMessage("apkInfo package name").that(apkInfo.applicationId).isEqualTo(packageName)
    expect.withMessage("apkInfo files size").that(apkInfo.files).hasSize(1)
    expect.withMessage("apkInfo file path")
        .that(apkInfo.files.first().apkFile.toPath())
        .isEqualTo(dummyApkPath)
  }

  @Test
  fun testDeployData_fetchApks_noPackageName() {
    val manifest = stubManifest(null) // Manifest with null package name
    val artifacts = listOf(dummyApkArtifact)
    val deployData = DeployData(mainAppLabel, manifest, artifacts)

    // Mock cacheLocally is included for signature matching, but not strictly necessary for this test case
    val mockCacheLocally: (Project, Label, List<OutputArtifact>, BlazeContext) -> List<Path> =
        { _, _, _, _ -> listOf(dummyApkPath) }

    val exception =
        assertThrows(ApkProvisionException::class.java) {
            deployData.fetchApks(
                dummyProject!!,
                BlazeContext.create(),
                mockCacheLocally
            )
        }

    assertThat(exception).hasMessageThat().contains("Valid manifest must have a package name")
  }
}

private class TestOutputArtifact(val artifact: String): OutputArtifact {
  override fun getArtifactPathPrefixLength(): Int = 3
  override fun getArtifactPath(): Path = Path.of("bazel-out/k8/bin").resolve(artifact)
  override fun getLength(): Long = 123
  override fun getDigest(): String = "HASH OF: $artifact"
}
