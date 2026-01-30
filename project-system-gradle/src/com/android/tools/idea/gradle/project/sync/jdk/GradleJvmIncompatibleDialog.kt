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
package com.android.tools.idea.gradle.project.sync.jdk

import com.android.tools.idea.gradle.project.sync.idea.issues.DescribedBuildIssueQuickFix
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.util.lang.JavaVersion
import org.jetbrains.android.util.AndroidBundle

object GradleJvmIncompatibleDialog {

  fun showDialog(
    project: Project,
    currentJavaVersion: JavaVersion,
    gradleJvmCompatibility: GradleJvmCompatibilityInfo,
    applyCompatibleJvmAction: DescribedBuildIssueQuickFix,
    openSettingsAction: DescribedBuildIssueQuickFix,
  ) {
    ApplicationManager.getApplication().invokeAndWait {
      val result =
        Messages.showDialog(
          project,
          AndroidBundle.message(
            "android.warning.incompatible.gradle.jvm.dialog.description",
            gradleJvmCompatibility.gradleVersion,
            currentJavaVersion.feature,
            gradleJvmCompatibility.minimumSupportedJavaVersion?.feature ?: "Unknown",
            gradleJvmCompatibility.maximumSupportedJavaVersion?.feature ?: "Unknown",
          ),
          AndroidBundle.message("android.warning.incompatible.gradle.jvm.dialog.title"),
          arrayOf(
            AndroidBundle.message(
              "android.warning.incompatible.gradle.jvm.dialog.button.apply.compatible.jvm",
              gradleJvmCompatibility.recommendedJavaVersion.feature,
            ),
            AndroidBundle.message("android.warning.incompatible.gradle.jvm.dialog.button.open.settings"),
          ),
          0,
          null,
        )

      val quickFixAction =
        when (result) {
          0 -> applyCompatibleJvmAction
          1 -> openSettingsAction
          else -> null
        }
      quickFixAction?.runQuickFix(project, DataContext.EMPTY_CONTEXT)
    }
  }
}
