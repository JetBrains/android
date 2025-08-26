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
package com.android.tools.idea.gradle.model.impl

import com.android.tools.idea.gradle.model.IdeAndroidArtifact
import com.android.tools.idea.gradle.model.IdeBasicVariant
import com.android.tools.idea.gradle.model.IdeJavaArtifact
import com.android.tools.idea.gradle.model.IdeVariant
import com.android.tools.idea.gradle.model.IdeVariantCore
import java.io.File
import java.io.Serializable

sealed interface IdeVariantCoreSerializable: IdeVariantCore, Serializable

data object ThrowingIdeVariantCore : IdeVariantCoreSerializable {
  override val mainArtifact: IdeAndroidArtifactCoreImpl get() { error("Should not be called") }
  override val deviceTestArtifacts: List<IdeAndroidArtifactCoreImpl> get() { error("Should not be called") }
  override val testFixturesArtifact: IdeAndroidArtifactCoreImpl? get() { error("Should not be called") }
  override val hostTestArtifacts: List<IdeJavaArtifactCoreImpl> get() { error("Should not be called") }
  override val testSuiteArtifacts: List<IdeTestSuiteVariantTargetImpl> get() { error("Should not be called") }
  override val minSdkVersion: IdeApiVersionImpl get() { error("Should not be called") }
  override val targetSdkVersion: IdeApiVersionImpl? get() { error("Should not be called") }
  override val maxSdkVersion: Int? get() { error("Should not be called") }
  override val versionCode: Int? get() { error("Should not be called") }
  override val versionNameSuffix: String? get() { error("Should not be called") }
  override val versionNameWithSuffix: String? get() { error("Should not be called") }
  override val instantAppCompatible: Boolean get() { error("Should not be called") }
  override val vectorDrawablesUseSupportLibrary: Boolean get() { error("Should not be called") }
  override val resourceConfigurations: List<String> get() { error("Should not be called") }
  override val resValues: Map<String, IdeClassFieldImpl> get() { error("Should not be called") }
  override val proguardFiles: List<FileImpl> get() { error("Should not be called") }
  override val consumerProguardFiles: List<FileImpl> get() { error("Should not be called") }
  override val manifestPlaceholders: Map<String, String> get() { error("Should not be called") }
  override val testInstrumentationRunner: String? get() { error("Should not be called") }
  override val testInstrumentationRunnerArguments: Map<String, String> get() { error("Should not be called") }
  override val testedTargetVariants: List<IdeTestedTargetVariantImpl> get() { error("Should not be called") }
  override val runTestInSeparateProcess: Boolean get() { error("Should not be called") }
  override val deprecatedPreMergedApplicationId: String? get() { error("Should not be called") }
  override val deprecatedPreMergedTestApplicationId: String? get() { error("Should not be called") }
  override val desugaredMethodsFiles: List<FileImpl> get() { error("Should not be called") }
  override val experimentalProperties: Map<String, String> get() { error("Should not be called") }
  override val name: String get() { error("Should not be called") }
  override val buildType: String get() { error("Should not be called") }
  override val productFlavors: List<String> get() { error("Should not be called") }
  override val displayName: String get() { error("Should not be called") }

  // Make sure the serialization always returns this singleton
  private fun readResolve(): Any = ThrowingIdeVariantCore
}


data class IdeBasicVariantImpl(
  override val name: String,
  override val applicationId: String?,
  override val testApplicationId: String?,
  override val buildType: String?,
  override val hideInStudio: Boolean,
) : IdeBasicVariant, Serializable

