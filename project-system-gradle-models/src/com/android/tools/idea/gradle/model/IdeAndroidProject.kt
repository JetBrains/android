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

interface IdeAndroidProject : Serializable {
  /**
   * Returns the AGP version. This is a string in the format X.Y.Z
   */
  val agpVersion: String

  /**
   * Returns a project path together with the name and location of the build containing it.
   */
  val projectPath: IdeProjectPath

  /**
   * Returns the type of project: Android application, library, feature, instantApp.
   */
  val projectType: IdeAndroidProjectType

  val defaultSourceProvider: IdeSourceProviderContainer

  val multiVariantData: IdeMultiVariantData?

  /**
   * Returns a list of all the variant names.
   *
   *
   * This does not include test variant. Test variants are additional artifacts in their
   * respective variant info.
   *
   */
  val basicVariants: Collection<IdeBasicVariant>

  /**
   * Returns a list of all the flavor dimensions, may be empty.
   *
   * @return a list of the flavor dimensions.
   */
  val flavorDimensions: Collection<String>

  /**
   * Returns the compilation target as a string. This is the full extended target hash string.
   * (see com.android.sdklib.IAndroidTarget#hashString())
   *
   * @return the target hash string
   */
  val compileTarget: String

  /**
   * Returns the boot classpath matching the compile target. This is typically android.jar plus
   * other optional libraries.
   *
   * @return a list of jar files.
   */
  val bootClasspath: Collection<String>

  /** Returns a list of [IdeSigningConfig].  */
  val signingConfigs: Collection<IdeSigningConfig>

  /** Returns the aapt options.  */
  val aaptOptions: IdeAaptOptions

  /** Returns the lint options.  */
  val lintOptions: IdeLintOptions

  /** Returns the compile options for Java code.  */
  val javaCompileOptions: IdeJavaCompileOptions

  /** Returns the build folder of this project.  */
  val buildFolder: File

  /**
   * Returns the resource prefix to use, if any. This is an optional prefix which can be set and
   * which is used by the defaults to automatically choose new resources with a certain prefix,
   * warn if resources are not using the given prefix, etc. This helps work with resources in the
   * app namespace where there could otherwise be unintentional duplicated resource names between
   * unrelated libraries.
   *
   * @return the optional resource prefix, or null if not set
   */
  val resourcePrefix: String?

  /**
   * Returns the build tools version used by this module.
   *
   * @return the build tools version.
   */
  val buildToolsVersion: String?

  /**
   * Returns true if this is the base feature split.
   */
  val isBaseSplit: Boolean

  /**
   * Returns the list of dynamic features.
   *
   */
  val dynamicFeatures: Collection<String>

  /**
   * The Gradle path of the base feature (if a dynamic feature).
   */
  val baseFeature: String?

  val viewBindingOptions: IdeViewBindingOptions?
  val dependenciesInfo: IdeDependenciesInfo?

  /**
   * Returns the optional group-id of the artifact represented by this project.
   *
   * @since 3.6
   */
  val groupId: String?

  /**
   * Returns the namespace of the main artifact.
   *
   * When this is not null, it overrides the value from the Android manifest.
   */
  val namespace: String?

  /** Various flags from AGP  */
  val agpFlags: IdeAndroidGradlePluginProjectFlags

  /**
   * Returns the minimal information of variants for this project, excluding test related
   * variants.
   *
   * @since 4.1
   */
  val variantsBuildInformation: Collection<IdeVariantBuildInformation>

  /**
   * Returns the lint jars that this module uses to run extra lint checks.
   *
   *
   * If null, the model does not contain the information because AGP was an older version, and
   * alternative ways to get the information should be used.
   */
  val lintChecksJars: List<File>?

  /**
   * Returns the testNamespace of the main artifact.
   */
  val testNamespace: String?

  val isKaptEnabled: Boolean

  val desugarLibraryConfigFiles: List<File>
}

val IdeAndroidProject.variantNames: Collection<String> get() = basicVariants.map { it.name }
