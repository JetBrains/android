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

import com.android.tools.idea.gradle.model.IdeBasicVariant
import com.android.tools.idea.gradle.model.IdeVariant
import com.android.tools.idea.gradle.model.IdeVariantCore
import java.io.File
import java.io.Serializable

sealed interface IdeVariantCoreSerializable : IdeVariantCore, Serializable

data object ThrowingIdeVariantCore : IdeVariantCoreSerializable {
  override val mainArtifact: IdeAndroidArtifactCoreImpl
    get() {
      error("Should not be called")
    }

  override val deviceTestArtifacts: List<IdeAndroidArtifactCoreImpl>
    get() {
      error("Should not be called")
    }

  override val testFixturesArtifact: IdeAndroidArtifactCoreImpl?
    get() {
      error("Should not be called")
    }

  override val hostTestArtifacts: List<IdeJavaArtifactCoreImpl>
    get() {
      error("Should not be called")
    }

  override val testSuiteArtifacts: List<IdeTestSuiteVariantTargetImpl>
    get() {
      error("Should not be called")
    }

  override val minSdkVersion: IdeApiVersionImpl
    get() {
      error("Should not be called")
    }

  override val targetSdkVersion: IdeApiVersionImpl?
    get() {
      error("Should not be called")
    }

  override val maxSdkVersion: Int?
    get() {
      error("Should not be called")
    }

  override val versionCode: Int?
    get() {
      error("Should not be called")
    }

  override val versionNameSuffix: String?
    get() {
      error("Should not be called")
    }

  override val versionNameWithSuffix: String?
    get() {
      error("Should not be called")
    }

  override val instantAppCompatible: Boolean
    get() {
      error("Should not be called")
    }

  override val vectorDrawablesUseSupportLibrary: Boolean
    get() {
      error("Should not be called")
    }

  override val resourceConfigurations: List<String>
    get() {
      error("Should not be called")
    }

  override val resValues: Map<String, IdeClassFieldImpl>
    get() {
      error("Should not be called")
    }

  override val proguardFiles: List<FileImpl>
    get() {
      error("Should not be called")
    }

  override val consumerProguardFiles: List<FileImpl>
    get() {
      error("Should not be called")
    }

  override val manifestPlaceholders: Map<String, String>
    get() {
      error("Should not be called")
    }

  override val testInstrumentationRunner: String?
    get() {
      error("Should not be called")
    }

  override val testInstrumentationRunnerArguments: Map<String, String>
    get() {
      error("Should not be called")
    }

  override val testedTargetVariants: List<IdeTestedTargetVariantImpl>
    get() {
      error("Should not be called")
    }

  override val runTestInSeparateProcess: Boolean
    get() {
      error("Should not be called")
    }

  override val deprecatedPreMergedApplicationId: String?
    get() {
      error("Should not be called")
    }

  override val deprecatedPreMergedTestApplicationId: String?
    get() {
      error("Should not be called")
    }

  override val desugaredMethodsFiles: List<FileImpl>
    get() {
      error("Should not be called")
    }

  override val experimentalProperties: Map<String, String>
    get() {
      error("Should not be called")
    }

  override val name: String
    get() {
      error("Should not be called")
    }

  override val buildType: String
    get() {
      error("Should not be called")
    }

  override val productFlavors: List<String>
    get() {
      error("Should not be called")
    }

