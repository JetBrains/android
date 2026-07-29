/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.projectsystem.gradle

import com.android.tools.idea.gradle.actions.GoToBundleLocationTask
import com.android.tools.idea.gradle.project.ProjectStructure
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil
import com.android.tools.idea.project.AndroidNotification
import com.android.tools.idea.projectsystem.BuildBundleActionToken
import com.android.tools.idea.projectsystem.GradleToken
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

class BuildBundleActionTokenGradle : BuildBundleActionToken<GradleProjectSystem>, GradleToken {
  override fun isAppModulePresent(project: Project): Boolean {
    return ProjectStructure.getInstance(project).getAppHolderModules().isNotEmpty()
  }

  override fun buildBundle(project: Project) {
    val appModules = GradleProjectSystemUtil.getAppHolderModulesSupportingBundleTask(project)
    if (appModules.isNotEmpty()) {
      val gradleBuildInvoker = GradleBuildInvoker.getInstance(project)
      val task = GoToBundleLocationTask(project, appModules, "Generate Bundles")
      val modulesToBuild = appModules.toTypedArray()
      task.executeWhenBuildFinished(gradleBuildInvoker.bundle(modulesToBuild), true)
    } else {
      AndroidNotification.getInstance(project)
        .showBalloon("Generate Bundles", "No modules supporting bundles found", NotificationType.ERROR)
    }
  }
}
