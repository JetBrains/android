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
package com.android.tools.idea.testing

import com.android.tools.idea.gradle.util.GradleConfigProperties
import com.android.tools.idea.sdk.Jdks
import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkProvider
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import org.jetbrains.plugins.gradle.GradleManager
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File

object JdkUtils {

  @JvmStatic
  fun getEmbeddedJdkPathWithVersion(version: JavaSdkVersion): File {
    val embeddedJdkPath = when (version) {
      JavaSdkVersion.JDK_21 -> JdkConstants.JDK_21_PATH
      JavaSdkVersion.JDK_17 -> JdkConstants.JDK_17_PATH
      JavaSdkVersion.JDK_11 -> JdkConstants.JDK_11_PATH
      JavaSdkVersion.JDK_1_8 -> JdkConstants.JDK_1_8_PATH
      else -> error("Unsupported JavaSdkVersion: $version")
    }
    return File(embeddedJdkPath)
  }

  @JvmStatic
  fun overrideProjectGradleJdkPathWithVersion(gradleRootProject: File, jdkVersion: JavaSdkVersion) {
    val configProperties = GradleConfigProperties(gradleRootProject)
    val currentJdkVersion = configProperties.javaHome?.toPath()?.let {
      Jdks.getInstance().findVersion(it)
    }
    if (currentJdkVersion != jdkVersion) {
      getEmbeddedJdkPathWithVersion(jdkVersion).also {
        configProperties.javaHome = it
        configProperties.save()
      }
    }
  }

  fun createNewGradleJvmProjectJdk(project: Project, parent: Disposable): Sdk {
    val gradleExecutionSettings =
      (ExternalSystemApiUtil.getManager(GradleConstants.SYSTEM_ID) as GradleManager).executionSettingsProvider.`fun`(
        com.intellij.openapi.util.Pair(
          project,
          project.guessProjectDir()?.path
        )
      )
    @Suppress("UnstableApiUsage")
    val sdk = ExternalSystemJdkProvider.getInstance().createJdk(null, gradleExecutionSettings.javaHome.orEmpty())
    if (sdk is Disposable) {
      Disposer.register(parent, sdk)
    }
    return sdk
  }
}