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
package com.android.tools.res

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.Locale
import com.android.ide.common.resources.ResourceRepository
import com.google.common.collect.ImmutableList

/**
 * [ResourceRepositoryManager] backed by just a single repository with no framework resources. Suitable for tests and for the cases where
 * where all resources are located in a single [CacheableResourceRepository], e.g. for rendering outside Android Studio.
 */
class SingleRepoResourceRepositoryManager(
  resourcesRepo: CacheableResourceRepository
) : ResourceRepositoryManager {
  override val appResources: CacheableResourceRepository = resourcesRepo
  override val projectResources: ResourceRepository = resourcesRepo
  // TODO(): Support namespaced resources
  override val namespacing: ResourceNamespacing = ResourceNamespacing.DISABLED
  override val namespace: ResourceNamespace = ResourceNamespace.RES_AUTO
  override val localesInProject: ImmutableList<Locale> = ImmutableList.of()
  override val moduleResources: ResourceRepository = resourcesRepo
  override fun getFrameworkResources(languages: Set<String>, overlays: List<FrameworkOverlay>): ResourceRepository? = null
}