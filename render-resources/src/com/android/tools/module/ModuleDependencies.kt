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
package com.android.tools.module

import com.android.SdkConstants
import com.android.ide.common.repository.GoogleMavenArtifactId

/** Information about module dependencies required for rendering. */
interface ModuleDependencies {
  fun dependsOn(artifactId: GoogleMavenArtifactId): Boolean

  fun dependsOnAndroidx(): Boolean = GoogleMavenArtifactId.values()
    .filter { it.mavenGroupId.startsWith(SdkConstants.ANDROIDX_PKG) }
    .any { dependsOn(it) }

  /** Returns a list of `R` class package names (resource package names) from the module and all of its dependencies. */
  fun getResourcePackageNames(includeExternalLibraries: Boolean): List<String>

  fun findViewClass(fqcn: String): ViewClass?
}