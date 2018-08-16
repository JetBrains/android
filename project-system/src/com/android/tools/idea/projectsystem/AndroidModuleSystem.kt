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
package com.android.tools.idea.projectsystem

import com.android.ide.common.repository.GradleCoordinate
import com.android.projectmodel.Library
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile

/**
 * Provides a build-system-agnostic interface to the build system. Instances of this interface
 * contain methods that apply to a specific [Module].
 */
interface AndroidModuleSystem: ClassFileFinder, SampleDataDirectoryProvider {
  /**
   * Requests information about the folder layout for the module. This can be used to determine
   * where files of various types should be written.
   *
   * TODO: Figure out and document the rest of the contracts for this method, such as how targetDirectory is used,
   * what the source set names are used for, and why the result is a list
   *
   * @param targetDirectory to filter the relevant source providers from the android facet.
   * @return a list of templates created from each of the android facet's source providers.
   * In cases where the source provider returns multiple paths, we always take the first match.
   */
  fun getModuleTemplates(targetDirectory: VirtualFile?): List<NamedModuleTemplate>

  /**
   * Returns a [GradleCoordinate] of the latest compatible artifact of the given maven project.
   * This function returns non-null only if the build system can find a version of the artifact that is
   * compatible with the rest of this module's dependencies.
   * When there are multiple versions of the artifact that satisfy the above conditions, the latest
   * stable artifact is selected. In the event that a stable artifact does not exist this function
   * will fallback to searching for preview artifacts.
   */
  fun getLatestCompatibleDependency(mavenGroupId: String, mavenArtifactId: String): GradleCoordinate?

  /**
   * Returns the dependency accessible to sources contained in this module referenced by its [GradleCoordinate] as registered with the
   * build system (e.g. build.gradle for Gradle, BUILD for bazel, etc). Build systems such as Gradle allow users to specify a dependency
   * such as x.y.+, which it will resolve to a specific version at sync time. This method returns the version registered in the build
   * script.
   * <p>
   * This method will find a dependency that matches the given query coordinate. For example:
   * Query coordinate a:b:+ will return a:b:+ if a:b:+ is registered with the build system.
   * Query coordinate a:b:+ will return a:b:123 if a:b:123 is registered with the build system.
   * Query coordinate a:b:456 will return null if a:b:456 is not registered, even if a:b:123 is.
   * Use [AndroidModuleSystem.getResolvedDependency] if you want the resolved dependency.
   */
  @Throws(DependencyManagementException::class)
  fun getRegisteredDependency(coordinate: GradleCoordinate): GradleCoordinate?

  /**
   * Returns the dependency accessible to sources contained in this module referenced by its [GradleCoordinate].
   * <p>
   * This method will resolve version information to what is resolved. For example:
   * Query coordinate a:b:+ will return a:b:123 if version 123 of that artifact is a resolved dependency.
   * Query coordinate a:b:123 will return a:b:123 if version 123 of that artifact is a resolved dependency.
   * Query coordinate a:b:456 will return null if version 123 is a resolved dependency but not version 456.
   * Use [AndroidModuleSystem.getRegisteredDependency] if you want the registered dependency.
   */
  @Throws(DependencyManagementException::class)
  fun getResolvedDependency(coordinate: GradleCoordinate): GradleCoordinate?

  /**
   * Register a requested dependency with the build system. Note that the requested dependency won't be available (a.k.a. resolved)
   * until the next sync. To ensure the dependency is resolved and available for use, sync the project after calling this function.
   */
  fun registerDependency(coordinate: GradleCoordinate)

  /**
   * Returns the resolved libraries that this module depends on.
   */
  fun getResolvedDependentLibraries(): Collection<Library>

  /**
   * Determines whether or not the underlying build system is capable of generating a PNG
   * from vector graphics.
   */
  fun canGeneratePngFromVectorGraphics(): CapabilityStatus

  /**
   * Determines whether or not the underlying build system supports instant run.
   */
  fun getInstantRunSupport(): CapabilityStatus
}
