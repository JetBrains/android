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

import com.android.tools.idea.gradle.model.IdeArtifactName
import com.android.tools.idea.gradle.model.IdeDependencies
import com.android.tools.idea.gradle.model.IdeJavaArtifact
import com.android.tools.idea.gradle.model.IdeJavaArtifactCore
import com.android.tools.idea.gradle.model.IdeSourceProvider
import java.io.File


data class IdeJavaArtifactCoreImpl(
  override val name: IdeArtifactName,
  override val compileTaskName: String?,
  override val assembleTaskName: String?,
  override val classesFolder: List<FileImpl>,
  override val variantSourceProvider: IdeSourceProvider?,
  override val multiFlavorSourceProvider: IdeSourceProvider?,
  override val ideSetupTaskNames: List<String>,
  override val generatedSourceFolders: List<FileImpl>,
  override val isTestArtifact: Boolean,
  override val compileClasspathCore: IdeDependenciesCoreImpl,
  override val runtimeClasspathCore: IdeDependenciesCoreImpl,
  override val unresolvedDependencies: List<IdeUnresolvedDependencyImpl>,
  override val mockablePlatformJar: FileImpl?,
  override val generatedClassPaths: Map<String, FileImpl>,
  override val bytecodeTransforms: List<IdeBytecodeTransformationImpl>?,
  ) : IdeJavaArtifactCore {
  constructor(
    name: IdeArtifactName,
    compileTaskName: String?,
    assembleTaskName: String?,
    classesFolder: List<File>,
    variantSourceProvider: IdeSourceProvider?,
    multiFlavorSourceProvider: IdeSourceProvider?,
    ideSetupTaskNames: List<String>,
    generatedSourceFolders: List<File>,
    isTestArtifact: Boolean,
    compileClasspathCore: IdeDependenciesCoreImpl,
    runtimeClasspathCore: IdeDependenciesCoreImpl,
    unresolvedDependencies: List<IdeUnresolvedDependencyImpl>,
    mockablePlatformJar: File?,
    generatedClassPaths: Map<String, File>,
    bytecodeTransforms: List<IdeBytecodeTransformationImpl>?,
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
    mockablePlatformJar?.toImpl(),
    generatedClassPaths.toImpl(),
    bytecodeTransforms
  )
}

data class IdeJavaArtifactImpl(
  private val core: IdeJavaArtifactCoreImpl,
  private val resolver: IdeLibraryModelResolverImpl
) : IdeJavaArtifact, IdeJavaArtifactCore {
  override val mockablePlatformJar: FileImpl? = core.mockablePlatformJar
  override val compileClasspathCore: IdeDependenciesCoreImpl = core.compileClasspathCore
  override val runtimeClasspathCore: IdeDependenciesCoreImpl = core.runtimeClasspathCore
  override val name: IdeArtifactName = core.name
  override val compileTaskName: String? = core.compileTaskName
  override val assembleTaskName: String? = core.assembleTaskName
  override val classesFolder: List<FileImpl> = core.classesFolder
  override val variantSourceProvider: IdeSourceProvider? = core.variantSourceProvider
  override val multiFlavorSourceProvider: IdeSourceProvider? = core.multiFlavorSourceProvider
  override val ideSetupTaskNames: List<String> = core.ideSetupTaskNames
  override val generatedSourceFolders: List<FileImpl> = core.generatedSourceFolders
  override val isTestArtifact: Boolean = core.isTestArtifact
  override val unresolvedDependencies: List<IdeUnresolvedDependencyImpl> = core.unresolvedDependencies
  override val generatedClassPaths: Map<String, File> = core.generatedClassPaths
  override val bytecodeTransforms: List<IdeBytecodeTransformationImpl>? = core.bytecodeTransforms
  override val compileClasspath: IdeDependencies = IdeDependencies(core.compileClasspathCore, resolver)
  override val runtimeClasspath: IdeDependencies = IdeDependencies(core.runtimeClasspathCore, resolver)
}