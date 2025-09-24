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

import com.android.builder.model.v2.models.Versions
import com.android.tools.idea.gradle.model.IdeAndroidProject
import com.android.tools.idea.gradle.model.impl.IdeVariantCoreImpl
import org.gradle.tooling.model.gradle.BasicGradleProject
import java.io.Serializable
import java.util.concurrent.ConcurrentHashMap

/** Class for storing per Gradle-project data between multiple model providers. */
class ModelProviderCachedData(
  private val disableLegacyModelProvidersForSupportedProjects: Boolean
): Serializable {
  internal val versions = ConcurrentHashMap<BasicGradleProject, Versions>()
  internal val data = ConcurrentHashMap<BasicGradleProject, CachedAndroidProjectData>()
  internal val selectedVariant = ConcurrentHashMap<BasicGradleProject, IdeVariantCoreImpl>()

  private var allProjectsSupportedByPhasedSync: Boolean = false

  fun markAllProjectsSupportedByPhasedSync() {
    allProjectsSupportedByPhasedSync = true
  }

  /**
   * If the flag is on to do so, only run legacy providers when there are Gradle subprojects that
   * are not supported. This is used to mutually exclusively run legacy and phased sync model
   * providers.
   *
   * The new phased sync source set model provider (and its corresponding sync contributor)
   * always runs because it is considered lightweight enough.`
   *
   * Dependency model provider is controlled by a completely separate flag and is not affected by
   * this.
   */
  val shouldRunLegacyModelProviders: Boolean get() =
    !disableLegacyModelProvidersForSupportedProjects || !allProjectsSupportedByPhasedSync

  /**
   * This class exists to share data between model providers, and the last one to execute should call this method to drop all shared state.
   * to not cause memory leaks and a previous sync attempt influencing the others.
   *
   * The model providers should be re-created each time anyway but this makes sure we can dispose of the data as early as possible and is
   * just an additional safeguard.
   */
  fun clear() {
    versions.clear()
    data.clear()
    selectedVariant.clear()
  }
}

internal data class CachedAndroidProjectData(
  val modelVersions: ModelVersions,
  val selectedVariantName: String,
  val ideAndroidProject: IdeAndroidProject,
  val shouldSkipRuntimeClassPathForLibraries: Boolean,
  val allOutgoingProjectDependencies: List<String>
)

