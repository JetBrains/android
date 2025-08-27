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
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.util.GradleVersions
import com.android.tools.idea.gradle.util.GradleVersions.getInstance
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.AndroidGradleTests
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.testing.resolve
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import java.io.File
import org.gradle.util.GradleVersion.version
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

/**
 * Tests for [AddGoogleMavenRepositoryHyperlink].
 */
@RunsInEdt
class AddGoogleMavenRepositoryHyperlinkTest {
  @get:Rule
  val projectRule = AndroidGradleProjectRule().onEdt()
  val project by lazy { projectRule.project }
  val fixture by lazy { projectRule.fixture }
  val projectFolderPath by lazy { File(project.basePath!!) }

  @Before
  fun setup() {
    // Prepare project
    AndroidGradleTests.prepareProjectForImportCore(AndroidCoreTestProject.SIMPLE_APPLICATION.templateAbsolutePath, projectFolderPath) { root ->
      AndroidGradleTests.defaultPatchPreparedProject(root, AgpVersionSoftwareEnvironmentDescriptor.AGP_LATEST.resolve(), null, false)
    }
  }

  // Check that quickfix adds google maven repository using method name when gradle version is 4.0 or higher
  @Test
  fun testExecuteWithGradle4dot0() {
    // Mock version
    val spyVersions = Mockito.spy(getInstance())
    getApplication().replaceService(GradleVersions::class.java, spyVersions, fixture.testRootDisposable)
    Mockito.`when`(spyVersions.getGradleVersion(project)).thenReturn(version("4.0"))

    val buildModel = GradleBuildModel.get(project)
    assertThat(buildModel).isNotNull()
    // Make sure no repositories are listed
    removeRepositories(buildModel!!)
    // Generate hyperlink and execute quick fix
    val hyperlink = AddGoogleMavenRepositoryHyperlink(listOf(buildModel.getVirtualFile()), false)
    hyperlink.execute(project)
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    buildModel.reparse()

    // Verify it added the repository
    buildModel.repositories().repositories().let { repositories ->
      assertThat(repositories).hasSize(1)
      verifyRepositoryForm(repositories[0], true)
    }
    // Verify it was added in buildscript
    buildModel.buildscript().repositories().repositories().let { repositories ->
      assertThat(repositories).hasSize(1)
      verifyRepositoryForm(repositories[0], true)
    }
  }

  // Check that quickfix adds google maven correctly when no build file is passed
  @Test
  fun testExecuteNullBuildFile() {
    // Make sure no repositories are listed
    val buildModel = GradleBuildModel.get(project)
    assertThat(buildModel).isNotNull()
    removeRepositories(buildModel!!)

    // Generate hyperlink and execute quick fix
    val hyperlink = AddGoogleMavenRepositoryHyperlink(listOf(buildModel.getVirtualFile()), false)
    hyperlink.execute(project)
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    buildModel.reparse()

    // Verify it added the repository
    assertThat(buildModel.repositories().repositories()).hasSize(1)
    // Verify it was added in buildscript
    assertThat(buildModel.buildscript().repositories().repositories()).hasSize(1)
  }

  private fun removeRepositories(buildModel: GradleBuildModel) {
    assertThat(buildModel.repositories().repositories()).isNotEmpty()
    assertThat(buildModel.buildscript().repositories().repositories()).isNotEmpty()
    buildModel.removeRepositoriesBlocks()
    buildModel.buildscript().removeRepositoriesBlocks()
    assertThat(buildModel.isModified()).isTrue()
    WriteCommandAction.runWriteCommandAction(project) { buildModel.applyChanges() }
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    buildModel.reparse()
    assertThat(buildModel.isModified()).isFalse()
    assertThat(buildModel.repositories().repositories()).hasSize(0)
    assertThat(buildModel.buildscript().repositories().repositories()).hasSize(0)

  }

  companion object {
    private fun verifyRepositoryForm(repository: RepositoryModel?, byMethod: Boolean) {
      assertThat(repository).isInstanceOf(UrlBasedRepositoryModel::class.java)
      val urlRepository = repository as UrlBasedRepositoryModel
      if (byMethod) {
        assertThat(urlRepository.url().getPsiElement()).named("url").isNull()
      } else {
        assertThat(urlRepository.url().getPsiElement()).named("url").isNotNull()
      }
    }
  }
}

