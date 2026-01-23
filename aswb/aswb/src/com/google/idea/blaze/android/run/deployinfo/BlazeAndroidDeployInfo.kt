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

import com.android.tools.idea.run.ApkInfo
import com.android.tools.idea.run.ApkProvisionException
import com.google.idea.blaze.android.manifest.ManifestParser.ParsedManifest
import java.io.File

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

  /** Returns the full list of apks to deploy, if any.  */
  val apksToDeploy: List<File>,

  /** Returns the full list of C++ symbol files to provide to LLDB to symbolize debugging.  */
  val symbolFiles: List<File>,

  /**
   * Returns a list of [ApkInfo]s to deploy. This includes the main apk and any split apks.
   */
  val apkInfos: List<ApkInfo>
) {

  class ManifestWithApks(val manifest: ParsedManifest, val apks: List<File>)

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

  companion object {
    @Throws(ApkProvisionException::class)
    @JvmStatic
    fun createBlazeAndroidDeployInfo(
      mainAppManifestAndApks: ManifestWithApks,
      appUnderTestMergedManifestAndApks: ManifestWithApks?,
      symbolFiles: List<File>
    ): BlazeAndroidDeployInfo {

      val apkInfos = getInfos(mainAppManifestAndApks) + appUnderTestMergedManifestAndApks?.let { getInfos(it) }.orEmpty()

      val mainAppMainManifest = mainAppManifestAndApks.manifest
      val appUnderTestMergedManifest = appUnderTestMergedManifestAndApks?.manifest
      val apksToDeploy = mainAppManifestAndApks.apks + appUnderTestMergedManifestAndApks?.apks.orEmpty()

      return BlazeAndroidDeployInfo(
        mainAppMergedManifest = mainAppMainManifest,
        appUnderTestMergedManifest = appUnderTestMergedManifest,
        apksToDeploy = apksToDeploy,
        symbolFiles = symbolFiles,
        apkInfos = apkInfos
      )
    }

    @Throws(ApkProvisionException::class)
    private fun getInfos(apks: ManifestWithApks): List<ApkInfo> {
      val packageName = apks.manifest.packageName ?: throw ApkProvisionException("No application id in merged manifest.")
      return apks.apks.map { ApkInfo(it, packageName) }
    }
  }
}
