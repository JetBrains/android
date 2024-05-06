/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.errors

import com.android.tools.idea.concurrency.executeOnPooledThread
import com.android.tools.idea.gradle.project.sync.idea.issues.BuildIssueComposer
import com.android.tools.idea.gradle.project.sync.issues.SyncFailureUsageReporter
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenGradleJdkSettingsQuickfix
import com.android.tools.idea.gradle.project.sync.quickFixes.SyncProjectRefreshingDependenciesQuickFix
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil
import com.android.tools.idea.sdk.IdeSdks
import com.google.common.base.Splitter
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.CANNOT_BE_CAST_TO
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.CLASS_NOT_FOUND
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.METHOD_NOT_FOUND
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.impl.SdkVersionUtil
import com.intellij.openapi.ui.Messages
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import java.util.regex.Pattern

class ClassLoadingIssueChecker: GradleIssueChecker {
  private val CLASS_NOT_FOUND_PATTERN = Pattern.compile("(.+) not found.")
  private val NO_SUCH_METHOD_TRACE_PATTERN = Pattern.compile("Caused by: java.lang.NoSuchMethodError:(.*)")
  private val CLASS_NOT_FOUND_TRACE_PATTERN = Pattern.compile("Caused by: java.lang.ClassNotFoundException:(.*)")
  private val CANNOT_BE_CAST_TO_EXCEPTION = "cannot be cast to"

  override fun check(issueData: GradleIssueData): BuildIssue? {
    val rootCause = GradleExecutionErrorHandler.getRootCauseAndLocation(issueData.error).first
    val message = rootCause.message ?: ""

    val buildIssueComposer = BuildIssueComposer(getExceptionMessage(rootCause, message, issueData.projectPath) ?: return null)

    val syncProjectQuickFix = SyncProjectRefreshingDependenciesQuickFix()
    val stopGradleDaemonQuickFix = StopGradleDaemonQuickFix()

    val jdk7Hint = buildString {
      val jdk = IdeSdks.getInstance().jdk ?: return@buildString
      val jdkHomePath = jdk.homePath
      val jdkVersion = if (jdkHomePath != null) SdkVersionUtil.getJdkVersionInfo(jdkHomePath) else null

      if (JavaSdkVersion.JDK_1_7 != JavaSdk.getInstance().getVersion(jdk)) return@buildString
      // Otherwise, we are using Jdk7.
      when (jdkVersion) {
        null -> append("Some versions of JDK 1.7 (e.g. 1.7.0_10) may cause class loading errors in Gradle. \n" +
                       "Please update to a newer version (e.g. 1.7.0_67).")
        else -> append("You are using JDK version '${jdkVersion.version.toFeatureMinorUpdateString()}'.")
      }
    }

    buildIssueComposer.addDescription(Splitter.on("\n").omitEmptyStrings().trimResults().splitToList(message)[0])
    if (jdk7Hint.isNotEmpty()) {
      buildIssueComposer.apply {
        addDescription("Possible causes for this unexpected error include:")
        addDescription(jdk7Hint)
        addQuickFix("Open JDK Settings", OpenGradleJdkSettingsQuickfix())
      }
    }
    buildIssueComposer.apply {
      addDescription("Gradle's dependency cache may be corrupt (this sometimes occurs after a network connection timeout.)")
      addQuickFix(syncProjectQuickFix.linkText, syncProjectQuickFix)
      addDescription("The state of a Gradle build process (daemon) may be corrupt. Stopping all Gradle daemons may solve this problem.")
      when (ApplicationManager.getApplication().isRestartCapable) {
        true -> addQuickFix("Stop Gradle build processes (requires restart)", stopGradleDaemonQuickFix)
        false -> addQuickFix("Open Gradle Daemon documentation", stopGradleDaemonQuickFix)
      }
      addDescription("Your project may be using a third-party plugin which is not compatible with the other " +
                     "plugins in the project or the version of Gradle requested by the project.\n\n" +
                     "In the case of corrupt Gradle processes, you can also try closing the IDE and then killing all Java processes.")
    }

    return buildIssueComposer.composeBuildIssue()
  }

  override fun consumeBuildOutputFailureMessage(message: String,
                                                failureCause: String,
                                                stacktrace: String?,
                                                location: FilePosition?,
                                                parentEventId: Any,
                                                messageConsumer: Consumer<in BuildEvent>): Boolean {
    if (stacktrace != null && NO_SUCH_METHOD_TRACE_PATTERN.matcher(stacktrace).find()) return true
    if (stacktrace != null && CLASS_NOT_FOUND_TRACE_PATTERN.matcher(stacktrace).find()) return true
    if (failureCause.contains(CANNOT_BE_CAST_TO_EXCEPTION)) return true
    return false
  }

  private fun getExceptionMessage(exception: Throwable, message: String, projectPath: String): String? {
    when (exception) {
      is ClassNotFoundException -> {
        var className = message
        val matcher = CLASS_NOT_FOUND_PATTERN.matcher(className)
        if (matcher.matches()) {
          className = matcher.group(1)
        }
        // Log metrics.
        SyncFailureUsageReporter.getInstance().collectFailure(projectPath, CLASS_NOT_FOUND)
        return "Unable to load class '${className}'"
      }
      is NoSuchMethodError -> {
        // Log metrics.
        SyncFailureUsageReporter.getInstance().collectFailure(projectPath, METHOD_NOT_FOUND)
        return "Unable to find method '$message'"
      }
      else -> {
        if (message.contains(CANNOT_BE_CAST_TO_EXCEPTION)) {
          // Log metrics.
          SyncFailureUsageReporter.getInstance().collectFailure(projectPath, CANNOT_BE_CAST_TO)
          return message
        }
      }
    }
    return null
  }
}

class StopGradleDaemonQuickFix : BuildIssueQuickFix {
  override val id = "stop.gradle.daemons"

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    val future = CompletableFuture<Any>()

    if (ApplicationManager.getApplication().isRestartCapable) {
      val title = "Stop Gradle Daemons"
      val message = """
          Stopping all Gradle daemons will terminate any running Gradle builds (e.g. from the command line).
          This action will also restart the IDE.
          Do you want to continue?
          """.trimIndent()
      val answer = Messages.showYesNoDialog(project, message, title,  Messages.getQuestionIcon())
      if (answer == Messages.YES) {
        // Run the action asynchronously from the pooled thread to avoid causing unnecessary UI freezes while waiting for Gradle to close.
        executeOnPooledThread {
          GradleProjectSystemUtil.stopAllGradleDaemonsAndRestart()
        }
        future.complete(null)
      }
    }
    else {
      invokeLater {
        BrowserUtil.browse("http://www.gradle.org/docs/current/userguide/gradle_daemon.html")
        future.complete(null)
      }
    }

    return future
  }
}