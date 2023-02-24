/*
 * Copyright (C) 2023 The Android Open Source Project
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
@file:JvmName("AndroidPlatforms")
package org.jetbrains.android.sdk

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager

/** Studio-specific [AndroidPlatform] constructors. We can't use [ModuleRootManager] and [AndroidSdkAdditionalData] outside of studio. */
fun getInstance(sdk: Sdk): AndroidPlatform? = AndroidSdkAdditionalData.from(sdk)?.androidPlatform

fun getInstance(module: Module): AndroidPlatform? {
  if (module.isDisposed) {
    return null
  }
  return ModuleRootManager.getInstance(module).sdk?.let { getInstance(it) }
}