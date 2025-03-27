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
package com.android.tools.idea.gradle.dependencies

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.utils.addIfNotNull

/**
 * We assume for now that declarative project is pure (no non-declarative modules)
 * and no version catalog in it.
 */
class DeclarativeDependenciesInserter: DependenciesInserter() {

  override fun addDependency(configuration: String,
                             dependency: String,
                             excludes: List<ArtifactDependencySpec>,
                             parsedModel: GradleBuildModel,
                             matcher: DependencyMatcher,
                             sourceSetName: String?): Set<PsiFile> {
    val changedFiles = mutableSetOf<PsiFile>()
    val dependenciesModel = parsedModel.dependencies()
    if (!dependenciesModel.hasArtifact(matcher)) {
      dependenciesModel.addArtifact(configuration, dependency).also {
        changedFiles.addIfNotNull(dependenciesModel.psiElement?.containingFile)
      }
    }
    return changedFiles
  }

  override fun addPlatformDependency(configuration: String,
                                     dependency: String,
                                     enforced: Boolean,
                                     parsedModel: GradleBuildModel,
                                     matcher: DependencyMatcher): Set<PsiFile> {
    val changedFiles = mutableSetOf<PsiFile>()
    val buildscriptDependencies = parsedModel.dependencies()

    if (!buildscriptDependencies.hasArtifact(matcher)) {
      buildscriptDependencies.addPlatformArtifact(configuration, dependency, enforced).also {
        changedFiles.addIfNotNull(parsedModel.psiElement?.containingFile)
      }
    }
    return changedFiles
  }
}