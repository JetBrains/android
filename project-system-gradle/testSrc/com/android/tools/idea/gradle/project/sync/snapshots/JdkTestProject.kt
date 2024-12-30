/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.idea.gradle.project.sync.model.GradleRoot
import com.android.tools.idea.gradle.project.sync.utils.ProjectJdkUtils
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.TestProjectToSnapshotPaths
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.Project.DIRECTORY_STORE_FOLDER
import com.intellij.util.PathUtil
import org.jetbrains.android.AndroidTestBase
import java.io.File
import java.nio.file.Files

sealed class JdkTestProject(
  override val template: String,
  override val pathToOpen: String = "",
  override val testName: String? = null,
  override val isCompatibleWith: (AgpVersionSoftwareEnvironmentDescriptor) -> Boolean = { true },
  override val autoMigratePackageAttribute: Boolean = true,
  override val setup: () -> () -> Unit = { {} },
  override val patch: AgpVersionSoftwareEnvironmentDescriptor.(projectRoot: File) -> Unit = {},
  override val expectedSyncIssues: Set<Int> = emptySet(),
  override val verifyOpened: ((Project) -> Unit)? = null,
  override val switchVariant: TemplateBasedTestProject.VariantSelection? = null,
  val agpVersion: AgpVersionSoftwareEnvironmentDescriptor
) : TemplateBasedTestProject {

  class SimpleApplicationWithoutIdea(
    agpVersion: AgpVersionSoftwareEnvironmentDescriptor = AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT
  ) : JdkTestProject(
    agpVersion = agpVersion,
    template = TestProjectToSnapshotPaths.SIMPLE_APPLICATION
  )

  class SimpleApplication(
    agpVersion: AgpVersionSoftwareEnvironmentDescriptor = AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT,
    ideaGradleJdk: String? = null,
    ideaProjectJdk: String? = null,
    gradleLocalJavaHome: String? = null,
    gradlePropertiesJavaHome: String? = null
  ) : JdkTestProject(
    agpVersion = agpVersion,
    template = TestProjectToSnapshotPaths.SIMPLE_APPLICATION,
    patch = { projectRoot ->
      Files.createDirectory(projectRoot.resolve(DIRECTORY_STORE_FOLDER).toPath())
      ideaGradleJdk?.let {
        ProjectJdkUtils.setProjectIdeaGradleJdk(
          projectRoot,
          listOf(GradleRoot(ideaGradleJdk = it, modulesPath = listOf("app")))
        )
      }
      ideaProjectJdk?.let {
        ProjectJdkUtils.setProjectIdeaMiscJdk(projectRoot, it)
      }
      gradleLocalJavaHome?.let {
        ProjectJdkUtils.setProjectGradleLocalJavaHome(projectRoot, it)
      }
      gradlePropertiesJavaHome?.let {
        ProjectJdkUtils.setProjectGradlePropertiesJavaHome(projectRoot, it)
      }
    }
  )

  class SimpleApplicationMultipleRoots(
    agpVersion: AgpVersionSoftwareEnvironmentDescriptor = AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT,
    roots: List<GradleRoot>,
    ideaProjectJdk: String? = null
  ) : JdkTestProject(
    agpVersion = agpVersion,
    template = TestProjectToSnapshotPaths.SIMPLE_APPLICATION,
    patch = { projectRoot ->
      Files.createDirectory(projectRoot.resolve(DIRECTORY_STORE_FOLDER).toPath())
      cloneProjectRootIntoMultipleGradleRoots(
        projectRoot,
        gradleRoots = roots,
        configGradleRoot = { gradleRootFile, gradleRoot ->
          gradleRoot.gradleLocalJavaHome?.let {
            ProjectJdkUtils.setProjectGradleLocalJavaHome(gradleRootFile, it)
          }
        },
        configProjectRoot = {
          ProjectJdkUtils.setProjectIdeaGradleJdk(projectRoot, roots)
          ideaProjectJdk?.let {
            ProjectJdkUtils.setProjectIdeaMiscJdk(projectRoot, ideaProjectJdk)
          }
        })
    }
  )

  override val name: String = this::class.simpleName.orEmpty()

  override fun getTestDataDirectoryWorkspaceRelativePath() = "tools/adt/idea/android/testData/snapshots"

  override fun getAdditionalRepos() =
    listOf(File(AndroidTestBase.getTestDataPath(), PathUtil.toSystemDependentName(TestProjectPaths.PSD_SAMPLE_REPO)))
}