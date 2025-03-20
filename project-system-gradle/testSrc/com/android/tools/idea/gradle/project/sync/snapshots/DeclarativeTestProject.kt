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
package com.android.tools.idea.gradle.project.sync.snapshots

import com.android.tools.idea.gradle.feature.flags.DeclarativeStudioSupport
import com.android.tools.idea.gradle.dcl.lang.ide.DeclarativeIdeSupport
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.TestProjectToSnapshotPaths
import com.intellij.openapi.project.Project
import com.intellij.util.PathUtil
import org.jetbrains.android.AndroidTestBase
import java.io.File

enum class DeclarativeTestProject(
  override val template: String,
  override val pathToOpen: String = "",
  override val testName: String? = null,
  override val isCompatibleWith: (AgpVersionSoftwareEnvironmentDescriptor) -> Boolean = { true },
  override val autoMigratePackageAttribute: Boolean = true,
  override val setup: () -> () -> Unit = {
    DeclarativeStudioSupport.override(true)
    DeclarativeIdeSupport.override(true)

    fun() {
      DeclarativeStudioSupport.clearOverride()
      DeclarativeIdeSupport.clearOverride()
    }
  },
  override val patch: AgpVersionSoftwareEnvironmentDescriptor.(projectRoot: File) -> Unit = {},
  override val expectedSyncIssues: Set<Int> = emptySet(),
  override val verifyOpened: ((Project) -> Unit)? = null,
  override val switchVariant: TemplateBasedTestProject.VariantSelection? = null
) : TemplateBasedTestProject {

  DECLARATIVE_ANDROID(
    TestProjectToSnapshotPaths.DECLARATIVE_ANDROID,
    isCompatibleWith = { it == AgpVersionSoftwareEnvironmentDescriptor.AGP_DECLARATIVE_GRADLE_SNAPSHOT },
  );

  override fun getTestDataDirectoryWorkspaceRelativePath(): String = "tools/adt/idea/android/testData/snapshots"

  override fun getAdditionalRepos(): Collection<File> =
    listOf(File(AndroidTestBase.getTestDataPath(), PathUtil.toSystemDependentName(TestProjectPaths.PSD_SAMPLE_REPO)))
}