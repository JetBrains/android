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
package com.android.tools.idea.gradle.project.sync

import com.android.tools.idea.projectsystem.gradle.sync.Counter

class SyncCounters {
  val buildInfoPhase = Counter("buildInfoPhase")
  val projectListPhase = Counter("projectListPhase")
  val projectInfoPhase = Counter("projectInfoPhase")
  val variantAndDependencyResolutionPhase = Counter("variantAndDependencyResolutionPhase")
  val additionalArtifactsPhase = Counter("additionalArtifactsPhase")

  val projectModel = Counter("projectModel")
  val projectGraphModel = Counter("projectGraphModel")
  val variantDependenciesModel = Counter("variantDependenciesModel")
  val additionalArtifactsModel = Counter("additionalArtifactsModel")
  val kotlinModel = Counter("kotlinModel")
  val kaptModel = Counter("kaptModel")
  val mppModel = Counter("mppModel")
  val nativeModel = Counter("nativeModel")
  val otherModel = Counter("otherModel")

  override fun toString(): String {
    return buildString {
      append(buildInfoPhase)
      append(projectListPhase)
      append(projectInfoPhase)
      append(variantAndDependencyResolutionPhase)
      append(additionalArtifactsPhase)
      append(projectModel)
      append(projectGraphModel)
      append(variantDependenciesModel)
      append(additionalArtifactsModel)
      append(kotlinModel)
      append(kaptModel)
      append(mppModel)
      append(otherModel)
    }
  }
}
