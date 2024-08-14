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
package com.android.tools.idea.gradle.project.sync.errors.integration

import com.android.tools.idea.gradle.project.sync.errors.AddGoogleMavenRepositoryQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenPluginBuildFileQuickFix
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.replaceContent
import com.android.tools.idea.util.toIoFile
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.testFramework.PlatformTestUtil
import org.junit.Test

class MissingAndroidPluginFailureTest : AbstractIssueCheckerIntegrationTest() {

  @Test
  fun missingAgpArtifactWithOldPluginApplyDsl() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)

    val buildGradle = preparedProject.root.resolve("build.gradle")
    buildGradle.replaceContent {
      it.replace("maven {", "maven {\ncontent { excludeModule(\"com.android.tools.build\", \"gradle\") }")
    }

    runSyncAndCheckBuildIssueFailure(
      preparedProject = preparedProject,
      verifyBuildIssue = { project, buildIssue ->
        expect.that(buildIssue).isNotNull()
        expect.that(buildIssue.title).isEqualTo("Gradle Sync issues.")
        expect.that(buildIssue.description).startsWith("Could not find com.android.tools.build:gradle:")
        expect.that(buildIssue.quickFixes).hasSize(2)
        buildIssue.quickFixes[0].let {
          expect.that(buildIssue.description).contains("<a href=\"${it.id}\">Add google Maven repository and sync project</a>")
          expect.that(it).isInstanceOf(AddGoogleMavenRepositoryQuickFix::class.java)
        }
        buildIssue.quickFixes[1].let {
          expect.that(buildIssue.description).contains("<a href=\"${it.id}\">Open File</a>")
          expect.that(it).isInstanceOf(OpenPluginBuildFileQuickFix::class.java)
          PlatformTestUtil.waitForFuture(it.runQuickFix(project, SimpleDataContext.getProjectContext(project)))
          expect.that(FileEditorManagerEx.getInstanceEx(project).currentFile?.toIoFile()).isEqualTo(buildGradle)
        }
      },
      expectedFailureReported = AndroidStudioEvent.GradleSyncFailure.ANDROID_BUILD_ISSUE_CREATED_UNKNOWN_FAILURE,
      expectedPhasesReported = """
        FAILURE : SYNC_TOTAL/GRADLE_CONFIGURE_ROOT_BUILD
        FAILURE : SYNC_TOTAL
      """.trimIndent()
    )
  }
}