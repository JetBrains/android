/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
import com.google.idea.blaze.android.manifest.ManifestParser.ParsedManifest
import com.google.idea.blaze.android.run.BazelApkProvider
import com.google.idea.blaze.android.run.BazelApplicationIdProvider
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo.ManifestWithApkInfo
import com.google.idea.blaze.base.run.RuntimeArtifactCache
import com.google.idea.blaze.base.run.RuntimeArtifactKind
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.base.sync.data.BlazeDataStorage
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.common.artifact.OutputArtifact
import com.intellij.openapi.project.Project
import java.io.File
import java.nio.file.Path

/** Info about the deployment phase.  */
class BlazeAndroidDeployInfo private constructor(
  /**
   * Returns parsed manifest of the main target for this deployment. During normal app deployment,
   * the main target is the android_binary that builds the app itself. During instrumentation tests
   * the main target is the android_binary/android_test target responsible for instrumenting the
   * app, while the merged manifest of the app under test can be obtained through [BlazeAndroidDeployInfo.appUnderTestMergedManifest].
   */
  val mainAppMergedManifest: ParsedManifest,

  /**
   * Returns parsed manifest of the app under test during an instrumentation test. This method
   * returns null in all other scenarios.
   */
  val appUnderTestMergedManifest: ParsedManifest?,

  /** Returns the full list of C++ symbol files to provide to LLDB to symbolize debugging.  */
  val symbolFiles: List<File>,

  /**
   * Returns a list of [ApkInfo]s to deploy. This includes the main apk and any split apks.
   */
  val apkInfos: List<ApkInfo>
) {

  class ManifestWithApkInfo(val label: Label, val manifest: ParsedManifest, val apkInfo: ApkInfo)

  /**
   * Returns the primary application ID for the app being launched (either an android_binary app or
   * a test instrumentation app).
   */
  val mainAppPackageName: String
    @Throws(ApkProvisionException::class)
    get() = mainAppMergedManifest.packageName ?: throw ApkProvisionException("No application id in merged manifest.")

  /**
   * Returns the application ID of the app under test for instrumentation tests.
   *
   * If [BlazeAndroidDeployInfo.appUnderTestMergedManifest] is null (i.e., the
   * test app is testing itself), this falls back to [BlazeAndroidDeployInfo.mainAppPackageName].
   */
  val appUnderTestPackageName: String?
    @Throws(ApkProvisionException::class)
    get() = appUnderTestMergedManifest?.let { it.packageName ?: throw ApkProvisionException("No application id in merged manifest.") }

  fun toInstrumentationTestApplicationIdProvider(): BazelApplicationIdProvider =
    BazelApplicationIdProvider(appUnderTestPackageName ?: mainAppPackageName, mainAppPackageName)

  fun toAndroidBinaryApplicationIdProvider(): BazelApplicationIdProvider =
    BazelApplicationIdProvider(mainAppPackageName, testPackageName = null)

  fun toApkProvider(): BazelApkProvider = BazelApkProvider(apkInfos, symbolFiles)

  companion object {
    @Throws(ApkProvisionException::class)
    @JvmStatic
    fun createBlazeAndroidDeployInfo(
      mainAppManifestAndApks: ManifestWithApkInfo,
      appUnderTestManifestAndApks: ManifestWithApkInfo?,
      symbolFiles: List<File>
    ): BlazeAndroidDeployInfo {

      val apkInfos = listOfNotNull(mainAppManifestAndApks.apkInfo, appUnderTestManifestAndApks?.apkInfo)

      val mainAppManifest = mainAppManifestAndApks.manifest
      val appUnderTestManifest = appUnderTestManifestAndApks?.manifest

      return BlazeAndroidDeployInfo(
        mainAppMergedManifest = mainAppManifest,
        appUnderTestMergedManifest = appUnderTestManifest,
        symbolFiles = symbolFiles,
        apkInfos = apkInfos
      )
    }
  }
}

data class DeployData(val targetLabel: Label, val mergedManifest: ParsedManifest, val apks: List<OutputArtifact>) {

  companion object {
    @JvmStatic
    fun create(targetLabel: Label, mergedManifest: ParsedManifest, apks: List<OutputArtifact>): DeployData {
      return DeployData(targetLabel, mergedManifest, apks)
    }

    @Throws(ApkProvisionException::class)
    @JvmStatic
    @JvmOverloads
    fun DeployData.fetchApks(
      project: Project,
      context: BlazeContext,
      cacheLocally: (project: Project, targetLabel: Label, artifacts: List<OutputArtifact>, context: BlazeContext) -> List<Path> = ::cacheLocally,
    ): ManifestWithApkInfo {
      return ManifestWithApkInfo(
        targetLabel,
        mergedManifest,
        ApkInfo(
          cacheLocally(project, targetLabel, apks, context)
            .map { ApkFileUnit(BlazeDataStorage.WORKSPACE_MODULE_NAME, it.toFile()) }
            .toList(),
          mergedManifest.packageName ?: throw ApkProvisionException("Valid manifest must have a package name")
        )
      )
    }
  }
}

private fun cacheLocally(
  project: Project, targetLabel: Label, artifacts: List<OutputArtifact>, context: BlazeContext,
): List<Path> {
  val runtimeArtifactCache = RuntimeArtifactCache.getInstance(project)
  return runtimeArtifactCache.fetchArtifacts(targetLabel, artifacts, context, RuntimeArtifactKind.APK)
}
