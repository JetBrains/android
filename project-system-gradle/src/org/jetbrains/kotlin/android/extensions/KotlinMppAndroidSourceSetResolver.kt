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
package org.jetbrains.kotlin.android.extensions

import com.android.tools.idea.gradle.project.sync.idea.data.model.KotlinMultiplatformAndroidSourceSetData
import com.android.tools.idea.gradle.project.sync.idea.data.model.KotlinMultiplatformAndroidSourceSetType
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil

/**
 * Responsible for populating [AndroidProjectKeys.KOTLIN_MULTIPLATFORM_ANDROID_SOURCE_SETS_TABLE] with android sourceSets for each kotlin
 * multiplatform module.
 */
class KotlinMppAndroidSourceSetResolver {
  private val sourceSetDataByGradleProjectPath = mutableMapOf<String, MutableMap<KotlinMultiplatformAndroidSourceSetType, String>>()

  /**
   * Record an android sourceSet in the module with the given [gradleProjectPath].
   */
  internal fun recordSourceSetForModule(
    gradleProjectPath: String,
    sourceSetName: String,
    sourceSetType: KotlinMultiplatformAndroidSourceSetType
  ) {
    sourceSetDataByGradleProjectPath.getOrPut(gradleProjectPath) { mutableMapOf() }.also {
      it[sourceSetType] = sourceSetName
    }
  }

  internal fun attachSourceSetDataToProject(
    projectNode: DataNode<ProjectData>
  ) {
    if (ExternalSystemApiUtil.find(projectNode, AndroidProjectKeys.KOTLIN_MULTIPLATFORM_ANDROID_SOURCE_SETS_TABLE) == null) {
      projectNode.createChild(
        AndroidProjectKeys.KOTLIN_MULTIPLATFORM_ANDROID_SOURCE_SETS_TABLE,
        KotlinMultiplatformAndroidSourceSetData(
          this.sourceSetDataByGradleProjectPath.toMap()
        )
      )
    }
  }

  /**
   * Returns tha main android sourceSet in the module with the given [gradleProjectPath].
   */
  fun getMainSourceSetForProject(
    gradleProjectPath: String
  ) = sourceSetDataByGradleProjectPath[gradleProjectPath]?.get(KotlinMultiplatformAndroidSourceSetType.MAIN)
}