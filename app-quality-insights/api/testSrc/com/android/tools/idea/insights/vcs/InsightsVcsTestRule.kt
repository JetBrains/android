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
package com.android.tools.idea.insights.vcs

import com.android.flags.junit.FlagRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.HeavyTestHelper.getOrCreateProjectBaseDir
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.vcsUtil.VcsUtil
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.Description
import org.junit.runners.model.Statement

class InsightsVcsTestRule(private val projectRule: AndroidProjectRule) : ExternalResource() {
  lateinit var vcs: MockAbstractVcs
  lateinit var projectLevelVcsManager: ProjectLevelVcsManagerImpl
  lateinit var projectBaseDir: VirtualFile
  lateinit var repositoryManager: VcsRepositoryManager

  private val project: Project
    get() = projectRule.project

  private val fixture: CodeInsightTestFixture
    get() = projectRule.fixture

  private val testRootDisposable: Disposable
    get() = projectRule.testRootDisposable

  override fun before() {
    assertThat((project as? ProjectImpl)?.isLight).isFalse()

    projectBaseDir = getOrCreateProjectBaseDir()
    projectLevelVcsManager =
      ProjectLevelVcsManager.getInstance(project) as ProjectLevelVcsManagerImpl
    projectLevelVcsManager.waitForInitialized()

    vcs = MockAbstractVcs(project, "MOCK")
    projectLevelVcsManager.registerVcs(vcs)

    ExtensionTestUtil.maskExtensions(
      VcsRepositoryManager.EP_NAME,
      listOf(FakeVcsRepositoryCreator(vcs)),
      testRootDisposable,
      false,
    )
    ExtensionTestUtil.maskExtensions(
      VcsForAppInsights.EP_NAME,
      listOf(FakeVcsForAppInsights()),
      testRootDisposable,
      false,
    )

    repositoryManager = VcsRepositoryManager.getInstance(project)
    addNewMappingToRootStructure(projectBaseDir.path, vcs)
  }

  override fun after() {
    clearUpMappingToRootStructure()
    projectLevelVcsManager.unregisterVcs(vcs)
  }

  override fun apply(base: Statement?, description: Description?): Statement {
    return RuleChain.outerRule(FlagRule(StudioFlags.APP_INSIGHTS_VCS_SUPPORT, true))
      .apply(super.apply(base, description), description)
  }

  fun addNewMappingToRootStructure(path: String, vcs: AbstractVcs): VirtualFile {
    val rootVFile = getOrCreateRootVFile(path)
    projectLevelVcsManager.directoryMappings =
      VcsUtil.addMapping(projectLevelVcsManager.directoryMappings, rootVFile.path, vcs.name)

    repositoryManager.waitForAsyncTaskCompletion()

    val newRepo = repositoryManager.repositories.find { it.root == rootVFile && it.vcs == vcs }
    assertThat(newRepo).isNotNull()

    return rootVFile
  }

  fun clearUpMappingToRootStructure() {
    projectLevelVcsManager.directoryMappings = emptyList()
    repositoryManager.waitForAsyncTaskCompletion()
  }

  fun createChangeForPath(path: String, beforeRevision: String, afterRevision: String): Change {
    val file: VirtualFile = VfsTestUtil.createFile(projectBaseDir, path)
    val filePath: FilePath = file.toVcsFilePath()
    val beforeContentRevision: ContentRevision =
      FakeContentRevision(filePath, beforeRevision) { "before" }
    val afterContentRevision: ContentRevision =
      FakeContentRevision(filePath, afterRevision) { "after" }
    return Change(beforeContentRevision, afterContentRevision)
  }

  private fun getOrCreateRootVFile(path: String): VirtualFile {
    return if (path == projectBaseDir.path) projectBaseDir
    else fixture.tempDirFixture.findOrCreateDir(path)
  }

  private fun getOrCreateProjectBaseDir(): VirtualFile {
    return fixture.tempDirFixture.findOrCreateDir("")
  }
}
