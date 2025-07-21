/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.ide.gradle.model.dependencies

import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.tooling.provider.model.ToolingModelBuilder
import org.gradle.util.GradleVersion
import java.io.Serializable

class DeclaredDependenciesModelBuilder : ToolingModelBuilder {
  override fun canBuild(modelName: String): Boolean = modelName == DeclaredDependencies::class.java.name

  override fun buildAll(modelName: String, project: Project): Any {
    val configurationsToCoordinates = mutableMapOf<String, MutableList<Coordinates>>()

    project.configurations.forEach { configuration ->
      if (CONFIGURATIONS_OF_INTEREST.contains(configuration.name)) {
        configuration.dependencies.forEach { dependency ->
          configurationsToCoordinates.getOrPut(configuration.name) { mutableListOf<Coordinates>() }
            .add(dependency.coordinates())
        }
      }
    }
    val allOutgoingProjectDependencies = project.configurations.flatMap {
      it.dependencies.filterIsInstance<ProjectDependency>()
        .map {
          if (GradleVersion.version(project.gradle.gradleVersion) >= GradleVersion.version("8.11")) {
            it.path
          } else {
            it.dependencyProject.path
          }
      }
    }
    return DeclaredDependenciesImpl(configurationsToCoordinates, allOutgoingProjectDependencies)
  }

  private val ProjectDependency.dependencyProject: Project
    get() = javaClass.getMethod("getDependencyProject").invoke(this) as Project

  companion object {
    // FIXME(xof): it would be nice to be able to have a manifest constant shared between this and the
    //  GradleModuleSystem/AndroidProjectSystem (see e.g. DependencyType.configurationName in GradleModuleSystem) except that:
    //  - the need to get the `api` configurations here makes that more complicated; we don't have a one-to-one mapping between
    //    Project System DependencyType and Gradle configurations;
    //  - because of the different execution contexts, sharing data between here and the Project system is tricky.
    //  Instead, just accept that this is the set of configurations corresponding to the current set of DependencyType
    //  and their expected semantics in use in the Android Project System.
    val CONFIGURATIONS_OF_INTEREST = setOf("api", "implementation", "debugApi", "debugImplementation", "annotationProcessor")
  }
}

interface DeclaredDependencies {
  val configurationsToCoordinates: Map<String, List<Coordinates>>
  val allOutgoingProjectDependencies: List<String>
}
interface Coordinates {
  val group: String?
  val name: String
  val version: String?
}

data class DeclaredDependenciesImpl(
  override val configurationsToCoordinates: Map<String, List<Coordinates>>,
  override val allOutgoingProjectDependencies: List<String>,
) : DeclaredDependencies, Serializable
data class CoordinatesImpl(
  override val group: String?,
  override val name: String,
  override val version: String?,
): Coordinates, Serializable
fun Dependency.coordinates(): Coordinates = CoordinatesImpl(group, name, version)