/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.model

interface IdeBuildType : IdeBaseConfig {
  /** Whether the build type is configured to generate a debuggable apk. */
  val isDebuggable: Boolean

  /** Whether the build type is configured to generate an apk with debuggable native code. */
  val isJniDebuggable: Boolean

  /** Whether the build type is configured to generate an apk with debuggable renderscript code. */
  val isRenderscriptDebuggable: Boolean

  /** The optimization level of the renderscript compilation. */
  val renderscriptOptimLevel: Int

  /**
   * Specifies whether to enable code shrinking for this build type.
   *
   * By default, when you enable code shrinking by setting this property to `true`,
   * the Android plugin uses ProGuard.
   *
   * To learn more, read
   * [Shrink Your Code and Resources](https://developer.android.com/studio/build/shrink-code.html).
   */
  val isMinifyEnabled: Boolean

  /** Whether zipalign is enabled for this build type. */
  val isZipAlignEnabled: Boolean

  /** Whether this build type is specified as a default by the user*/
  val isDefault: Boolean?
}
