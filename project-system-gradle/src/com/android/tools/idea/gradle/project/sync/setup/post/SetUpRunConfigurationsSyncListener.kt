/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.setup.post

import com.android.tools.idea.gradle.project.sync.GradleSyncListenerWithRoot
import com.android.tools.idea.project.AndroidRunConfigurations
import com.android.tools.idea.projectsystem.getAndroidFacets
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.SystemIndependent


class SetUpRunConfigurationsSyncListener : GradleSyncListenerWithRoot {
  override fun syncSucceeded(project: Project, rootProjectPath: @SystemIndependent String) {
    if (ExternalSystemUtil.isNoBackgroundMode()) {
      setUpRunConfigurations(project)
    } else {
      ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Setting up run configurations...") {
        override fun run(indicator: ProgressIndicator) {
          setUpRunConfigurations(project)
        }
      })
    }
  }
}

private fun setUpRunConfigurations(project: Project) {
  project.getAndroidFacets().filter { it.configuration.isAppProject }.forEach {
    AndroidRunConfigurations.getInstance().createRunConfiguration(it)
  }
}

