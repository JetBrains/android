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
package org.jetbrains.android.refactoring

import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.refactoring.actions.BaseRefactoringAction
import org.jetbrains.android.util.AndroidUtils.hasAndroidFacets

/**
 * This class ensures that Gradle-based actions are disabled and invisible in non-Gradle-based projects.
 */
abstract class AndroidGradleBaseRefactoringAction: BaseRefactoringAction() {
  override fun update(e: AnActionEvent) {
    val presentation = e.presentation
    val project = e.project
    if (project == null || !hasAndroidFacets(project)) {
      presentation.isEnabledAndVisible = false
      return
    }

    val projectSystem = project.getProjectSystem()
    if (projectSystem !is GradleProjectSystem) {
      presentation.isEnabledAndVisible = false
      return
    }

    return super.update(e)
  }
}