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

import com.android.tools.idea.gradle.model.CodeShrinker
import com.android.tools.idea.gradle.model.IdeAndroidArtifact
import com.android.tools.idea.gradle.model.IdeAndroidArtifactCore
import com.android.tools.idea.gradle.model.IdeArtifactName
import com.android.tools.idea.gradle.model.IdeDependencies
import com.android.tools.idea.gradle.model.IdeLibraryModelResolver
import java.io.File

data class IdeAndroidArtifactCoreImpl(
  override val name: IdeArtifactName,
  override val compileTaskName: String?,
  override val assembleTaskName: String?,
  override val classesFolder: List<FileImpl>,
  override val variantSourceProvider: IdeSourceProviderImpl?,
  override val multiFlavorSourceProvider: IdeSourceProviderImpl?,
  override val ideSetupTaskNames: List<String>,
  override val generatedSourceFolders: List<FileImpl>,
  override val isTestArtifact: Boolean,
  override val compileClasspathCore: IdeDependenciesCoreImpl,
  override val runtimeClasspathCore: IdeDependenciesCoreImpl,
  override val unresolvedDependencies: List<IdeUnresolvedDependencyImpl>,
  override val applicationId: String?,
  override val signingConfigName: String?,
  override val isSigned: Boolean,
  override val generatedResourceFolders: List<FileImpl>,
  override val additionalRuntimeApks: List<FileImpl>,
  override val testOptions: IdeTestOptionsImpl?,
  override val abiFilters: Set<String>,
  override val buildInformation: IdeBuildTasksAndOutputInformationImpl,
  override val codeShrinker: CodeShrinker?,
  override val privacySandboxSdkInfo: IdePrivacySandboxSdkInfoImpl?,
  override val desugaredMethodsFiles: List<FileImpl>,
  override val generatedClassPaths: Map<String, FileImpl>,
  override val bytecodeTransforms: List<IdeBytecodeTransformationImpl>?,
  override val generatedAssetFolders: List<FileImpl>,
) : IdeAndroidArtifactCore {
  constructor(
    name: IdeArtifactName,
    compileTaskName: String?,
    assembleTaskName: String?,
    classesFolder: List<File>,
    variantSourceProvider: IdeSourceProviderImpl?,
    multiFlavorSourceProvider: IdeSourceProviderImpl?,
    ideSetupTaskNames: List<String>,
    generatedSourceFolders: List<File>,
    isTestArtifact: Boolean,
    compileClasspathCore: IdeDependenciesCoreImpl,
    runtimeClasspathCore: IdeDependenciesCoreImpl,
    unresolvedDependencies: List<IdeUnresolvedDependencyImpl>,
    applicationId: String?,
    signingConfigName: String?,
    isSigned: Boolean,
    generatedResourceFolders: List<File>,
    additionalRuntimeApks: List<File>,
    testOptions: IdeTestOptionsImpl?,
    abiFilters: Set<String>,
    buildInformation: IdeBuildTasksAndOutputInformationImpl,
    codeShrinker: CodeShrinker?,
    privacySandboxSdkInfo: IdePrivacySandboxSdkInfoImpl?,
    desugaredMethodsFiles: List<File>,
    generatedClassPaths: Map<String, File>,
    bytecodeTransforms: List<IdeBytecodeTransformationImpl>?,
    generatedAssetFolders: List<File>,
    unused: String = "" // to prevent clash
  ) : this(
    name,
    compileTaskName,
    assembleTaskName,
    classesFolder.toImpl(),
    variantSourceProvider,
    multiFlavorSourceProvider,
    ideSetupTaskNames,
    generatedSourceFolders.toImpl(),
    isTestArtifact,
    compileClasspathCore,
    runtimeClasspathCore,
    unresolvedDependencies,
    applicationId,
    signingConfigName,
    isSigned,
    generatedResourceFolders.toImpl(),
    additionalRuntimeApks.toImpl(),
    testOptions,
    abiFilters,
    buildInformation,
    codeShrinker,
    privacySandboxSdkInfo,
    desugaredMethodsFiles.toImpl(),
    generatedClassPaths.toImpl(),
    bytecodeTransforms,
    generatedAssetFolders.toImpl()
  )
}

data class IdeAndroidArtifactImpl(
  private val core: IdeAndroidArtifactCoreImpl,
  private val resolver: IdeLibraryModelResolverImpl
) : IdeAndroidArtifact, IdeAndroidArtifactCore by core {
  override val compileClasspath: IdeDependencies = IdeDependencies(core.compileClasspathCore, resolver)
  override val runtimeClasspath: IdeDependencies = IdeDependencies(core.runtimeClasspathCore, resolver)
}