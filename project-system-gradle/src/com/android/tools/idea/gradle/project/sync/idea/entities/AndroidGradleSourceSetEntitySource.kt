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
package com.android.tools.idea.gradle.project.sync.idea.entities

import org.jetbrains.plugins.gradle.service.syncContributor.entitites.GradleEntitySource
import org.jetbrains.plugins.gradle.service.syncContributor.entitites.GradleProjectEntitySource

/** Entity source identifying all Gradle source set entities that are created from Android projects.  */
class AndroidGradleSourceSetEntitySource(
  val projectEntitySource: GradleProjectEntitySource,
  val sourceSetName: String,
) : GradleEntitySource {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is AndroidGradleSourceSetEntitySource) return false

    if (projectEntitySource != other.projectEntitySource) return false
    if (sourceSetName != other.sourceSetName) return false

    return true
  }

  override fun hashCode(): Int {
    var result = projectEntitySource.hashCode()
    result = 31 * result + sourceSetName.hashCode()
    return result
  }
}