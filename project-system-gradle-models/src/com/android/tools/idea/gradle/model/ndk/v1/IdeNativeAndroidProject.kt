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
package com.android.tools.idea.gradle.model.ndk.v1

import java.io.File

interface IdeNativeAndroidProject {
  /** Returns the model version. This is a string in the format X.Y.Z  */
  val modelVersion: String

  /**
   * Returns the model api version.
   *
   *
   * This is different from [.getModelVersion] in a way that new model version might
   * increment model version but keep existing api. That means that code which was built against
   * particular 'api version' might be safely re-used for all new model versions as long as they
   * don't change the api.
   *
   *
   * Every new model version is assumed to return an 'api version' value which is equal or
   * greater than the value used by the previous model version.
   */
  val apiVersion: Int

  /** Returns the name of the module.  */
  val name: String

  /**
   * Returns a map of variant name to information about that variant. For example, it contains
   * this list of ABIs built for that variant.
   */
  val variantInfos: Map<String, IdeNativeVariantInfo>

  /** Returns a collection of files that affects the build.  */
  val buildFiles: Collection<File>

  /** Returns a collection of native artifacts.  */
  val artifacts: Collection<IdeNativeArtifact>

  /** Returns a collection of toolchains.  */
  val toolChains: Collection<IdeNativeToolchain>

  /** Returns a collection of all compile settings.  */
  val settings: Collection<IdeNativeSettings>

  /**
   * Return a map of file extension to each file type.
   *
   *
   * The key is the file extension, the value is either "c" or "c++".
   */
  val fileExtensions: Map<String, String>

  /** Return the names of build systems used to create the native artifacts.  */
  val buildSystems: Collection<String>

  /** Get the default NDK version.  */
  val defaultNdkVersion: String

  companion object {
    const val BUILD_SYSTEM_UNKNOWN = "unknown"
    const val BUILD_SYSTEM_GRADLE = "gradle"
    const val BUILD_SYSTEM_CMAKE = "cmake"
    const val BUILD_SYSTEM_NDK_BUILD = "ndkBuild"
  }
}
