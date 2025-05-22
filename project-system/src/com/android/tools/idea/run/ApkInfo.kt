/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.run

import com.android.ide.common.build.BaselineProfileDetails
import com.android.sdklib.AndroidVersion
import com.google.common.base.Preconditions
import java.io.File

/**
 * The list of files to install for a given application ID.
 *
 *  * For single APK applications, [.getFiles] contains a single [File]
 *  * For split APK applications (e.g. app + dynamic features),
 *    [.getFiles] contains the corresponding list of APK files
 *  * For Instant App applications, [.getFiles] contains a single [File]
 *    which points to a zip file containing the app and its optional features.
 */

/**
 *  @param files The list of files to deploy for the given [.getApplicationId].
 *              The APK file(s). Contains at least one element.  */
/** @param applicationId The manifest package name for the APK (the app ID).  */
/** @param requiredInstallOptions A set of required "pm install" options to install this APK.  */
/** @param isSandboxApk A flag indicating if the APK is the sandbox APK. */
/** @param baselineProfiles: List of baseline profile which can be installed along with the apks. */
data class ApkInfo @JvmOverloads constructor (
  val files: List<ApkFileUnit>,
  val applicationId: String,
  val requiredInstallOptions: Set<AppInstallOption> = emptySet(),
  val isSandboxApk: Boolean = false,
  val baselineProfiles: List<BaselineProfileDetails> = emptyList(),
  val minSdkVersionForDexing: Int? = null,
) {
  init {
    Preconditions.checkArgument(files.isNotEmpty())
  }

  /**
   * An available "pm options".
   */
  enum class AppInstallOption(@JvmField val minSupportedApiLevel: Int) {
    // Request to be installed with "all privileges" (-g).
    GRANT_ALL_PERMISSIONS(AndroidVersion.VersionCodes.M),  // Request to be installed as queryable (--force-queryable).
    FORCE_QUERYABLE(AndroidVersion.VersionCodes.R)
  }

  @JvmOverloads
  constructor(
    file: File,
    applicationId: String,
    requiredInstallOptions: Set<AppInstallOption> = emptySet(),
    isSandboxApk: Boolean = false,
    baselineProfiles: List<BaselineProfileDetails> = emptyList(),
    minSdkVersionForDexing: Int? = null,
  ) : this(listOf(ApkFileUnit("", file)), applicationId, requiredInstallOptions, isSandboxApk, baselineProfiles, minSdkVersionForDexing)

  constructor(
    file: File,
    applicationId: String,
    minSdkVersionForDexing: Int? = null,
  ) : this(listOf(ApkFileUnit("", file)), applicationId, emptySet(), false, emptyList(), minSdkVersionForDexing)

  constructor(apkFileList: List<ApkFileUnit>, pkgName: String,
              baselineProfiles: List<BaselineProfileDetails>, minSdkVersionForDexing: Int?) :
    this(files = apkFileList, pkgName, baselineProfiles = baselineProfiles, minSdkVersionForDexing = minSdkVersionForDexing) {
  }
}
