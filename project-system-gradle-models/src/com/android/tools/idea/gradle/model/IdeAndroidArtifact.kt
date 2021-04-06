/*
 * Copyright (C) 2017 The Android Open Source Project
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
import java.io.Serializable

interface IdeAndroidArtifact : Serializable, IdeBaseArtifact {
  @Deprecated("Deprecated as of 4.2. Application ID is now only provided post-build")
  val applicationId: String

  /**
   * Returns the name of the [IdeSigningConfig] used for the signing. If none are setup or if
   * this is not the main artifact of an application project, then this is null.
   */
  val signingConfigName: String?

  @Deprecated("Use post-build model instead. See VariantBuildInformation.")
  val outputs: List<IdeAndroidArtifactOutput>

  /**
   * Returns whether the output file is signed. This can only be true for the main apk of an
   * application project.
   */
  val isSigned: Boolean

  val generatedResourceFolders: Collection<File>

  /**
   * Map of generated res values where the key is the res name. This method is deprecated and will
   * always return an empty map
   */
  @Deprecated("Returns empty map")
  val resValues: Map<String, IdeClassField>

  /**
   * Returns a list of additional APKs that need to installed on the device for this artifact to
   * work correctly.
   *
   *
   * For test artifacts, these will be "buddy APKs" from the `androidTestUtil`
   * configuration.
   */
  val additionalRuntimeApks: List<File>

  /**
   * Returns the test options only if the variant type is testing.
   */
  val testOptions: IdeTestOptions?

  val abiFilters: Set<String>

  val buildInformation: IdeBuildTasksAndOutputInformation
  /**
   * Returns the code shrinker used by this artifact or null if no shrinker is used to build this
   * artifact.
   */
  val codeShrinker: CodeShrinker?
}
