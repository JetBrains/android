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

interface IdeNativeArtifact {

  /**
   * Returns the name of the artifact.
   */
  val name: String

  /**
   * Returns the toolchain used for compilation.
   */
  val toolChain: String

  /**
   * Returns the group this artifact is associated with.
   */
  val groupName: String

  /**
   * Returns the source files.
   */
  val sourceFiles: Collection<IdeNativeFile>

  /**
   * Returns the folders container headers exported for the library.
   */
  val exportedHeaders: Collection<File>

  /**
   * Returns the target ABI of the artifact.
   */
  val abi: String

  /**
   * Returns the name of the target that builds this artifact.
   */
  val targetName: String

  /** Returns the output file with debug symbols unstripped.  */
  val outputFile: File?
}
