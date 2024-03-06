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
import com.android.tools.idea.gradle.util.GradleWrapper
import com.android.tools.idea.project.AndroidNotification
import com.android.tools.idea.sdk.GradleDefaultJdkPathStore
import com.android.tools.idea.sdk.IdeSdks
import com.intellij.notification.NotificationType
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.ProjectJdkTable
import kotlinx.collections.immutable.toImmutableList
import org.gradle.util.GradleVersion
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.jps.model.java.JdkVersionDetector
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
    var resolution: String? = null
    lateinit var notificationType: NotificationType
    recoveryJdkCandidates
      .firstOrNull { candidate -> ExternalSystemJdkUtil.isValidJdk(candidate.jdkPath) }
      ?.let { candidate ->
        JdkUtils.setProjectGradleJdk(project, gradleRootPath, candidate.jdkPath)
        resolution = candidate.generateResolutionMessage()
        notificationType = NotificationType.WARNING
      }
    if (resolution == null) {
      // Show a notification asking users to pick a different JDK
      val gradleVersion = GradleWrapper.find(project)?.gradleVersion?.let { GradleVersion.version(it) }
      if (gradleVersion != null) {
        val requiredJavaVersion = getCompatibleJdkVersionForGradle(gradleVersion)
        resolution = "This project uses Gradle ${gradleVersion.version} that requires JDK ${requiredJavaVersion.description}"
      }
      notificationType = NotificationType.ERROR
    }
    showRecoverResultGradleJdkNotification(
      errorResolution = resolution,
      notificationType = notificationType
    )
  }

  protected abstract val cause: InvalidGradleJdkCause

  protected open val recoveryJdkCandidates: List<RecoveryCandidate>
    get() {
      val jdkCandidates: MutableList<RecoveryCandidate> = mutableListOf()
      val gradleVersion = GradleWrapper.find(project)?.gradleVersion?.let { GradleVersion.version(it) }
      if (gradleVersion != null) {
        val requiredJavaVersion = getCompatibleJdkVersionForGradle(gradleVersion)
        // Add default and embedded if they are compatible with Gradle
        jdkCandidates.addIfSameJdkVersion(
          jdkName = AndroidBundle.message("gradle.default.jdk.name"),
          jdkPath = GradleDefaultJdkPathStore.jdkPath.orEmpty(),
          requiredJavaVersion
        )
        jdkCandidates.addIfSameJdkVersion(
          jdkName = AndroidBundle.message("gradle.embedded.jdk.name"),
          jdkPath = IdeSdks.getInstance().embeddedJdkPath.toString(),
          requiredJavaVersion
        )
        // Add candidates from JDK table that are compatible with Gradle
        val javaSdkType = ExternalSystemJdkUtil.getJavaSdkType()
        ProjectJdkTable.getInstance().getSdksOfType(javaSdkType)
          .filterNotNull()
          .filter { sdk ->
            val sdkVersion = JavaSdk.getInstance().getVersion(sdk)
            requiredJavaVersion == sdkVersion
          }
          .forEach { jdk ->
            jdkCandidates.add(RecoveryCandidate(
              jdkName = "JDK ${requiredJavaVersion.description}",
              jdkPath = jdk.homePath.orEmpty(),
              reason = " required by Gradle ${gradleVersion.version}")
            )
          }
      }
      else {
        // Try default and embedded when Gradle version cannot be found
        jdkCandidates.add(
          RecoveryCandidate(AndroidBundle.message("gradle.default.jdk.name"), GradleDefaultJdkPathStore.jdkPath.orEmpty(), "")
        )
        jdkCandidates.add(
          RecoveryCandidate(AndroidBundle.message("gradle.embedded.jdk.name"), IdeSdks.getInstance().embeddedJdkPath.toString(), "")
        )
      }
      return jdkCandidates.toImmutableList()
    }

  private fun showRecoverResultGradleJdkNotification(
    errorResolution: String?,
    notificationType: NotificationType
  ) {
    val title = GradleBundle.message("gradle.jvm.is.invalid")
    val messageBuilder = StringBuilder()
      .appendLine(cause.description)
    errorResolution?.let { messageBuilder.appendLine(it) }
    val quickFixes = listOfNotNull(SelectJdkFromFileSystemHyperlink.create(project, gradleRootPath))
    AndroidNotification.getInstance(project).showBalloon(title, messageBuilder.toString(), notificationType, *quickFixes.toTypedArray())
  }

  private fun getCompatibleJdkVersionForGradle(gradleVersion: GradleVersion): JavaSdkVersion =
    when {
      gradleVersion < GradleVersion.version("5.0") -> JavaSdkVersion.JDK_1_8
      gradleVersion < GradleVersion.version("7.3") -> JavaSdkVersion.JDK_11
      gradleVersion < GradleVersion.version("8.5") -> JavaSdkVersion.JDK_17
      else -> JavaSdkVersion.JDK_21
    }

  private fun MutableList<RecoveryCandidate>.addIfSameJdkVersion(jdkName: String, jdkPath: String, requiredJavaVersion: JavaSdkVersion) {
    val defaultJdkVersion = getJdkVersionFromPath(jdkPath)
    if (defaultJdkVersion == requiredJavaVersion) {
      add(RecoveryCandidate(jdkName, jdkPath, reason = ""))
    }
  }

  private fun getJdkVersionFromPath(jdkPath: String): JavaSdkVersion? {
    if (!ExternalSystemJdkUtil.isValidJdk(jdkPath)) {
      return null
    }
    val versionInfo = JdkVersionDetector.getInstance().detectJdkVersionInfo(jdkPath) ?: return null
    return JavaSdkVersion.fromJavaVersion(versionInfo.version)
  }

  protected data class RecoveryCandidate(val jdkName: String, val jdkPath: String, val reason: String) {
    fun generateResolutionMessage(): String {
      val jdkVersion = JavaSdk.getInstance().getVersionString(jdkPath) ?: "<unknown>"
      return AndroidBundle.message("project.sync.jdk.recovery.message", jdkName, jdkVersion, reason)
    }
  }
}
