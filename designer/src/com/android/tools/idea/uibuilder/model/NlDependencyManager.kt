/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.model

import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.gradle.dependencies.GradleDependencyManager
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.uibuilder.api.PaletteComponentHandler
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager
import com.android.tools.idea.util.dependsOn
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.module.Module
import org.jetbrains.android.facet.AndroidFacet

/**
 * Handles depedencies for the Layout Editor.
 *
 * This class acts as an abstraction layer beetween Layout Editor component and the build sytem to manage
 * dependencies required by the provided [NlComponent]
 */
class NlDependencyManager private constructor(private val dependencyManager: DependencyManager) {

  companion object {
    @JvmOverloads
    fun get(dependencyManager: DependencyManager = GradleManager()) = NlDependencyManager(dependencyManager)
  }

  /**
   * Make sure the dependencies of the components being added are present and resolved in the module.
   * If they are not: ask the user if they can be added now.
   * Return true if the dependencies are present now (they may have just been added).
   */
  @JvmOverloads
  fun addDependencies(components: Iterable<NlComponent>,
                      facet: AndroidFacet,
                      syncDoneCallback: (() -> Unit)? = null): Boolean {
    val dependencies = collectDependencies(components)
    if (dependencies.none() || dependencyManager.findMissingDependencies(facet.module, dependencies).none()) {
      syncDoneCallback?.invoke()
      return true
    }
    return dependencyManager.addDependencies(facet.module, dependencies, syncDoneCallback)
  }

  /**
   * Returns true if the current module depends on the specified library.
   */
  fun isModuleDependency(artifact: GoogleMavenArtifactId, facet: AndroidFacet): Boolean = facet.module.dependsOn(artifact)

  /**
   * Returns the [GradleVersion] of the library with specified [artifactId] that the current module depends on.
   * @return the revision or null if the module does not depend on the specified library or the maven version is unknown.
   */
  fun getModuleDependencyVersion(artifactId: GoogleMavenArtifactId, facet: AndroidFacet): GradleVersion? =
      facet.module.project.getProjectSystem().getModuleSystem(facet.module).getResolvedVersion(artifactId)?.mavenVersion

  /**
   * Check if there is any missing dependencies and ask the user only if they are some.
   *
   * User cannot be asked to accept dependencies in a write action.
   * Calls to this method should be made outside a write action, or all dependencies should already be added.
   */
  fun checkIfUserWantsToAddDependencies(toAdd: List<NlComponent>, facet: AndroidFacet): Boolean {
    val dependencies = collectDependencies(toAdd)
    val module = facet.module
    val missing = dependencyManager.findMissingDependencies(module, dependencies)
    if (missing.none()) {
      return true
    }

    val application = ApplicationManagerEx.getApplicationEx()
    if (application.isWriteActionInProgress) {
      kotlin.assert(false) {
        "User cannot be asked to accept dependencies in a write action." +
            "Calls to this method should be made outside a write action"
      }
      return true
    }
    return dependencyManager.dependenciesAccepted(module, dependencies)
  }

  /**
   * Find all dependencies for the given components and map them to a [GradleCoordinate]
   * @see GradleCoordinate.parseCoordinateString()
   */
  private fun collectDependencies(components: Iterable<NlComponent>): Iterable<GradleCoordinate> {
    return components
        .flatMap(this::getDependencies)
        .mapNotNull { artifact -> GradleCoordinate.parseCoordinateString(artifact + ":+") }
        .toList()
  }

  /**
   * Find the Gradle dependency for the given component and return them as a list of String
   */
  private fun getDependencies(component: NlComponent): Set<String> {
    val artifacts = mutableSetOf<String>()
    val tagName = component.tag.name
    val handler = ViewHandlerManager.get(component.model.project).getHandler(tagName) ?: return emptySet()
    val artifactId = handler.getGradleCoordinateId(tagName)
    if (artifactId != PaletteComponentHandler.IN_PLATFORM) {
      artifacts.add(artifactId)
    }
    component.children.flatMap { getDependencies(it) }.toCollection(artifacts)

    return artifacts.toSet()
  }

  /**
   * Abstraction for call to the build system related code for testability.
   *
   * This won't be needed once the OneStudio abstraction is in place
   */
  interface DependencyManager {
    fun dependenciesAccepted(module: Module, missingDependencies: Iterable<GradleCoordinate>): Boolean
    fun findMissingDependencies(module: Module, dependencies: Iterable<GradleCoordinate>): Iterable<GradleCoordinate>
    fun addDependencies(module: Module, dependencies: Iterable<GradleCoordinate>, syncDoneCallback: (() -> Unit)?): Boolean
  }

  private class GradleManager : DependencyManager {
    override fun dependenciesAccepted(module: Module, missingDependencies: Iterable<GradleCoordinate>): Boolean {
      return GradleDependencyManager.getInstance(module.project).userWantToAddDependencies(module, missingDependencies.toList())
    }

    override fun findMissingDependencies(module: Module, dependencies: Iterable<GradleCoordinate>): Iterable<GradleCoordinate> {
      return GradleDependencyManager.getInstance(module.project).findMissingDependencies(module, dependencies)
    }

    override fun addDependencies(module: Module, dependencies: Iterable<GradleCoordinate>, syncDoneCallback: (() -> Unit)?): Boolean {
      return GradleDependencyManager.getInstance(module.project).addDependenciesAndSync(module, dependencies, syncDoneCallback)
    }
  }
}