data class IdeVariantCoreImpl(
  override val name: String,
  override val displayName: String,
  override val mainArtifact: IdeAndroidArtifactCoreImpl,
  override val testSuiteArtifacts: List<IdeTestSuiteVariantTargetImpl>,
  override val hostTestArtifacts: List<IdeJavaArtifactCoreImpl>,
  override val deviceTestArtifacts: List<IdeAndroidArtifactCoreImpl>,
  override val testFixturesArtifact: IdeAndroidArtifactCoreImpl?,
  override val buildType: String,
  override val productFlavors: List<String>,
  override val minSdkVersion: IdeApiVersionImpl,
  override val targetSdkVersion: IdeApiVersionImpl?,
  override val maxSdkVersion: Int?,
  override val versionCode: Int?,
  override val versionNameWithSuffix: String?,
  override val versionNameSuffix: String?,
  override val instantAppCompatible: Boolean,
  override val vectorDrawablesUseSupportLibrary: Boolean,
  override val resourceConfigurations: List<String>,
  override val resValues: Map<String, IdeClassFieldImpl>,
  override val proguardFiles: List<FileImpl>,
  override val consumerProguardFiles: List<FileImpl>,
  override val manifestPlaceholders: Map<String, String>,
  override val testInstrumentationRunner: String?,
  override val testInstrumentationRunnerArguments: Map<String, String>,
  override val testedTargetVariants: List<IdeTestedTargetVariantImpl>,
  override val runTestInSeparateProcess: Boolean,
  // TODO(b/178961768); Review usages and replace with the correct alternatives or rename.
  override val deprecatedPreMergedApplicationId: String?,
  override val deprecatedPreMergedTestApplicationId: String?,
  override val desugaredMethodsFiles: List<FileImpl>,
  override val experimentalProperties: Map<String, String>
) : IdeVariantCoreSerializable {
  constructor(
    name: String,
    displayName: String,
    mainArtifact: IdeAndroidArtifactCoreImpl,
    testSuiteArtifacts: List<IdeTestSuiteVariantTargetImpl>,
    hostTestArtifacts: List<IdeJavaArtifactCoreImpl>,
    deviceTestArtifacts: List<IdeAndroidArtifactCoreImpl>,
    testFixturesArtifact: IdeAndroidArtifactCoreImpl?,
    buildType: String,
    productFlavors: List<String>,
    minSdkVersion: IdeApiVersionImpl,
    targetSdkVersion: IdeApiVersionImpl?,
    maxSdkVersion: Int?,
    versionCode: Int?,
    versionNameWithSuffix: String?,
    versionNameSuffix: String?,
    instantAppCompatible: Boolean,
    vectorDrawablesUseSupportLibrary: Boolean,
    resourceConfigurations: List<String>,
    resValues: Map<String, IdeClassFieldImpl>,
    proguardFiles: List<File>,
    consumerProguardFiles: List<File>,
    manifestPlaceholders: Map<String, String>,
    testInstrumentationRunner: String?,
    testInstrumentationRunnerArguments: Map<String, String>,
    testedTargetVariants: List<IdeTestedTargetVariantImpl>,
    runTestInSeparateProcess: Boolean,
    deprecatedPreMergedApplicationId: String?,
    deprecatedPreMergedTestApplicationId: String?,
    desugaredMethodsFiles: List<File>,
    experimentalProperties: Map<String, String>,
    unused: String = "" // to prevent clash
  ) : this(
    name,
    displayName,
    mainArtifact,
    testSuiteArtifacts,
    hostTestArtifacts,
    deviceTestArtifacts,
    testFixturesArtifact,
    buildType,
    productFlavors,
    minSdkVersion,
    targetSdkVersion,
    maxSdkVersion,
    versionCode,
    versionNameWithSuffix,
    versionNameSuffix,
    instantAppCompatible,
    vectorDrawablesUseSupportLibrary,
    resourceConfigurations,
    resValues,
    proguardFiles.toImpl(),
    consumerProguardFiles.toImpl(),
    manifestPlaceholders,
    testInstrumentationRunner,
    testInstrumentationRunnerArguments,
    testedTargetVariants,
    runTestInSeparateProcess,
    deprecatedPreMergedApplicationId,
    deprecatedPreMergedTestApplicationId,
    desugaredMethodsFiles.toImpl(),
    experimentalProperties
  )
}

data class IdeVariantImpl(
  private val core: IdeVariantCoreImpl,
  private val resolver: IdeLibraryModelResolverImpl
) : IdeVariant, IdeVariantCore by core {
  override val mainArtifact: IdeAndroidArtifact = IdeAndroidArtifactImpl(core.mainArtifact, resolver)
  override val deviceTestArtifacts: List<IdeAndroidArtifact> = core.deviceTestArtifacts.map { IdeAndroidArtifactImpl(it, resolver) }
  override val testFixturesArtifact: IdeAndroidArtifact? = core.testFixturesArtifact?.let { IdeAndroidArtifactImpl(it, resolver) }
  override val hostTestArtifacts: List<IdeJavaArtifact> = core.hostTestArtifacts.map { IdeJavaArtifactImpl(it, resolver) }
  override val testSuiteArtifacts: List<IdeTestSuiteVariantTargetImpl> = core.testSuiteArtifacts.map { it as IdeTestSuiteVariantTargetImpl }
}