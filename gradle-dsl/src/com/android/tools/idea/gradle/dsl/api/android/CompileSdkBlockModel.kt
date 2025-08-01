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
package com.android.tools.idea.gradle.dsl.api.android

import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel
import com.android.tools.idea.gradle.dsl.api.util.GradleBlockModel

interface CompileSdkBlockModel : GradleBlockModel {
  fun getVersion(): CompileSdkVersionModel?
  fun setReleaseVersion(version: Int, minorApi: Int?, extension: Int?)
  fun setPreviewVersion(version: String)
  fun setAddon(vendorName: String, addonName: String, apiLevel: Int)
}

interface CompileSdkVersionModel {
  fun toHash(): String?
  fun toInt(): Int?
  fun getVersion(): ResolvedPropertyModel
  fun delete()
}

interface CompileSdkReleaseModel : CompileSdkVersionModel {
  fun getMinorApiLevel(): ResolvedPropertyModel
  fun getSdkExtension(): ResolvedPropertyModel
}

interface CompileSdkPreviewModel : CompileSdkVersionModel

interface CompileSdkAddonModel : CompileSdkVersionModel {
  fun getVendorName(): ResolvedPropertyModel
  fun getAddonName(): ResolvedPropertyModel
}