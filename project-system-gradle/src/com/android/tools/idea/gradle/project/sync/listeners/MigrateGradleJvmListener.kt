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
package com.android.tools.idea.gradle.project.sync.listeners

import com.android.tools.idea.gradle.project.AndroidGradleProjectSettingsControlBuilder.Companion.ANDROID_STUDIO_JAVA_HOME_NAME
import com.android.tools.idea.gradle.project.AndroidGradleProjectSettingsControlBuilder.Companion.EMBEDDED_JDK_NAME
import com.android.tools.idea.gradle.project.sync.GradleSyncListenerWithRoot
import com.android.tools.idea.gradle.project.sync.jdk.JdkUtils
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.USE_JAVA_HOME
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.plugins.gradle.settings.GradleSettings

private val LOG = Logger.getInstance(MigrateGradleJvmListener::class.java)
/**
 * This GradleSyncListener is responsible for migrate Gradle projects away of the hardcoded jdk naming
 * using platform convention: vendor + version i.e. jbr-17 or special macros defined on [ExternalSystemJdkUtil]
 */
open class MigrateGradleJvmListener : GradleSyncListenerWithRoot {

  override fun syncStarted(project: Project, rootProjectPath: @SystemIndependent String) {
    val projectRootSettings = GradleSettings.getInstance(project).getLinkedProjectSettings(rootProjectPath)
    when (projectRootSettings?.gradleJvm) {
      EMBEDDED_JDK_NAME -> runWriteActionAndWait {
        JdkUtils.setProjectGradleJvmToUseEmbeddedJdk(project, rootProjectPath)?.let { gradleJvm ->
          LOG.info("Project Gradle root: $rootProjectPath gradleJvm updated from $EMBEDDED_JDK_NAME to $gradleJvm")
        }
      }
      ANDROID_STUDIO_JAVA_HOME_NAME -> {
        projectRootSettings.gradleJvm = USE_JAVA_HOME
        LOG.info("Project Gradle root: $rootProjectPath gradleJvm updated from $ANDROID_STUDIO_JAVA_HOME_NAME to $USE_JAVA_HOME")
      }
    }
  }
}