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
@file:JvmName("SdkExtensions")

package com.android.tools.idea.sdk.extensions

import com.intellij.openapi.projectRoots.Sdk

fun Sdk.isEqualTo(sdk: Sdk): Boolean {
  if (name != sdk.name) return false
  if (homePath != sdk.homePath) return false
  if (versionString != sdk.versionString) return false
  if (sdkType != sdk.sdkType) return false
  if (sdkAdditionalData != sdk.sdkAdditionalData) return false
  if (!rootProvider.isEqualTo(sdk.rootProvider)) return false
  return true
}