/*
 * Copyright (C) 2019 The Android Open Source Project
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

import org.jetbrains.android.facet.AndroidFacet

/**
 * An interface used by the project system internally to instantiate project specific [SourceProviders] when the project
 * structure changes.
 */
interface SourceProvidersFactory {
  /**
   * Do not use directly. This method is supposed to be called by the project system internals to instantiate a cached copy
   * of [SourceProviders].
   *
   * Note: Temporarily, if the method returns [null] an implementation for legacy projects will be provided.
   */
  fun createSourceProvidersFor(facet: AndroidFacet): SourceProviders?
}