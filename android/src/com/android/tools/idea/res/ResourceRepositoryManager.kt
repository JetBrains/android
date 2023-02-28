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
package com.android.tools.idea.res

import com.android.annotations.concurrency.Slow
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.Locale
import com.android.tools.idea.model.Namespacing
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
  val appResources: LocalResourceRepository

  /**
   * Returns the resource repository for a module along with all its (local) module dependencies.
   * The repository doesn't contain resources from AAR dependencies.
   *
   * @return the computed repository
   */
  @get:Slow
  val projectResources: LocalResourceRepository

  /**
   * Returns the [ResourceNamespace] used by the current module.
   *
   * <p>This is read from the manifest, so needs to be run inside a read action.
   */
  val namespacing: Namespacing
  val namespace: ResourceNamespace

  /** Returns all locales of the project resources. */
  val localesInProject: ImmutableList<Locale>
}