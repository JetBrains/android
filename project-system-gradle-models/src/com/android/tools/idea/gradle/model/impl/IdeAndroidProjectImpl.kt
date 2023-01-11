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
  override val defaultConfig: IdeProductFlavorContainerImpl,
  override val buildTypes: Collection<IdeBuildTypeContainerImpl>,
  override val productFlavors: Collection<IdeProductFlavorContainerImpl>,
  override val basicVariants: Collection<IdeBasicVariantImpl>,
  override val flavorDimensions: Collection<String>,
  override val compileTarget: String,
  override val bootClasspath: Collection<String>,
  override val signingConfigs: Collection<IdeSigningConfigImpl>,
  override val aaptOptions: IdeAaptOptionsImpl,
  override val lintOptions: IdeLintOptionsImpl,
  override val javaCompileOptions: IdeJavaCompileOptionsImpl,
  override val buildFolder: File,
  override val resourcePrefix: String?,
  override val buildToolsVersion: String?,
  override val isBaseSplit: Boolean,
  override val dynamicFeatures: Collection<String>,
  override val baseFeature: String?,
  override val viewBindingOptions: IdeViewBindingOptionsImpl?,
  override val dependenciesInfo: IdeDependenciesInfoImpl?,
  override val groupId: String?,
  override val namespace: String?,
  override val agpFlags: IdeAndroidGradlePluginProjectFlagsImpl,
  override val variantsBuildInformation: Collection<IdeVariantBuildInformationImpl>,
  override val lintChecksJars: List<File>?,
  override val testNamespace: String?,
  override val isKaptEnabled: Boolean,
  override val desugarLibraryConfigFiles: List<File>,
) : IdeAndroidProject, Serializable