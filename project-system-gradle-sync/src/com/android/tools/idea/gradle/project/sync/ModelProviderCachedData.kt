/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync

import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import org.gradle.tooling.model.gradle.BasicGradleProject
import java.io.Serializable

/** Class for storing per Gradle-project data between multiple model providers. */
class ModelProviderCachedData: Serializable {
  internal val data = mutableMapOf<BasicGradleProject, CachedAndroidProjectData>()
}

internal data class CachedAndroidProjectData (
  val selectedVariantName: String,
  val projectType: IdeAndroidProjectType,
  val shouldSkipRuntimeClassPathForLibraries: Boolean,
  val allOutgoingProjectDependencies: List<String>
)



