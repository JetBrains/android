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
import com.android.tools.idea.gradle.model.IdeLibraryModelResolver
import com.android.tools.idea.gradle.model.IdeVariant
import com.android.tools.idea.gradle.model.IdeVariantCore
import java.io.File
import java.io.Serializable

data class IdeBasicVariantImpl(
  override val name: String,
  override val applicationId: String?,
  override val testApplicationId: String?,
  override val buildType: String?,
) : IdeBasicVariant, Serializable

data class IdeVariantCoreImpl(
  override val name: String,
  override val displayName: String,
  override val mainArtifact: IdeAndroidArtifactCoreImpl,
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
  override val resourceConfigurations: Collection<String>,
  override val resValues: Map<String, IdeClassFieldImpl>,
  override val proguardFiles: Collection<File>,
  override val consumerProguardFiles: Collection<File>,
  override val manifestPlaceholders: Map<String, String>,
  override val testInstrumentationRunner: String?,
  override val testInstrumentationRunnerArguments: Map<String, String>,
  override val testedTargetVariants: List<IdeTestedTargetVariantImpl>,
  override val runTestInSeparateProcess: Boolean,
  // TODO(b/178961768); Review usages and replace with the correct alternatives or rename.
  override val deprecatedPreMergedApplicationId: String?,
  override val deprecatedPreMergedTestApplicationId: String?,
  override val desugaredMethodsFiles: Collection<File>,
  override val experimentalProperties: Map<String, String>
) : IdeVariantCore, Serializable

data class IdeVariantImpl(
  private val core: IdeVariantCore,
  private val resolver: IdeLibraryModelResolver
) : IdeVariant, IdeVariantCore by core {
  override val mainArtifact: IdeAndroidArtifact = IdeAndroidArtifactImpl(core.mainArtifact, resolver)
  override val deviceTestArtifacts: List<IdeAndroidArtifact> = core.deviceTestArtifacts.map { IdeAndroidArtifactImpl(it, resolver) }
  override val testFixturesArtifact: IdeAndroidArtifact? = core.testFixturesArtifact?.let { IdeAndroidArtifactImpl(it, resolver) }
  override val hostTestArtifacts: List<IdeJavaArtifact> = core.hostTestArtifacts.map { IdeJavaArtifactImpl(it, resolver) }
}