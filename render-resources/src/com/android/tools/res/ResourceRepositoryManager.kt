/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.res

import com.android.annotations.concurrency.Slow
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.Locale
import com.android.ide.common.resources.ResourceRepository
import com.google.common.collect.ImmutableList

/**
 * Studio independent version of [StudioResourceRepositoryManager]
 */
interface ResourceRepositoryManager {
  /**
   * Returns the repository with all non-framework resources available to a given module (in the current variant).
   * This includes not just the resources defined in this module, but in any other modules that this module depends
   * on, as well as any libraries those modules may depend on (e.g. appcompat). This repository also contains sample
   * data resources associated with the [ResourceNamespace.TOOLS] namespace.
   *
   * <p>When a layout is rendered in the layout editor, it is getting resources from the app resource repository:
   * it should see all the resources just like the app does.
   *
   * @return the computed repository
   */
  @get:Slow
  val appResources: CacheableResourceRepository

  /**
   * Returns the resource repository for a module along with all its (local) module dependencies.
   * The repository doesn't contain resources from AAR dependencies.
   *
   * @return the computed repository
   */
  @get:Slow
  val projectResources: ResourceRepository

  /**
   * Returns the [ResourceNamespace] used by the current module.
   *
   * <p>This is read from the manifest, so needs to be run inside a read action.
   */
  val namespacing: ResourceNamespacing
  val namespace: ResourceNamespace

  /** Returns all locales of the project resources. */
  val localesInProject: ImmutableList<Locale>

  /**
   * Returns the resource repository for a single module (which can possibly have multiple resource folders).
   * Does not include resources from any dependencies.
   *
   * <p><b>Note:</b> This method should not be called on the event dispatch thread since it may take long time,
   * or block waiting for a read action lock.
   *
   * @return the computed repository
   */
  val moduleResources: ResourceRepository
  /**
   * Returns the resource repository with framework resources
   *
   * <p><b>Note:</b> This method should not be called on the event dispatch thread since it may take long time.
   *
   * @param languages the set of ISO 639 language codes determining the subset of resources to load.
   *     May be empty to load only the language-neutral resources. The returned repository may contain resources
   *     for more languages than was requested.
   * @param overlays a list of overlays to add to the base framework resources
   * @return the framework repository, or null if the SDK resources directory cannot be determined for the module
   */
  @Slow
  fun getFrameworkResources(languages: Set<String>, overlays: List<FrameworkOverlay>): ResourceRepository?

  @Slow
  fun getFrameworkResources(languages: Set<String>): ResourceRepository? {
    return getFrameworkResources(languages, emptyList())
  }

  /**
   * If namespacing is disabled, the namespace parameter is ignored and the method returns a list containing
   * the single resource repository returned by [.getAppResources]. Otherwise the method returns
   * a list of module, library, or sample data resource repositories for the given namespace. Usually the returned
   * list will contain at most two resource repositories, one for a module and another for its user-defined sample
   * data. More repositories may be returned only when there is a package name collision between modules or
   * libraries.
   *
   *
   * **Note:** This method should not be called on the event dispatch thread since it may take long time,
   * or block waiting for a read action lock.
   *
   * @param namespace the namespace to return resource repositories for
   * @return the repositories for the given namespace
   */
  @Slow
  fun getAppResourcesForNamespace(namespace: ResourceNamespace): List<ResourceRepository> {
    val appRepository = appResources as MultiResourceRepository<*>
    return if (namespacing === ResourceNamespacing.DISABLED) {
      listOf(appRepository)
    }
    else ImmutableList.copyOf<ResourceRepository>(appRepository.getRepositoriesForNamespace(namespace))
  }
}