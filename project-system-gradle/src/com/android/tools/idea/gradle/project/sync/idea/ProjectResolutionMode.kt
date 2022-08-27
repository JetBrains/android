/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.idea

/**
 * Describes the requested project resolver chain operating mode and its parameters.
 *
 * Note that the the requestor may/should additionally configure the chain to contain only a subset of the default resolvers.
 */
sealed class ProjectResolutionMode {
  class FetchNativeVariantsMode(
    /** moduleId => variantName where moduleId is by [com.android.tools.idea.gradle.project.sync.Modules.createUniqueModuleId] */
    val moduleVariants: Map<String, String>,
    val requestedAbis: Set<String>
  ) : ProjectResolutionMode()

  object FetchAllVariantsMode : ProjectResolutionMode()

  object SingleVariantSyncProjectMode : ProjectResolutionMode()
}
