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

import java.io.File

interface IdeAndroidArtifactOutput {

  companion object {
    /** An object representing the lack of filter.  */
    @JvmField
    val NO_FILTER: String? = null

    /**
     * String representation of the OutputType enum which can be used for remote-able interfaces.
     */
    @JvmField
    val MAIN = OutputType.MAIN.name

    @JvmField
    val FULL_SPLIT = OutputType.FULL_SPLIT.name

    /**
     * String representations of the FilterType enum which can be used for remote-able interfaces.Ap
     */
    @JvmField
    val DENSITY = FilterType.DENSITY.name

    @JvmField
    val ABI = FilterType.ABI.name

    @JvmField
    val LANGUAGE = FilterType.LANGUAGE.name
  }

  /**
   * Type of package file, either the main APK or a full split APK file containing resources for a
   * particular split dimension.
   */
  enum class OutputType {
    MAIN, FULL_SPLIT
  }

  /** Split dimension type  */
  enum class FilterType {
    DENSITY, ABI, LANGUAGE
  }

  /** Returns all the split information used to create the APK.  */
  val filters: Collection<IdeFilterData?>

  /**
   * Returns the version code for this output.
   *
   *
   * This is convenient method that returns the final version code whether it's coming from the
   * override set in the output or from the variant's merged flavor.
   *
   * @return the version code.
   */
  val versionCode: Int

  /**
   * Returns the output file for this artifact's output.
   * Depending on whether the project is an app or a library project, this could be an apk or
   * an aar file. If this output file has filters, this is a split
   * APK.
   *
   * For test artifact for a library project, this would also be an apk.
   *
   * @return the output file.
   */
  val outputFile: File
}
