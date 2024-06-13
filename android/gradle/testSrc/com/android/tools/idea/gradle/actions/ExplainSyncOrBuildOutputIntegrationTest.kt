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
package com.android.tools.idea.gradle.actions

import com.android.SdkConstants.APP_PREFIX
import com.android.SdkConstants.FN_BUILD_GRADLE
import com.android.Version.ANDROID_GRADLE_PLUGIN_VERSION
import com.android.tools.idea.gradle.actions.ExplainSyncOrBuildOutput.Companion.getErrorShortDescription
import com.android.tools.idea.gradle.actions.ExplainSyncOrBuildOutput.Companion.getGradleFilesContext
import com.android.tools.idea.gradle.model.IdeSyncIssue
import com.android.tools.idea.gradle.project.build.events.AndroidSyncIssueFileEvent
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.PreparedTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.studiobot.AiExcludeService
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.OpenPreparedProjectOptions
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.EventResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ExplainSyncOrBuildOutputIntegrationTest {

  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule =
    AndroidProjectRule.withIntegrationTestEnvironment()

  @Test
  fun testAddDependencyAndSync() {
    val preparedProject: PreparedTestProject =
      projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    val buildFile = preparedProject.root.resolve(APP_PREFIX).resolve(FN_BUILD_GRADLE)
    buildFile.appendText("\nandroid.compileSdkVersion 341123")
    val eventResults = mutableListOf<EventResult>()
    preparedProject.open(
      updateOptions = { projectOptions: OpenPreparedProjectOptions ->
        projectOptions.copy(
          expectedSyncIssues = setOf(IdeSyncIssue.TYPE_MISSING_SDK_PACKAGE),
          overrideProjectGradleJdkPath = null,
          syncViewEventHandler = { event: BuildEvent ->
            when (event) {
              is AndroidSyncIssueFileEvent -> {
                eventResults += event.result
              }
              else -> {}
            }
          },
        )
      }
    ) {}

    val actual =
      getErrorShortDescription(eventResults[1])!!
        .trimIndent()
        .replace("\r\n", "\n")
        .replace("\\", "/")
    val absolutePath = buildFile.absolutePath.replace("\\", "/")
    assertEquals(
      """
                We recommend using a newer Android Gradle plugin to use compileSdk = 341123
                
                This Android Gradle plugin ($ANDROID_GRADLE_PLUGIN_VERSION) was tested up to compileSdk = 34.
                
                You are strongly encouraged to update your project to use a newer
                Android Gradle plugin that has been tested with compileSdk = 341123.
                
                If you are already using the latest version of the Android Gradle plugin,
                you may need to wait until a newer version with support for compileSdk = 341123 is available.
                
                For more information refer to the compatibility table:
                https://d.android.com/r/tools/api-level-support
                
                To suppress this warning, add/update
                    android.suppressUnsupportedCompileSdk=341123
                to this project's gradle.properties.
                Affected Modules: <a href="openFile:$absolutePath">app</a>
              """
        .trimIndent(),
      actual,
    )
  }

  @Test
  fun testGradleFilesContext() {
    val preparedProject: PreparedTestProject =
      projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    val (contextString, files) =
      preparedProject.open { getGradleFilesContext(it, AiExcludeService.FakeAiExcludeService()) }!!

    assertTrue(contextString.contains("Project Gradle files, separated by -------:"))
    assertEquals(3, files.size)
  }
}
