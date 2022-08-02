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

import com.android.builder.model.v2.models.Versions
import com.android.ide.gradle.model.LegacyV1AgpVersionModel
import org.gradle.tooling.model.gradle.BasicGradleProject

/**
 * The container class of modules we couldn't fetch using parallel Gradle TAPI API.
 * For now this list has :
 *  - All the non-Android modules
 *  - The android modules using an older AGP version than the minimum supported for V2 sync
 */
sealed class BasicIncompleteGradleModule(
  val gradleProject: BasicGradleProject,
  val buildName: String
) {
  val buildId: BuildId get() = BuildId(gradleProject.projectIdentifier.buildIdentifier.rootDir)
  val projectPath: String get() = gradleProject.path
}

/**
 * The container class of Android modules.
 */
sealed class BasicIncompleteAndroidModule(gradleProject: BasicGradleProject, buildName: String)
  :  BasicIncompleteGradleModule(gradleProject, buildName) {
  abstract val agpVersion: String
}

/**
 *  The container class of Android modules that can be fetched using V1 builder models.
 *  legacyV1AgpVersion: The model that contains the agp version used by the AndroidProject. This can be null if the AndroidProject is using
 *  an AGP version lower than the minimum supported version by Android Studio
 */
class BasicV1AndroidModuleGradleProject(
  gradleProject: BasicGradleProject,
  buildName: String,
  private val legacyV1AgpVersion: LegacyV1AgpVersionModel
) :  BasicIncompleteAndroidModule(gradleProject, buildName) {
  override val agpVersion: String
    get() = legacyV1AgpVersion?.agp
}

/**
 * The container class of Android modules that can be fetched using V2 builder models.
 */
class BasicV2AndroidModuleGradleProject(gradleProject: BasicGradleProject, buildName: String, val versions: Versions) :
  BasicIncompleteAndroidModule(gradleProject, buildName)
{
  override val agpVersion: String
    get() = versions.agp
}

/**
 * The container class of non-Android modules.
 */
class BasicNonAndroidIncompleteGradleModule(gradleProject: BasicGradleProject, buildName: String) :
  BasicIncompleteGradleModule(gradleProject, buildName)

