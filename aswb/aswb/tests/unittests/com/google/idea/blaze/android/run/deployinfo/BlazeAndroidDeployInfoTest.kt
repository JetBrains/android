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
import com.google.common.truth.Expect
import com.google.common.truth.Truth.assertThat
import com.google.idea.blaze.android.manifest.ManifestParser.ParsedManifest
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo.ManifestWithApks
import java.io.File
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

  private val nativeSymbolsPath = "symbols.so"
  private val appApkPath = "dummy.apk"
  private val testApkPath = "test.apk"
  private val appUnderTestApkPath = "app_under_test.apk"

  private val nativeSymbols: List<File> = listOf(stubFile(nativeSymbolsPath))
  private val appApks: List<File> = listOf(stubFile(appApkPath))
  private val testAppApks: List<File> = listOf(stubFile(testApkPath))
  private val appUnderTestApks: List<File> = listOf(stubFile(appUnderTestApkPath))

  @Test
  fun testBinaryDeployment_success() {
    val packageName = "app.package.name"
    val mainManifest = stubManifest(packageName)

    val deployInfo =
        BlazeAndroidDeployInfo.createBlazeAndroidDeployInfo(
          ManifestWithApks(mainManifest, appApks),
          /* testTargetMergedManifestAndApks= */ null,
          nativeSymbols)

    expect.withMessage("getMergedManifest()").that(deployInfo.mainAppMergedManifest).isEqualTo(mainManifest)
    expect.withMessage("getTestTargetMergedManifest()")
        .that(deployInfo.appUnderTestMergedManifest)
        .isNull()
    expect.withMessage("getPackageName()").that(deployInfo.mainAppPackageName).isEqualTo(packageName)
    // For binary deployment, app under test package should be null
    expect.withMessage("getAppUnderTestPackageName()")
        .that(deployInfo.appUnderTestPackageName)
        .isNull()

    expect.withMessage("getApksToDeploy()").that(deployInfo.apksToDeploy).isEqualTo(appApks)

    val apkInfos = deployInfo.apkInfos
    expect.withMessage("apkInfos size").that(apkInfos).hasSize(1)
    expect.withMessage("apkInfo package").that(apkInfos[0].applicationId).isEqualTo(packageName)
    expect.withMessage("apkInfo files").that(apkInfos[0].files.map {it.apkFile}).isEqualTo(appApks)
  }

  @Test
  fun testBinaryDeployment_noPackageName() {
    val mainManifest = stubManifest(null)

    val exception = assertThrows(ApkProvisionException::class.java) {
        BlazeAndroidDeployInfo.createBlazeAndroidDeployInfo(
          ManifestWithApks(mainManifest, appApks),
          /* testTargetMergedManifestAndApks= */ null,
          nativeSymbols
        )
    }
    assertThat(exception)
        .hasMessageThat()
        .contains("No application id in merged manifest")
  }

  @Test
  fun testTestDeployment_success() {
    val testPackageName = "test.package.name"
    val appPackageName = "app.package.name"
    val testManifest = stubManifest(testPackageName)
    val appManifest = stubManifest(appPackageName)

    val deployInfo =
        BlazeAndroidDeployInfo.createBlazeAndroidDeployInfo(
          ManifestWithApks(testManifest, testAppApks),
          ManifestWithApks(appManifest, appUnderTestApks),
          nativeSymbols)

    expect.withMessage("getMergedManifest()").that(deployInfo.mainAppMergedManifest).isEqualTo(testManifest)
    expect.withMessage("getTestTargetMergedManifest()")
        .that(deployInfo.appUnderTestMergedManifest)
        .isEqualTo(appManifest)
    expect.withMessage("getPackageName()").that(deployInfo.mainAppPackageName).isEqualTo(testPackageName)
    expect.withMessage("getAppUnderTestPackageName()")
        .that(deployInfo.appUnderTestPackageName)
        .isEqualTo(appPackageName)

    val expectedApks = testAppApks + appUnderTestApks
    expect.withMessage("getApksToDeploy()").that(deployInfo.apksToDeploy).isEqualTo(expectedApks)

    val apkInfos = deployInfo.apkInfos
    expect.withMessage("apkInfos size").that(apkInfos).hasSize(2)
    // First APKInfo is the test app
    expect.withMessage("test apkInfo package").that(apkInfos[0].applicationId).isEqualTo(testPackageName)
    expect.withMessage("test apkInfo file").that(apkInfos[0].files.map {it.apkFile}).isEqualTo(testAppApks)
    // Second APKInfo is the app under test
    expect.withMessage("app under test apkInfo package")
        .that(apkInfos[1].applicationId)
        .isEqualTo(appPackageName)
    expect.withMessage("app under test apkInfo file")
        .that(apkInfos[1].files.map {it.apkFile})
        .isEqualTo(appUnderTestApks)
  }

  @Test
  fun testTestDeployment_mainNoPackageName() {
    val testManifest = stubManifest(null)
    val appManifest = stubManifest("app.package.name")

    val exception = assertThrows(ApkProvisionException::class.java) {
        BlazeAndroidDeployInfo.createBlazeAndroidDeployInfo(
          ManifestWithApks(testManifest, testAppApks),
          ManifestWithApks(appManifest, appUnderTestApks),
          nativeSymbols
        )
    }
    assertThat(exception)
        .hasMessageThat()
        .contains("No application id in merged manifest")
  }

  @Test
  fun testTestDeployment_testTargetNoPackageName() {
    val testManifest = stubManifest("test.package.name")
    val appManifest = stubManifest(null)

    val exception = assertThrows(ApkProvisionException::class.java) {
        BlazeAndroidDeployInfo.createBlazeAndroidDeployInfo(
          ManifestWithApks(testManifest, testAppApks),
          ManifestWithApks(appManifest, appUnderTestApks),
          nativeSymbols
        )
    }
    assertThat(exception)
        .hasMessageThat()
        .contains("No application id in merged manifest")
  }
}
