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
package com.android.tools.idea.gradle.project.sync.listeners

import com.android.tools.idea.gradle.config.GradleConfigManager
import com.android.tools.idea.gradle.project.sync.GradleSyncListenerWithRoot
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.USE_GRADLE_LOCAL_JAVA_HOME

/**
 * This GradleSyncListener is responsible to initialize the java.home when the current project uses [USE_GRADLE_LOCAL_JAVA_HOME] macro,
 * resolving those cases when users tries to sync a project without having configured yet the java.home in .gradle/config.properties.
 */
class InitializeGradleLocalJavaHomeListener : GradleSyncListenerWithRoot {

  override fun syncStarted(project: Project, rootProjectPath: @SystemIndependent String) {
    val projectRootSettings = GradleSettings.getInstance(project).getLinkedProjectSettings(rootProjectPath)
    when (projectRootSettings?.gradleJvm) {
      USE_GRADLE_LOCAL_JAVA_HOME -> GradleConfigManager.initializeJavaHome(project, rootProjectPath)
    }
  }
}