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
   * Analyzes the compatibility of the [dependenciesToAdd] with the existing artifacts in the project.
   *
   * The version component of each of the coordinates in [dependenciesToAdd] are disregarded.
   * The result is a triplet consisting of:
   * <ul>
   *   <li>A list of coordinates including a valid version found in the repository</li>
   *   <li>A list of coordinates that were missing from the repository</li>
   *   <li>A warning string describing the compatibility issues that could not be resolved if any</li>
   * </ul>
   *
   * An incompatibility warning is either a compatibility with problem among the already existing artifacts,
   * or a compatibility problem with one of the [dependenciesToAdd]. In the latter case the coordinates in
   * the found coordinates are simply the latest version of the libraries, which may or may not cause build
   * errors if they are added to the project.
   * <p>
   * An empty warning value and an empty missing list of coordinates indicates a successful result.
   * <p>
   * **Note**: This function may cause the parsing of build files and as such should not be called from the UI thread.
   */
  fun analyzeDependencyCompatibility(dependenciesToAdd: List<GradleCoordinate>)
    : Triple<List<GradleCoordinate>, List<GradleCoordinate>, String>

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
   * <p>
   * **Note**: This function may perform read actions and may cause the parsing of build files, as such should not be called from
   * the UI thread.
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
   * <p>
   * **Note**: This function will not acquire any locks during it's operation.
   */
  @Throws(DependencyManagementException::class)
  fun getResolvedDependency(coordinate: GradleCoordinate): GradleCoordinate?

  /** Whether this module system supports adding dependencies of the given type via [registerDependency] */
  fun canRegisterDependency(type: DependencyType = DependencyType.IMPLEMENTATION): CapabilityStatus

  /**
   * Register a requested dependency with the build system. Note that the requested dependency won't be available (a.k.a. resolved)
   * until the next sync. To ensure the dependency is resolved and available for use, sync the project after calling this function.
   * <p>
   * **Note**: This function will perform a write action.
   */
  fun registerDependency(coordinate: GradleCoordinate)

  /**
   * Like [registerDependency] where you can specify the type of dependency to add
   */
  fun registerDependency(coordinate: GradleCoordinate, type: DependencyType)

  /**
   * Returns the resolved libraries that this module depends on.
   * <p>
   * **Note**: This function will not acquire read/write locks during it's operation.
   */
  fun getResolvedDependentLibraries(): Collection<Library>

  /**
   * Returns the Android modules that this module transitively depends on for resources.
   * As Android modules, each module in the returned list will have an associated AndroidFacet.
   *
   * Where supported, the modules will be returned in overlay order to help with resource resolution,
   * but this is only to support legacy callers. New callers should avoid making such assumptions and
   * instead determine the overlay order explicitly if necessary.
   *
   * TODO(b/118317486): Remove this API once resource module dependencies can accurately
   * be determined from order entries for all supported build systems.
   */
  fun getResourceModuleDependencies(): List<Module>

  /**
   * Returns the Android modules that directly depend on this module for resources.
   * As Android modules, each module in the returned list will have an associated AndroidFacet.
   *
   * TODO(b/118317486): Remove this API once resource module dependencies can accurately
   * be determined from order entries for all supported build systems.
   */
  fun getDirectResourceModuleDependents(): List<Module>

  /**
   * Determines whether or not the underlying build system is capable of generating a PNG
   * from vector graphics.
   */
  fun canGeneratePngFromVectorGraphics(): CapabilityStatus

}

/** Types of dependencies that [AndroidModuleSystem.registerDependency] can add */
enum class DependencyType {
  IMPLEMENTATION,
  // TODO: Add "API," & support in build systems
  ANNOTATION_PROCESSOR
}