  override val displayName: String
    get() {
      error("Should not be called")
    }

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
  override val experimentalProperties: Map<String, String>,
  private val hashCode: Int =
    computeHashCode(
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
      proguardFiles,
      consumerProguardFiles,
      manifestPlaceholders,
      testInstrumentationRunner,
      testInstrumentationRunnerArguments,
      testedTargetVariants,
      runTestInSeparateProcess,
      deprecatedPreMergedApplicationId,
      deprecatedPreMergedTestApplicationId,
      desugaredMethodsFiles,
      experimentalProperties,
    ),
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
    unused: String = "", // to prevent clash
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
    experimentalProperties,
  )

  override fun hashCode() = hashCode

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as IdeVariantCoreImpl

    if (hashCode() != other.hashCode()) return false
    if (name != other.name) return false
    if (displayName != other.displayName) return false
    if (mainArtifact != other.mainArtifact) return false
    if (testSuiteArtifacts != other.testSuiteArtifacts) return false
    if (hostTestArtifacts != other.hostTestArtifacts) return false
    if (deviceTestArtifacts != other.deviceTestArtifacts) return false
    if (testFixturesArtifact != other.testFixturesArtifact) return false
    if (buildType != other.buildType) return false
    if (productFlavors != other.productFlavors) return false
    if (minSdkVersion != other.minSdkVersion) return false
    if (targetSdkVersion != other.targetSdkVersion) return false
    if (maxSdkVersion != other.maxSdkVersion) return false
    if (versionCode != other.versionCode) return false
    if (versionNameWithSuffix != other.versionNameWithSuffix) return false
    if (versionNameSuffix != other.versionNameSuffix) return false
    if (instantAppCompatible != other.instantAppCompatible) return false
    if (vectorDrawablesUseSupportLibrary != other.vectorDrawablesUseSupportLibrary) return false
    if (resourceConfigurations != other.resourceConfigurations) return false
    if (resValues != other.resValues) return false
    if (proguardFiles != other.proguardFiles) return false
    if (consumerProguardFiles != other.consumerProguardFiles) return false
    if (manifestPlaceholders != other.manifestPlaceholders) return false
    if (testInstrumentationRunner != other.testInstrumentationRunner) return false
    if (testInstrumentationRunnerArguments != other.testInstrumentationRunnerArguments) return false
    if (testedTargetVariants != other.testedTargetVariants) return false
    if (runTestInSeparateProcess != other.runTestInSeparateProcess) return false
    if (deprecatedPreMergedApplicationId != other.deprecatedPreMergedApplicationId) return false
    if (deprecatedPreMergedTestApplicationId != other.deprecatedPreMergedTestApplicationId) return false
    if (desugaredMethodsFiles != other.desugaredMethodsFiles) return false
    if (experimentalProperties != other.experimentalProperties) return false
    return true
  }

  @Suppress("unused") // Used by equality unit tests
  private fun computeHashCode() =
    computeHashCode(
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
      proguardFiles,
      consumerProguardFiles,
      manifestPlaceholders,
      testInstrumentationRunner,
      testInstrumentationRunnerArguments,
      testedTargetVariants,
      runTestInSeparateProcess,
      deprecatedPreMergedApplicationId,
      deprecatedPreMergedTestApplicationId,
      desugaredMethodsFiles,
      experimentalProperties,
    )

  companion object {
    private fun computeHashCode(
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
      proguardFiles: List<FileImpl>,
      consumerProguardFiles: List<FileImpl>,
      manifestPlaceholders: Map<String, String>,
      testInstrumentationRunner: String?,
      testInstrumentationRunnerArguments: Map<String, String>,
      testedTargetVariants: List<IdeTestedTargetVariantImpl>,
      runTestInSeparateProcess: Boolean,
      deprecatedPreMergedApplicationId: String?,
      deprecatedPreMergedTestApplicationId: String?,
      desugaredMethodsFiles: List<FileImpl>,
      experimentalProperties: Map<String, String>,
    ): Int {
      var result = name.hashCode()
      result = 31 * result + displayName.hashCode()
      result = 31 * result + mainArtifact.hashCode()
      result = 31 * result + testSuiteArtifacts.hashCode()
      result = 31 * result + hostTestArtifacts.hashCode()
      result = 31 * result + deviceTestArtifacts.hashCode()
      result = 31 * result + testFixturesArtifact.hashCode()
      result = 31 * result + buildType.hashCode()
      result = 31 * result + productFlavors.hashCode()
      result = 31 * result + minSdkVersion.hashCode()
      result = 31 * result + targetSdkVersion.hashCode()
      result = 31 * result + maxSdkVersion.hashCode()
      result = 31 * result + versionCode.hashCode()
      result = 31 * result + versionNameWithSuffix.hashCode()
      result = 31 * result + versionNameSuffix.hashCode()
      result = 31 * result + instantAppCompatible.hashCode()
      result = 31 * result + vectorDrawablesUseSupportLibrary.hashCode()
      result = 31 * result + resourceConfigurations.hashCode()
      result = 31 * result + resValues.hashCode()
      result = 31 * result + proguardFiles.hashCode()
      result = 31 * result + consumerProguardFiles.hashCode()
      result = 31 * result + manifestPlaceholders.hashCode()
      result = 31 * result + testInstrumentationRunner.hashCode()
      result = 31 * result + testInstrumentationRunnerArguments.hashCode()
      result = 31 * result + testedTargetVariants.hashCode()
      result = 31 * result + runTestInSeparateProcess.hashCode()
      result = 31 * result + deprecatedPreMergedApplicationId.hashCode()
      result = 31 * result + deprecatedPreMergedTestApplicationId.hashCode()
      result = 31 * result + desugaredMethodsFiles.hashCode()
      result = 31 * result + experimentalProperties.hashCode()
      return result
    }
  }
}

