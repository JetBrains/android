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
import com.android.tools.idea.gradle.model.IdeBytecodeTransformation
import com.android.tools.idea.gradle.model.IdeDependencies
import com.android.tools.idea.gradle.model.IdeJavaArtifact
import com.android.tools.idea.gradle.model.IdeJavaArtifactCore
import com.android.tools.idea.gradle.model.IdeLibraryModelResolver
import java.io.File

data class IdeJavaArtifactCoreImpl(
  override val name: IdeArtifactName,
  override val compileTaskName: String,
  override val assembleTaskName: String,
  override val classesFolder: Collection<File>,
  override val variantSourceProvider: IdeSourceProviderImpl?,
  override val multiFlavorSourceProvider: IdeSourceProviderImpl?,
  override val ideSetupTaskNames: List<String>,
  override val generatedSourceFolders: Collection<File>,
  override val isTestArtifact: Boolean,
  override val compileClasspathCore: IdeDependenciesCoreImpl,
  override val runtimeClasspathCore: IdeDependenciesCoreImpl,
  override val unresolvedDependencies: List<IdeUnresolvedDependencyImpl>,
  override val mockablePlatformJar: File?,
  override val generatedClassPaths: Map<String, File>,
  override val bytecodeTransforms: Collection<IdeBytecodeTransformation>?,
  ) : IdeJavaArtifactCore

data class IdeJavaArtifactImpl(
  private val core: IdeJavaArtifactCore,
  private val resolver: IdeLibraryModelResolver
) : IdeJavaArtifact, IdeJavaArtifactCore by core {
  override val compileClasspath: IdeDependencies = IdeDependenciesImpl(core.compileClasspathCore, resolver)
  override val runtimeClasspath: IdeDependencies = IdeDependenciesImpl(core.runtimeClasspathCore, resolver)
}