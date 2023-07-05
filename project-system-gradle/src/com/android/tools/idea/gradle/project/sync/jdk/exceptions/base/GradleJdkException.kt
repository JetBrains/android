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
package com.android.tools.idea.gradle.project.sync.jdk.exceptions.base

import com.android.tools.idea.gradle.project.sync.hyperlink.SelectJdkFromFileSystemHyperlink
import com.android.tools.idea.gradle.project.sync.jdk.JdkUtils
import com.android.tools.idea.gradle.project.sync.jdk.exceptions.cause.InvalidGradleJdkCause
import com.android.tools.idea.project.AndroidNotification
import com.android.tools.idea.sdk.GradleDefaultJdkPathStore
import com.android.tools.idea.sdk.IdeSdks
import com.intellij.notification.NotificationType
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.plugins.gradle.util.GradleBundle
import java.io.File

/**
 * Base of the founded invalid JDK configuration that provides a cause [InvalidGradleJdkCause]
 * but also allows an easy recovery for the affected gradle root project.
 */
abstract class GradleJdkException(
  private val project: Project,
  private val gradleRootPath: @SystemIndependent String,
) {

  open val jdkPathLocationFile: File? = null
  val message by lazy { cause.description }
  val reason by lazy { cause.reason }

  fun recover() {
    recoveryJdkCandidates
      .firstOrNull { (_, jdkPath) -> ExternalSystemJdkUtil.isValidJdk(jdkPath)}
      ?.let { (jdkName, jdkPath) ->
        JdkUtils.setProjectGradleJdk(project, gradleRootPath, jdkPath)
        val jdkVersion = JavaSdk.getInstance().getVersionString(jdkPath)  ?: "<unknown>"
        showRecoveredGradleJdkNotification(
          project = project,
          gradleRootPath = gradleRootPath,
          errorDescription = cause.description,
          errorResolution = AndroidBundle.message("project.sync.jdk.recovery.message", jdkName, jdkVersion)
        )
    }
  }

  protected abstract val cause: InvalidGradleJdkCause

  protected open val recoveryJdkCandidates = listOf(
    AndroidBundle.message("gradle.default.jdk.name") to GradleDefaultJdkPathStore.jdkPath.orEmpty(),
    AndroidBundle.message("gradle.embedded.jdk.name") to IdeSdks.getInstance().embeddedJdkPath.toString()
  )

  private fun showRecoveredGradleJdkNotification(
    project: Project,
    gradleRootPath: @SystemIndependent String,
    errorDescription: String,
    errorResolution: String
  ) {
    val title = GradleBundle.message("gradle.jvm.is.invalid")
    val messageBuilder = StringBuilder()
      .appendLine(errorDescription)
      .appendLine(errorResolution)
    val quickFixes = listOfNotNull(SelectJdkFromFileSystemHyperlink.create(project, gradleRootPath))
    AndroidNotification.getInstance(project).showBalloon(title, messageBuilder.toString(), NotificationType.WARNING, *quickFixes.toTypedArray())
  }
}