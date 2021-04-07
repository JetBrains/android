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
package com.android.tools.idea.gradle.project.upgrade

import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult.SUCCESS
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.module.ModuleManager
import java.io.File

class AgpUpgradeAssistantIntegrationTest : AndroidGradleTestCase() {
  fun testUpgradeBasic40() {
    loadProject("upgrade/Projects/Basic40", null, "6.1.1", "4.0.0", "1.3.72")

    val appModule = ModuleManager.getInstance(project).modules.first { it.name == "Basic40.app" }
    assertThat(ProjectSystemService.getInstance(project).projectSystem.getSyncManager().getLastSyncResult()).isEqualTo(SUCCESS)
    assertThat(GradleFacet.getInstance(appModule)?.configuration?.LAST_SUCCESSFUL_SYNC_AGP_VERSION).isEqualTo("4.0.0")

    val processor = AgpUpgradeRefactoringProcessor(project, GradleVersion.parse("4.0.0"), GradleVersion.parse("4.1.0"))
    processor.run()

    assertThat(ProjectSystemService.getInstance(project).projectSystem.getSyncManager().getLastSyncResult()).isEqualTo(SUCCESS)
    assertThat(GradleFacet.getInstance(appModule)?.configuration?.LAST_SUCCESSFUL_SYNC_AGP_VERSION).isEqualTo("4.1.0")

    // We can't straightforwardly assert the exact contents of build.gradle, because the test infrastructure patches it with repository
    // declarations to make it work in the test environment
    val projectBuildFile = File(project.basePath, "build.gradle")
    val projectBuildFileLines = projectBuildFile.readLines().map { it.trim() }
    assertThat(projectBuildFileLines).contains("classpath 'com.android.tools.build:gradle:4.1.0'")
    assertThat(projectBuildFileLines).doesNotContain("google()")
    assertThat(projectBuildFileLines).contains("ext.kotlin_version = \"1.3.72\"")

    val appBuildFile = File(File(project.basePath, "app"), "build.gradle")
    assertThat(appBuildFile.readText()).isEqualTo(File(File(project.basePath, "app"), "build.gradle.expected").readText())

    val gradleWrapperFile = File(File(File(project.basePath, "gradle"), "wrapper"), "gradle-wrapper.properties")
    val distributionUrlLine = gradleWrapperFile.readLines().first { it.contains("distributionUrl") }
    assertThat(distributionUrlLine).contains("gradle-6.5-bin.zip")
  }
}