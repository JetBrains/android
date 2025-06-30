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
package com.android.tools.idea.gradle.toolchain

import com.android.tools.idea.gradle.extensions.getRecommendedJavaVersion
import com.android.tools.idea.gradle.jdk.GradleDefaultJvmCriteriaStore
import com.android.tools.idea.gradle.project.sync.jdk.ProjectJdkUtils
import com.intellij.openapi.project.Project
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmHelper.updateProjectDaemonJvmCriteria
import java.util.concurrent.CompletableFuture

class GradleDaemonJvmCriteriaInitializer(
  private val project: Project,
  private val externalProjectPath: @SystemIndependent String,
  private val gradleVersion: GradleVersion
) {

  fun initialize(useDefault: Boolean): CompletableFuture<Boolean> {
    if (useDefault) {
      GradleDefaultJvmCriteriaStore.daemonJvmCriteria?.let {
        // Because of IDEA-369696 issue projectSdk is required to run updateDaemonJvm task
        ProjectJdkUtils.setUpEmbeddedJdkAsProjectJdk(project)
        return updateProjectDaemonJvmCriteria(project, externalProjectPath, it)
      }
    }

    val javaVersion = GradleJvmSupportMatrix.getRecommendedJavaVersion(gradleVersion, true)
    return GradleDaemonJvmCriteriaTemplatesManager.generatePropertiesFile(javaVersion, externalProjectPath)
  }
}