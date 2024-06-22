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

interface IdeBasicVariant {
  val name: String

  /**
   * See [IdeAndroidArtifactCore.applicationId] for details.
   */
  val applicationId: String?

  /**
   * See [IdeAndroidArtifactCore.applicationId] for details.
   */
  val testApplicationId: String?

  /**
   * The name of the build type that contributed to this variant.
   *
   * Null if there are no build types, such as for the single-variant KMP, or if there's something broken about the project structure.
   */
  val buildType: String?

  /**
   * The Studio UI hide all references to those variants for the users, keeping them for internal usage purposes
   */
  val hideInStudio: Boolean
}

interface IdeVariantHeader {
  val name: String
  val buildType: String
  val productFlavors: List<String>
  val displayName: String
}

interface IdeVariantCore : IdeVariantHeader {
  val mainArtifact: IdeAndroidArtifactCore
  val deviceTestArtifacts: List<IdeAndroidArtifactCore>
  val testFixturesArtifact: IdeAndroidArtifactCore?
  val hostTestArtifacts: List<IdeJavaArtifactCore>

  val minSdkVersion: IdeApiVersion

  val targetSdkVersion: IdeApiVersion?

  val maxSdkVersion: Int?

  val versionCode: Int?

  val versionNameSuffix: String?

  val versionNameWithSuffix: String?

  val instantAppCompatible: Boolean

  val vectorDrawablesUseSupportLibrary: Boolean

  /**
   * The resource configuration for this variant.
   *
   * This is the list of -c parameters for aapt.
   */
  val resourceConfigurations: Collection<String>

  /**
   * Map of generated res values where the key is the res name.
   */
  val resValues: Map<String, IdeClassField>

  /**
   * Specifies the ProGuard configuration files that the plugin should use.
   */
  val proguardFiles: Collection<File>

  /** The collection of proguard rule files for consumers of the library to use. */
  val consumerProguardFiles: Collection<File>

  /**
   * The map of key value pairs for placeholder substitution in the android manifest file.
   *
   * This map will be used by the manifest merger.
   */
  val manifestPlaceholders: Map<String, String>

  /**
   * The test instrumentation runner. This is only the value set on this product flavor.
   * TODO: make test instrumentation runner available through the model.
   */
  val testInstrumentationRunner: String?

  /** The arguments for the test instrumentation runner.*/
  val testInstrumentationRunnerArguments: Map<String, String>

  val testedTargetVariants: List<IdeTestedTargetVariant>
  /** True when "android.experimental.self-instrumenting" experimental flag is enabled. */
  val runTestInSeparateProcess: Boolean

  // TODO(b/178961768); Review usages and replace with the correct alternatives or rename.
  val deprecatedPreMergedApplicationId: String?
  val deprecatedPreMergedTestApplicationId: String?

  /**
   * Files listing any D8 backported desugared methods or core library desugared methods.
   */
  val desugaredMethodsFiles: Collection<File>

  /**
   * The experimental flags set on the variant.
   */
  val experimentalProperties: Map<String, String>
}

interface IdeVariant : IdeVariantCore {
  override val mainArtifact: IdeAndroidArtifact
  override val deviceTestArtifacts: List<IdeAndroidArtifact>
  override val testFixturesArtifact: IdeAndroidArtifact?
  override val hostTestArtifacts: List<IdeJavaArtifact>
}