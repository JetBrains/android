/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.tools.idea.gradle.project.sync.idea.issues.BuildIssueComposer
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.issue.BuildIssue
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler.getRootCauseAndLocation
import java.util.function.Consumer

private const val DEVICE_TEST_SOURCE_SET = "KotlinSourceSet with name 'androidTestOnDevice' not found"
private const val HOST_TEST_SOURCE_SET = "KotlinSourceSet with name 'androidTestOnJvm' not found"

class UnknownMultiplatformTestSourceSetChecker: GradleIssueChecker {
  override fun check(issueData: GradleIssueData): BuildIssue? {
    val rootCause = getRootCauseAndLocation(issueData.error).first
    val message = rootCause.message ?: return null
    if (!message.contains(DEVICE_TEST_SOURCE_SET) && !message.contains(HOST_TEST_SOURCE_SET)) return null

    return createBuildIssue(message)
  }

  override fun consumeBuildOutputFailureMessage(message: String,
                                                failureCause: String,
                                                stacktrace: String?,
                                                location: FilePosition?,
                                                parentEventId: Any,
                                                messageConsumer: Consumer<in BuildEvent>): Boolean {
    return failureCause.contains(DEVICE_TEST_SOURCE_SET) || failureCause.contains(HOST_TEST_SOURCE_SET)
  }

  private fun createBuildIssue(message: String) =
    BuildIssueComposer(message, "Unknown kotlin source set issue").apply {
      addDescriptionOnNewLine("The default source sets for Android KMP target were renamed in AGP 8.9.0-alpha03.")
      addDescriptionOnNewLine("In order to migrate you need to change:")
      addDescriptionOnNewLine("sourceSets.getByName(\"androidTestOnDevice\") -> sourceSets.getByName(\"androidDeviceTest\")")
      addDescriptionOnNewLine("sourceSets.getByName(\"androidTestOnJvm\") -> sourceSets.getByName(\"androidHostTest\")")
      startNewParagraph()
    }.composeBuildIssue()
}