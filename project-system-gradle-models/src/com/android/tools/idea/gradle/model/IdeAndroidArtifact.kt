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

interface IdeAndroidArtifactCore : Serializable, IdeBaseArtifactCore {

  /**
   * The application ID of this artifact.
   *
   * Known for:
   *  - Application plugin main artifacts
   *  - AndroidTest components of all project types
   *  - Test-only plugin main artifacts
   *
   *  Not included (null) for:
   *   - Library plugin main artifacts, as no APK is produced
   *   - UnitTest components, also as no APK is produced
   *   - Dynamic feature plugin main artifacts, as the application ID comes from the base
   *     application, and is therefore not available in dynamic feature projects during
   *     configuration. In this case Android Studio must look at the dependency graph to find the
   *     base application to find this value.
   */
  val applicationId: String?

  /**
   * Returns the name of the [IdeSigningConfig] used for the signing. If none are setup or if
   * this is not the main artifact of an application project, then this is null.
   */
  val signingConfigName: String?

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

  /**
   * Details about privacy sandbox SDK consumption.
   *
   * Null if the feature is not enabled, but may be present even if there are
   * no privacy sandbox SDKs that need to be deployed.
   */
  val privacySandboxSdkInfo: IdePrivacySandboxSdkInfo?

  /**
   * Returns all the [IdeModelSyncFile] for this artifact.
   *
   * @return a list of [IdeModelSyncFile]
   * @since 7.2
   */
  val modelSyncFiles: Collection<IdeModelSyncFile>
  val compileClasspathCore: IdeDependenciesCore
  val runtimeClasspathCore: IdeDependenciesCore

  /**
   * Files listing any D8 backported desugared methods or core library desugared methods for this artifact
   */
  val desugaredMethodsFiles: Collection<File>
}

interface IdeAndroidArtifact : IdeAndroidArtifactCore, IdeBaseArtifact