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

interface IdeNativeVariantInfo {
  /**
   * Names of ABIs built by this variant. Typical values are: x86, x86_64, armeabi-v7a, and
   * arm64-v8a Rarer values are: armeabi, mips, and mip64
   *
   * @return set of ABI names.
   */
  val abiNames: List<String>

  /**
   * Map of ABI name to corresponding build root folder for that ABI.
   *
   *
   * For CMake, the build root folder is the one that contains the generated build system. For
   * example, this is where build.ninja is emitted. This is also the folder with
   * compile_commands.json. This is the folder that is set by passing -B [folder] to CMake at
   * project generation time.
   *
   *
   * For both ndk-build and CMake, this is the folder that contains android_gradle_build.json
   * is emitted.
   *
   *
   * In general, this is the folder that contains metadata about the build for this particular
   * ABI.
   *
   *
   * There should be one entry in the map per ABI name returned by getAbiNames().
   *
   * @return the map of ABI to build root folder.
   */
  val buildRootFolderMap: Map<String, File>
}
