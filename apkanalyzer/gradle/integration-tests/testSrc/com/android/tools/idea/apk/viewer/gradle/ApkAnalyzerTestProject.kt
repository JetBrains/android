/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.apk.viewer.gradle

import com.android.tools.idea.gradle.project.sync.snapshots.TemplateBasedTestProject
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.intellij.openapi.project.Project
import java.io.File


enum class ApkAnalyzerTestProject(
  override val template: String,
  override val pathToOpen: String = "",
  override val testName: String? = null,
  override val isCompatibleWith: (AgpVersionSoftwareEnvironmentDescriptor) -> Boolean = { true },
  override val autoMigratePackageAttribute: Boolean = true,
  override val setup: () -> () -> Unit = { {} },
  override val patch: AgpVersionSoftwareEnvironmentDescriptor.(projectRoot: File) -> Unit = {},
  override val expectedSyncIssues: Set<Int> = emptySet(),
  override val verifyOpened: ((Project) -> Unit)? = null,
) : TemplateBasedTestProject {
  SIMPLE_APPLICATION("simpleApplication"),
  ;

  override fun getTestDataDirectoryWorkspaceRelativePath(): String =
    "tools/adt/idea/apkanalyzer/gradle/integration-tests/projects"

  override fun getAdditionalRepos(): Collection<File> = listOf()

}