class IdeVariantImpl(val core: IdeVariantCoreImpl, resolver: IdeLibraryModelResolverImpl) : IdeVariant, IdeVariantCore {
  override val minSdkVersion: IdeApiVersionImpl = core.minSdkVersion
  override val targetSdkVersion: IdeApiVersionImpl? = core.targetSdkVersion
  override val maxSdkVersion: Int? = core.maxSdkVersion
  override val versionCode: Int? = core.versionCode
  override val versionNameSuffix: String? = core.versionNameSuffix
  override val versionNameWithSuffix: String? = core.versionNameWithSuffix
  override val instantAppCompatible: Boolean = core.instantAppCompatible
  override val vectorDrawablesUseSupportLibrary: Boolean = core.vectorDrawablesUseSupportLibrary
  override val resourceConfigurations: List<String> = core.resourceConfigurations
  override val resValues: Map<String, IdeClassFieldImpl> = core.resValues
  override val proguardFiles: List<FileImpl> = core.proguardFiles
  override val consumerProguardFiles: List<FileImpl> = core.consumerProguardFiles
  override val manifestPlaceholders: Map<String, String> = core.manifestPlaceholders
  override val testInstrumentationRunner: String? = core.testInstrumentationRunner
  override val testInstrumentationRunnerArguments: Map<String, String> = core.testInstrumentationRunnerArguments
  override val testedTargetVariants: List<IdeTestedTargetVariantImpl> = core.testedTargetVariants
  override val runTestInSeparateProcess: Boolean = core.runTestInSeparateProcess
  override val deprecatedPreMergedApplicationId: String? = core.deprecatedPreMergedApplicationId
  override val deprecatedPreMergedTestApplicationId: String? = core.deprecatedPreMergedTestApplicationId
  override val desugaredMethodsFiles: List<FileImpl> = core.desugaredMethodsFiles
  override val experimentalProperties: Map<String, String> = core.experimentalProperties
  override val name: String = core.name
  override val buildType: String = core.buildType
  override val productFlavors: List<String> = core.productFlavors
  override val displayName: String = core.displayName
  override val mainArtifact: IdeAndroidArtifactImpl = IdeAndroidArtifactImpl(core.mainArtifact, resolver)
  override val deviceTestArtifacts: List<IdeAndroidArtifactImpl> = core.deviceTestArtifacts.map { IdeAndroidArtifactImpl(it, resolver) }
  override val testFixturesArtifact: IdeAndroidArtifactImpl? = core.testFixturesArtifact?.let { IdeAndroidArtifactImpl(it, resolver) }
  override val hostTestArtifacts: List<IdeJavaArtifactImpl> = core.hostTestArtifacts.map { IdeJavaArtifactImpl(it, resolver) }
  override val testSuiteArtifacts: List<IdeTestSuiteVariantTargetImpl> =
    core.testSuiteArtifacts.map {
      IdeTestSuiteVariantTargetImpl(
        it.suiteName,
        it.targetedVariantName,
        it.targets.map { target -> IdeTestSuiteTargetImpl(target.targetName, target.testTaskName, target.targetedDevices) },
      )
    }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    return core == (other as IdeVariantImpl).core
  }

  override fun hashCode(): Int = core.hashCode()
}
