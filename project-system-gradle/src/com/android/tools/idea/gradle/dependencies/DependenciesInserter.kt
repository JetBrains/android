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

import com.android.ide.common.gradle.Dependency
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec
import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.utils.addIfNotNull

@Suppress("AddDependencyUsage")
open class DependenciesInserter {

  open fun addPlatformDependency(
    configuration: String,
    dependency: String,
    enforced: Boolean,
    parsedModel: GradleBuildModel,
    matcher: DependencyMatcher = ExactDependencyMatcher(configuration, dependency)): Set<PsiFile> {
    val buildscriptDependencies = parsedModel.dependencies()
    val updatedFiles = mutableSetOf<PsiFile>()

    if (!buildscriptDependencies.hasArtifact(matcher)) {
      buildscriptDependencies.addPlatformArtifact(configuration, dependency, enforced).also {
        updatedFiles.addIfNotNull(parsedModel.psiFile)
      }
    }

    return updatedFiles
  }

  open fun addDependency(configuration: String,
                         dependency: String,
                         excludes: List<ArtifactDependencySpec>,
                         parsedModel: GradleBuildModel,
                         matcher: DependencyMatcher,
                         sourceSetName: String? = null): Set<PsiFile> {
    val updateFiles = mutableSetOf<PsiFile>()
    val dependenciesModel = getDependenciesModel(sourceSetName, parsedModel)
    if (dependenciesModel != null && !dependenciesModel.hasArtifact(matcher)) {
      dependenciesModel.addArtifact(configuration, dependency, excludes).also {
        updateFiles.addIfNotNull(dependenciesModel.psiElement?.containingFile)
      }
    }
    return updateFiles
  }

  internal fun findDependency(dependency: Dependency,
                              buildModel: GradleBuildModel): ArtifactDependencyModel? {
    val dependenciesModel = buildModel.dependencies()
    val richVersion = dependency.version
    var richVersionIdentifier: String? = null
    if (richVersion != null) richVersionIdentifier = richVersion.toIdentifier()

    val artifacts: List<ArtifactDependencyModel> = ArrayList(dependenciesModel.artifacts())
    for (artifact in artifacts) {
      if (dependency.group == artifact.group().toString()
          && dependency.name == artifact.name().forceString()
          && richVersionIdentifier != artifact.version().toString()) {
        return artifact
      }
    }
    return null
  }

  open fun updateDependencyVersion(dependency: Dependency,
                                   buildModel: GradleBuildModel) {
    check(dependency.version != null) { "Version must not be null for updateDependencyVersion" }
    findDependency(dependency, buildModel)?.let { artifact ->
      buildModel.dependencies().apply {
        remove(artifact)
        addArtifact(artifact.configurationName(), dependency.toString())
      }
    }
  }

  private fun getDependenciesModel(sourceSetName: String?, parsedModel: GradleBuildModel): DependenciesModel? {
    return if (sourceSetName != null) {
      parsedModel.kotlin().sourceSets().find { it.name() == sourceSetName }?.dependencies()
    }
    else {
      parsedModel.dependencies()
    }
  }

  internal fun DependenciesModel.hasArtifact(matcher: DependencyMatcher): Boolean =
    artifacts().any { matcher.match(it) }

  /**
   * This is short version of addDependency function.
   * Assuming there is no excludes and algorithm will search exact dependency declaration in catalog if exists.
   */
  fun addDependency(configuration: String, dependency: String, parsedModel: GradleBuildModel) =
    addDependency(configuration, dependency, listOf(), parsedModel, ExactDependencyMatcher(configuration, dependency))

}