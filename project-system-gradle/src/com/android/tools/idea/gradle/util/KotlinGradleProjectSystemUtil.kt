/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.util

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.gradle.configuration.KotlinGradleSourceSetData
import org.jetbrains.kotlin.idea.gradleTooling.KotlinGradlePluginVersion
import org.jetbrains.kotlin.idea.gradleTooling.compareTo

object KotlinGradleProjectSystemUtil {
  /**
   * Determines the versions of the Kotlin plugin in use in the external (Gradle) project with root at projectPath.  The result can be:
   * - an empty list, if there are no Kotlin modules in the project.
   * - list with multiple items in descending order, if there are multiple Kotlin modules using different versions of the Kotlin compiler.
   * - null, if sync has never succeeded in this session.
   */
  fun getKotlinVersionsInUse(project: Project, gradleProjectPath: String): List<KotlinGradlePluginVersion>? {
    val projectData = ExternalSystemApiUtil.findProjectNode(project, GradleProjectSystemUtil.GRADLE_SYSTEM_ID, gradleProjectPath)
                      ?: return null

    val kotlinVersions = mutableSetOf<KotlinGradlePluginVersion>()

    projectData.visit { node: DataNode<*> ->
      if (node.key == KotlinGradleSourceSetData.Companion.KEY) {
        val data = node.data as KotlinGradleSourceSetData
        val kotlinPluginVersion = data.kotlinPluginVersion
        if (kotlinPluginVersion != null) {
          KotlinGradlePluginVersion.parse(kotlinPluginVersion)?.let { kotlinVersions.add(it) }
        }
      }
    }

    return kotlinVersions.sortedWith { a, b -> b.compareTo(a) }
  }
}