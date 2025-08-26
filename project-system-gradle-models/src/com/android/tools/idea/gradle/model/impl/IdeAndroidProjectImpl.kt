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
  override val testSuites: List<IdeTestSuiteImpl>
) : IdeAndroidProject, Serializable {
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
    testSuites
  )
}