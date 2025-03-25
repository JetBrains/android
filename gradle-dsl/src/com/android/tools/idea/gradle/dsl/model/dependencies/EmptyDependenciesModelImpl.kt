/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.dependencies

import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec
import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel
import com.android.tools.idea.gradle.dsl.api.dependencies.DependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.FileDependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.FileTreeDependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ModuleDependencyModel
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo
import com.android.tools.idea.gradle.dsl.parser.elements.EmptyGradleBlockModel
import com.intellij.psi.PsiElement

class EmptyDependenciesModelImpl: EmptyGradleBlockModel(), DependenciesModel {
  override fun all(): List<DependencyModel> = listOf()

  override fun artifacts(configurationName: String): List<ArtifactDependencyModel> = listOf()

  override fun artifacts(): List<ArtifactDependencyModel> = listOf()

  override fun addArtifact(configurationName: String, compactNotation: String) =
    throw UnsupportedOperationException("Call is not supported for Declarative")

  override fun addArtifact(configurationName: String,
                           compactNotation: String,
                           excludes: List<ArtifactDependencySpec>) =
    throw UnsupportedOperationException("Call is not supported for Declarative")


  override fun containsArtifact(configurationName: String, dependency: ArtifactDependencySpec): Boolean =
    throw UnsupportedOperationException("Call is not supported for Declarative")


  override fun addArtifact(configurationName: String,
                           reference: ReferenceTo,
                           excludes: List<ArtifactDependencySpec>) =
    throw UnsupportedOperationException("Call is not supported for Declarative")


  override fun addArtifact(configurationName: String, reference: ReferenceTo) =
    throw UnsupportedOperationException("Call is not supported for Declarative")


  override fun addArtifact(configurationName: String, dependency: ArtifactDependencySpec) =
    throw UnsupportedOperationException("Call is not supported for Declarative")


  override fun addArtifact(configurationName: String,
                           dependency: ArtifactDependencySpec,
                           excludes: List<ArtifactDependencySpec>) =
    throw UnsupportedOperationException("Call is not supported for Declarative")


  override fun addPlatformArtifact(configurationName: String, compactNotation: String, enforced: Boolean) =
    throw UnsupportedOperationException("Call is not supported for Declarative")

  override fun addPlatformArtifact(configurationName: String, reference: ReferenceTo, enforced: Boolean) =
    throw UnsupportedOperationException("Call is not supported for Declarative")

  override fun addPlatformArtifact(configurationName: String, dependency: ArtifactDependencySpec, enforced: Boolean) =
    throw UnsupportedOperationException("Call is not supported for Declarative")

  override fun replaceArtifactByPsiElement(oldPsiElement: PsiElement, newArtifact: ArtifactDependencySpec?): Boolean  =
    throw UnsupportedOperationException("Call is not supported for Declarative")


  override fun modules(): List<ModuleDependencyModel> = listOf()

  override fun addModule(configurationName: String, path: String) =
    throw UnsupportedOperationException("Call is not supported for Declarative")

  override fun addModule(configurationName: String, path: String, config: String?) =
    throw UnsupportedOperationException("Call is not supported for Declarative")

  override fun fileTrees(): List<FileTreeDependencyModel> = listOf()

  override fun addFileTree(configurationName: String, dir: String) =
    throw UnsupportedOperationException("Call is not supported for Declarative")


  override fun addFileTree(configurationName: String,
                           dir: String,
                           includes: List<String>?,
                           excludes: List<String>?) =
    throw UnsupportedOperationException("Call is not supported for Declarative")

  override fun files(): List<FileDependencyModel> = listOf()

  override fun addFile(configurationName: String, file: String) =
    throw UnsupportedOperationException("Call is not supported for Declarative")

  override fun remove(dependency: DependencyModel) =
    throw UnsupportedOperationException("Call is not supported for Declarative")

}