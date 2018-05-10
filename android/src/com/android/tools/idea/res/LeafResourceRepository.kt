/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.AbstractResourceRepository

/**
 * An [AbstractResourceRepository] that contains resources from a single app or library. This means all resources are in the same namespace
 * if namespaces are used by the project.
 *
 * @see AbstractResourceRepository.getLeafResourceRepositories
 */
interface LeafResourceRepository {
  /**
   * [ResourceNamespace] that all items in this repository use. This is [ResourceNamespace.RES_AUTO] in non-namespaced projects or a
   * [ResourceNamespace] corresponding to [packageName] in namespaced projects.
   */
  val namespace: ResourceNamespace

  /**
   * Package name from the manifest corresponding to this repository.
   *
   * When the project is namespaced, this corresponds to [namespace]. In non-namespaced projects, [namespace] is
   * [ResourceNamespace.RES_AUTO] but the value returned from this method can be used when automatically migrating a project to use
   * namespaces.
   *
   * @return the package name or null in the unlikely case it cannot be determined.
   */
  val packageName: String?
}
