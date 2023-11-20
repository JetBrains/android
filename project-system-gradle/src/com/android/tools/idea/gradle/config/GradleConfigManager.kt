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
package com.android.tools.idea.gradle.config

import com.android.tools.idea.IdeInfo
import com.android.tools.idea.gradle.util.GradleConfigProperties
import com.android.tools.idea.sdk.GradleDefaultJdkPathStore
import com.android.tools.idea.sdk.IdeSdks
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.SystemIndependent
import java.io.File

/**
 * Manager class for the [GradleConfigProperties] that will expose necessary
 * methods in order to handle the different defined properties
 */
object GradleConfigManager {

  fun initializeJavaHome(project: Project, rootProjectPath: @SystemIndependent String) {
    val configProperties = GradleConfigProperties(File(rootProjectPath))
    if (configProperties.javaHome != null) return

    val jdkPathCandidatesSortedByPriority = getJdkCandidates(project)
      .filter { it.isNotEmpty() && ExternalSystemJdkUtil.isValidJdk(it) }

    jdkPathCandidatesSortedByPriority.firstOrNull()?.let { jdkPath ->
      configProperties.javaHome = File(jdkPath)
      configProperties.save()
    }
  }

  private fun getJdkCandidates(project: Project): List<@NonNls String> {
    return buildList {
      add(ProjectRootManager.getInstance(project).projectSdk?.homePath)
      add(GradleDefaultJdkPathStore.jdkPath)
      if (IdeInfo.getInstance().isAndroidStudio) {
        add(IdeSdks.getInstance().embeddedJdkPath.toString())
      } else {
        IdeSdks.getInstance().jdkPath?.let { add(it.toString()) }
      }
    }.filterNotNull()
  }
}