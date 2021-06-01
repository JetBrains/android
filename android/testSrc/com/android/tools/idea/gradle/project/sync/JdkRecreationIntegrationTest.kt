/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.AndroidStudioGradleInstallationManager
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult.SUCCESS
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.GradleIntegrationTest
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.prepareGradleProject
import com.google.common.truth.Expect
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.io.FileUtil
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import java.io.File

@Suppress("DEPRECATION")
class JdkRecreationIntegrationTest: GradleIntegrationTest {
  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels().onEdt()

  @get:Rule
  val expect = Expect.createAndEnableStackTrace()!!

  @get:Rule
  val testName = TestName()

  override fun getName(): String = testName.methodName

  override fun getBaseTestPath(): String = projectRule.fixture.tempDirPath

  override fun getTestDataDirectoryWorkspaceRelativePath(): String = TestProjectPaths.TEST_DATA_PATH

  override fun getAdditionalRepos(): Collection<File> = listOf()

  @Suppress("UnstableApiUsage")
  @Test
  fun `Corrupted Jdk is recreated after sync`() {
    // Set flag
    StudioFlags.GRADLE_SYNC_RECREATE_JDK.override(true)

    try {

      // Create a project with modified JDK
      val project1File = prepareGradleProject(TestProjectPaths.SIMPLE_APPLICATION, "project_1")
      var projectJdk: ProjectJdkImpl? = null
      openPreparedProject("project_1") { project ->
        assertThat(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SUCCESS)
        val basePath = project.basePath
        assertThat(basePath).isNotNull()
        assertThat(basePath).isNotEmpty()

        val gradleManager = AndroidStudioGradleInstallationManager.getInstance()
        projectJdk = gradleManager.getGradleJdk(project, basePath!!) as ProjectJdkImpl
      }

      // Corrupt JDK by removing a class root
      assertThat(projectJdk).isNotNull()
      val corruptedJdk = projectJdk!!.clone()
      val roots = corruptedJdk.getRoots(OrderRootType.CLASSES)
      assertThat(roots).isNotEmpty()
      val originalSize = roots.size
      corruptedJdk.removeRoot(roots[0], OrderRootType.CLASSES)
      WriteAction.runAndWait<RuntimeException> {
        ProjectJdkTable.getInstance().updateJdk(projectJdk!!, corruptedJdk)
      }

      // Verify corrupted JDK has a class root less
      assertThat(projectJdk!!.getRoots(OrderRootType.CLASSES)).hasLength(originalSize - 1)

      // Copy project1
      val copiedProjectPath = File(FileUtil.toSystemDependentName(getBaseTestPath() + "/project_2"))
      FileUtil.copyDir(project1File, copiedProjectPath)

      // Open copied project and confirm that the corrupted JDK is fixed
      openPreparedProject("project_2") { project ->
        assertThat(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SUCCESS)

        val basePath = project.basePath
        val gradleManager = AndroidStudioGradleInstallationManager.getInstance()
        val project2Jdk = gradleManager.getGradleJdk(project, basePath!!) as ProjectJdkImpl
        assertThat(project2Jdk.getRoots(OrderRootType.CLASSES)).hasLength(originalSize)
      }
    }
    finally {
      StudioFlags.GRADLE_SYNC_RECREATE_JDK.clearOverride()
    }
  }
}