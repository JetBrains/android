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

import com.android.tools.idea.gradle.model.IdeAndroidProject
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import java.io.File
import java.io.Serializable

data class IdeAndroidProjectImpl(
  override val agpVersion: String,
  override val projectPath: IdeProjectPathImpl,
  override val projectType: IdeAndroidProjectType,
  override val defaultSourceProvider: IdeSourceProviderContainerImpl,
  override val multiVariantData: IdeMultiVariantDataImpl?,
  override val basicVariants: List<IdeBasicVariantImpl>,
  override val coreVariants: List<IdeVariantCoreSerializable>,
  override val flavorDimensions: List<String>,
  override val compileTarget: String,
  override val bootClasspath: List<String>,
  override val signingConfigs: List<IdeSigningConfigImpl>,
  override val aaptOptions: IdeAaptOptionsImpl,
  override val lintOptions: IdeLintOptionsImpl?,
  override val javaCompileOptions: IdeJavaCompileOptionsImpl?,
  override val buildFolder: FileImpl,
  override val resourcePrefix: String?,
  override val buildToolsVersion: String?,
  override val isBaseSplit: Boolean,
  override val dynamicFeatures: List<String>,
  override val baseFeature: String?,
  override val viewBindingOptions: IdeViewBindingOptionsImpl?,
  override val dependenciesInfo: IdeDependenciesInfoImpl?,
  override val groupId: String?,
  override val namespace: String?,
  override val agpFlags: IdeAndroidGradlePluginProjectFlagsImpl,
  override val variantsBuildInformation: List<IdeVariantBuildInformationImpl>,
  override val lintChecksJars: List<FileImpl>?,
  override val testNamespace: String?,
  override val isKaptEnabled: Boolean,
  override val desugarLibraryConfigFiles: List<FileImpl>,
  override val defaultVariantName: String?,
  override val lintJar: FileImpl?,
  override val testSuites: List<IdeTestSuiteImpl>,
  private val hashCode: Int = computeHashCode(
    agpVersion,
    projectPath,
    projectType,
    defaultSourceProvider,
    multiVariantData,
    basicVariants,
    coreVariants,
    flavorDimensions,
    compileTarget,
    bootClasspath,
    signingConfigs,
    aaptOptions,
    lintOptions,
    javaCompileOptions,
    buildFolder,
    resourcePrefix,
    buildToolsVersion,
    isBaseSplit,
    dynamicFeatures,
    baseFeature,
    viewBindingOptions,
    dependenciesInfo,
    groupId,
    namespace,
    agpFlags,
    variantsBuildInformation,
    lintChecksJars,
    testNamespace,
    isKaptEnabled,
    desugarLibraryConfigFiles,
    defaultVariantName,
    lintJar,
    testSuites,
  )) : IdeAndroidProject, Serializable {
  constructor(
    agpVersion: String,
    projectPath: IdeProjectPathImpl,
    projectType: IdeAndroidProjectType,
    defaultSourceProvider: IdeSourceProviderContainerImpl,
    multiVariantData: IdeMultiVariantDataImpl?,
    basicVariants: List<IdeBasicVariantImpl>,
    coreVariants: List<IdeVariantCoreSerializable>,
    flavorDimensions: List<String>,
    compileTarget: String,
    bootClasspath: List<String>,
    signingConfigs: List<IdeSigningConfigImpl>,
    aaptOptions: IdeAaptOptionsImpl,
    lintOptions: IdeLintOptionsImpl?,
    javaCompileOptions: IdeJavaCompileOptionsImpl?,
    buildFolder: File,
    resourcePrefix: String?,
    buildToolsVersion: String?,
    isBaseSplit: Boolean,
    dynamicFeatures: List<String>,
    baseFeature: String?,
    viewBindingOptions: IdeViewBindingOptionsImpl?,
    dependenciesInfo: IdeDependenciesInfoImpl?,
    groupId: String?,
    namespace: String?,
    agpFlags: IdeAndroidGradlePluginProjectFlagsImpl,
    variantsBuildInformation: List<IdeVariantBuildInformationImpl>,
    lintChecksJars: List<File>?,
    testNamespace: String?,
    isKaptEnabled: Boolean,
    desugarLibraryConfigFiles: List<File>,
    defaultVariantName: String?,
    lintJar: File?,
    testSuites: List<IdeTestSuiteImpl>,
    unused: String = ""
  ) : this(
    agpVersion,
    projectPath,
    projectType,
    defaultSourceProvider,
    multiVariantData,
    basicVariants,
    coreVariants,
    flavorDimensions,
    compileTarget,
    bootClasspath,
    signingConfigs,
    aaptOptions,
    lintOptions,
    javaCompileOptions,
    buildFolder.toImpl(),
    resourcePrefix,
    buildToolsVersion,
    isBaseSplit,
    dynamicFeatures,
    baseFeature,
    viewBindingOptions,
    dependenciesInfo,
    groupId,
    namespace,
    agpFlags,
    variantsBuildInformation,
    lintChecksJars?.toImpl(),
    testNamespace,
    isKaptEnabled,
    desugarLibraryConfigFiles.toImpl(),
    defaultVariantName,
    lintJar?.toImpl(),
    testSuites)

  override fun hashCode() = hashCode

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as IdeAndroidProjectImpl

    if (hashCode() != other.hashCode()) return false
    if (isBaseSplit != other.isBaseSplit) return false
    if (isKaptEnabled != other.isKaptEnabled) return false
    if (agpVersion != other.agpVersion) return false
    if (projectPath != other.projectPath) return false
    if (projectType != other.projectType) return false
    if (defaultSourceProvider != other.defaultSourceProvider) return false
    if (multiVariantData != other.multiVariantData) return false
    if (basicVariants != other.basicVariants) return false
    if (coreVariants != other.coreVariants) return false
    if (flavorDimensions != other.flavorDimensions) return false
    if (compileTarget != other.compileTarget) return false
    if (bootClasspath != other.bootClasspath) return false
    if (signingConfigs != other.signingConfigs) return false
    if (aaptOptions != other.aaptOptions) return false
    if (lintOptions != other.lintOptions) return false
    if (javaCompileOptions != other.javaCompileOptions) return false
    if (buildFolder != other.buildFolder) return false
    if (resourcePrefix != other.resourcePrefix) return false
    if (buildToolsVersion != other.buildToolsVersion) return false
    if (dynamicFeatures != other.dynamicFeatures) return false
    if (baseFeature != other.baseFeature) return false
    if (viewBindingOptions != other.viewBindingOptions) return false
    if (dependenciesInfo != other.dependenciesInfo) return false
    if (groupId != other.groupId) return false
    if (namespace != other.namespace) return false
    if (agpFlags != other.agpFlags) return false
    if (variantsBuildInformation != other.variantsBuildInformation) return false
    if (lintChecksJars != other.lintChecksJars) return false
    if (testNamespace != other.testNamespace) return false
    if (desugarLibraryConfigFiles != other.desugarLibraryConfigFiles) return false
    if (defaultVariantName != other.defaultVariantName) return false
    if (lintJar != other.lintJar) return false
    if (testSuites != other.testSuites) return false

    return true
  }

  @Suppress("unused") // Used by equality unit tests
  private fun computeHashCode() = computeHashCode(
    agpVersion,
    projectPath,
    projectType,
    defaultSourceProvider,
    multiVariantData,
    basicVariants,
    coreVariants,
    flavorDimensions,
    compileTarget,
    bootClasspath,
    signingConfigs,
    aaptOptions,
    lintOptions,
    javaCompileOptions,
    buildFolder,
    resourcePrefix,
    buildToolsVersion,
    isBaseSplit,
    dynamicFeatures,
    baseFeature,
    viewBindingOptions,
    dependenciesInfo,
    groupId,
    namespace,
    agpFlags,
    variantsBuildInformation,
    lintChecksJars,
    testNamespace,
    isKaptEnabled,
    desugarLibraryConfigFiles,
    defaultVariantName,
    lintJar,
    testSuites
  )

  companion object {
    private fun computeHashCode(agpVersion: String,
                                projectPath: IdeProjectPathImpl,
                                projectType: IdeAndroidProjectType,
                                defaultSourceProvider: IdeSourceProviderContainerImpl,
                                multiVariantData: IdeMultiVariantDataImpl?,
                                basicVariants: List<IdeBasicVariantImpl>,
                                coreVariants: List<IdeVariantCoreSerializable>,
                                flavorDimensions: List<String>,
                                compileTarget: String,
                                bootClasspath: List<String>,
                                signingConfigs: List<IdeSigningConfigImpl>,
                                aaptOptions: IdeAaptOptionsImpl,
                                lintOptions: IdeLintOptionsImpl?,
                                javaCompileOptions: IdeJavaCompileOptionsImpl?,
                                buildFolder: FileImpl,
                                resourcePrefix: String?,
                                buildToolsVersion: String?,
                                isBaseSplit: Boolean,
                                dynamicFeatures: List<String>,
                                baseFeature: String?,
                                viewBindingOptions: IdeViewBindingOptionsImpl?,
                                dependenciesInfo: IdeDependenciesInfoImpl?,
                                groupId: String?,
                                namespace: String?,
                                agpFlags: IdeAndroidGradlePluginProjectFlagsImpl,
                                variantsBuildInformation: List<IdeVariantBuildInformationImpl>,
                                lintChecksJars: List<FileImpl>?,
                                testNamespace: String?,
                                isKaptEnabled: Boolean,
                                desugarLibraryConfigFiles: List<FileImpl>,
                                defaultVariantName: String?,
                                lintJar: FileImpl?,
                                testSuites: List<IdeTestSuiteImpl>) : Int{
      var result = isBaseSplit.hashCode()
      result = 31 * result + isKaptEnabled.hashCode()
      result = 31 * result + agpVersion.hashCode()
      result = 31 * result + projectPath.hashCode()
      result = 31 * result + projectType.hashCode()
      result = 31 * result + defaultSourceProvider.hashCode()
      result = 31 * result + (multiVariantData?.hashCode() ?: 0)
      result = 31 * result + basicVariants.hashCode()
      result = 31 * result + coreVariants.hashCode()
      result = 31 * result + flavorDimensions.hashCode()
      result = 31 * result + compileTarget.hashCode()
      result = 31 * result + bootClasspath.hashCode()
      result = 31 * result + signingConfigs.hashCode()
      result = 31 * result + aaptOptions.hashCode()
      result = 31 * result + (lintOptions?.hashCode() ?: 0)
      result = 31 * result + (javaCompileOptions?.hashCode() ?: 0)
      result = 31 * result + buildFolder.hashCode()
      result = 31 * result + (resourcePrefix?.hashCode() ?: 0)
      result = 31 * result + (buildToolsVersion?.hashCode() ?: 0)
      result = 31 * result + dynamicFeatures.hashCode()
      result = 31 * result + (baseFeature?.hashCode() ?: 0)
      result = 31 * result + (viewBindingOptions?.hashCode() ?: 0)
      result = 31 * result + (dependenciesInfo?.hashCode() ?: 0)
      result = 31 * result + (groupId?.hashCode() ?: 0)
      result = 31 * result + (namespace?.hashCode() ?: 0)
      result = 31 * result + agpFlags.hashCode()
      result = 31 * result + variantsBuildInformation.hashCode()
      result = 31 * result + (lintChecksJars?.hashCode() ?: 0)
      result = 31 * result + (testNamespace?.hashCode() ?: 0)
      result = 31 * result + desugarLibraryConfigFiles.hashCode()
      result = 31 * result + (defaultVariantName?.hashCode() ?: 0)
      result = 31 * result + (lintJar?.hashCode() ?: 0)
      result = 31 * result + testSuites.hashCode()
      return result
    }
  }
}