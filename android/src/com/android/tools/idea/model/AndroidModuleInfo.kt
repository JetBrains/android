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
package com.android.tools.idea.model

import com.android.sdklib.AndroidVersion
import com.google.common.util.concurrent.ListenableFuture

/**
 * Android information about a module, such as its application package, its minSdkVersion, and so on.
 */
interface AndroidModuleInfo {
  /** The minimum SDK version for current Android module. */
  val moduleMinApi: Int

  /** Obtains the applicationId name for the current variant, or if not initialized, from the primary manifest. */
  val packageName: String?

  /**
   * Returns the minSdkVersion that we pass to the runtime. This is normally the same as [minSdkVersion], but with preview platforms the
   * minSdkVersion, targetSdkVersion and compileSdkVersion are all coerced to the same preview platform value. This method should be used by
   * launch code for example or packaging code.
   */
  val runtimeMinSdkVersion: ListenableFuture<AndroidVersion>

  /** The minimum SDK version for current Android module. */
  val minSdkVersion: AndroidVersion

  /** The target SDK version for current Android module. */
  val targetSdkVersion: AndroidVersion

  /** The SDK version current Android module is built with if known. */
  val buildSdkVersion: AndroidVersion?
}