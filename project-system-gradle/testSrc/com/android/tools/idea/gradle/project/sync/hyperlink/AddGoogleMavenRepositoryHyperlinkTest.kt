/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.hyperlink

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoryModel
import com.android.tools.idea.gradle.dsl.api.repositories.UrlBasedRepositoryModel
import com.android.tools.idea.gradle.util.GradleVersions
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.replaceService
import java.io.IOException
import org.gradle.util.GradleVersion
import org.junit.Assume
import org.mockito.Mockito

/**
 * Tests for [AddGoogleMavenRepositoryHyperlink].
 */
class AddGoogleMavenRepositoryHyperlinkTest : AndroidGradleTestCase() {
  @Throws(Exception::class)
  fun testExecuteWithGradle4dot0() {
    // Check that quickfix adds google maven repository using method name when gradle version is 4.0 or higher
    verifyExecute("4.0")
  }

  // Check that quickfix adds google maven correctly when no build file is passed
  @Throws(Exception::class)
  fun testExecuteNullBuildFile() {
    // Prepare project and mock version
    prepareProjectForImportNoSync(TestProjectPaths.SIMPLE_APPLICATION)
    val project = getProject()

    // Make sure no repositories are listed
    removeRepositories(project)
    var buildModel = GradleBuildModel.get(project)
    Truth.assertThat(buildModel).isNotNull()

    // Generate hyperlink and execute quick fix
    val hyperlink =
      AddGoogleMavenRepositoryHyperlink(ImmutableList.of<VirtualFile?>(buildModel!!.getVirtualFile()),  /* no sync */false)
    hyperlink.execute(project)

    // Verify it added the repository
    buildModel = GradleBuildModel.get(project)
    Truth.assertThat(buildModel).isNotNull()
    var repositories = buildModel!!.repositories().repositories()
    Truth.assertThat(repositories).hasSize(1)

    // Verify it was added in buildscript
    repositories = buildModel.buildscript().repositories().repositories()
    Truth.assertThat(repositories).hasSize(1)
  }

  @Throws(IOException::class)
  private fun verifyExecute(version: String) {
    Assume.assumeTrue(version == "4.0")
    // Prepare project and mock version
    prepareProjectForImportNoSync(TestProjectPaths.SIMPLE_APPLICATION)
    val project = getProject()
    val spyVersions = Mockito.spy<GradleVersions>(GradleVersions.getInstance())
    ApplicationManager.getApplication().replaceService(
      GradleVersions::class.java,
      spyVersions,
      getTestRootDisposable()
    )
    Mockito.`when`<GradleVersion?>(spyVersions.getGradleVersion(project)).thenReturn(GradleVersion.version(version))

    // Make sure no repositories are listed
    removeRepositories(project)
    var buildModel = GradleBuildModel.get(project)
    Truth.assertThat(buildModel).isNotNull()

    // Generate hyperlink and execute quick fix
    val hyperlink =
      AddGoogleMavenRepositoryHyperlink(ImmutableList.of<VirtualFile?>(buildModel!!.getVirtualFile()),  /* no sync */false)
    hyperlink.execute(project)

    // Verify it added the repository
    buildModel = GradleBuildModel.get(project)
    Truth.assertThat(buildModel).isNotNull()
    var repositories = buildModel!!.repositories().repositories()
    Truth.assertThat(repositories).hasSize(1)
    verifyRepositoryForm(repositories.get(0), true)

    // Verify it was added in buildscript
    repositories = buildModel.buildscript().repositories().repositories()
    Truth.assertThat(repositories).hasSize(1)
    verifyRepositoryForm(repositories.get(0), true)
  }

  private fun removeRepositories(project: Project) {
    var buildModel = GradleBuildModel.get(project)
    Truth.assertThat(buildModel).isNotNull()
    buildModel!!.removeRepositoriesBlocks()
    buildModel.buildscript().removeRepositoriesBlocks()
    assertTrue(buildModel.isModified())
    WriteCommandAction.runWriteCommandAction(getProject(), Runnable { buildModel!!.applyChanges() })
    buildModel.reparse()
    assertFalse(buildModel.isModified())
    buildModel = GradleBuildModel.get(project)
    Truth.assertThat(buildModel).isNotNull()
    Truth.assertThat(buildModel!!.repositories().repositories()).hasSize(0)
    Truth.assertThat(buildModel.buildscript().repositories().repositories()).hasSize(0)
  }

  companion object {
    private fun verifyRepositoryForm(repository: RepositoryModel?, byMethod: Boolean) {
      assertInstanceOf<UrlBasedRepositoryModel?>(repository, UrlBasedRepositoryModel::class.java)
      val urlRepository = repository as UrlBasedRepositoryModel
      if (byMethod) {
        assertNull("url", urlRepository.url().getPsiElement())
      } else {
        assertNotNull("url", urlRepository.url().getPsiElement())
      }
    }
  }
}

