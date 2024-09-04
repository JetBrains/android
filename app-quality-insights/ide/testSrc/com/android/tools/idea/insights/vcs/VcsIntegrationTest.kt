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

import com.android.testutils.MockitoKt.mock
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.insights.AppVcsInfo
import com.android.tools.idea.insights.InsightsTestProject
import com.android.tools.idea.insights.PROJECT_ROOT_PREFIX
import com.android.tools.idea.insights.RepoInfo
import com.android.tools.idea.insights.VCS_CATEGORY
import com.android.tools.idea.insights.ui.cleanUpListenersFromEditorMouseHoverPopupManager
import com.android.tools.idea.insights.ui.initConsoleWithFilters
import com.android.tools.idea.insights.ui.printAndHighlight
import com.android.tools.idea.insights.ui.vcs.ContextDataForDiff
import com.android.tools.idea.insights.ui.vcs.InsightsDiffVirtualFile
import com.android.tools.idea.insights.ui.vcs.VCS_INFO_OF_SELECTED_CRASH
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.OpenPreparedProjectOptions
import com.android.tools.idea.testing.withoutKtsRelatedIndexing
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.GitRepositoryInitializer
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.runInEdtAndWait
import git4idea.GitUtil
import git4idea.commands.GitCommand
import git4idea.repo.GitRepository
import org.junit.Rule
import org.junit.Test

class VcsIntegrationTest {
  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule =
    AndroidProjectRule.withIntegrationTestEnvironment()

  @Test
  fun `test git single repo project`() {
    val preparedProject = projectRule.prepareTestProject(InsightsTestProject.SIMPLE_APP)
    preparedProject.open(updateOptions = OpenPreparedProjectOptions::withoutKtsRelatedIndexing) {
      val projectBaseDir = project.guessProjectDir()!!
      val gitRepository = initGitRepoAndCommit(project, root = projectBaseDir)

      runInEdtAndWait {
        val console = initConsoleWithFilters(project, mock())

        try {
          console.putClientProperty(VCS_INFO_OF_SELECTED_CRASH, buildAppVcsInfo(gitRepository))

          // Ensure our class file exists
          val targetFile =
            projectBaseDir.findFileByRelativePath(
              "app/src/main/java/com/example/myapplication/MyActivity.java"
            )!!

          // Prepare: print and apply filters
          console.printAndHighlight(
            """
              java.lang.RuntimeException:
                  com.example.myapplication.MyActivity.onCreate(MyActivity.java:14)
            """
              .trimIndent()
          )

          // Act: click on the inlay
          console.editor.caretModel.moveToOffset(console.editor.document.textLength - 1)
          val position = console.editor.caretModel.logicalPosition
          val point = console.editor.logicalPositionToXY(position)

          val mouse = FakeUi(root = console.editor.contentComponent).mouse

          // We have a sequence of inlays (", " and "show diff") and we want to click on the second
          // one. "16" is the width of "," inlay.
          mouse.click(point.x + 16, point.y)

          // `InlayEditorMouseMotionListener` can hold `activeContainer` (i.e. editor in
          // `WithCursorOnHoverPresentation`) and thus it causes leaks in tests. So we explicitly
          // move mouse away to release `activeContainer` to make sure the containing editor is
          // released.
          mouse.moveTo(0, 0)

          // Assert: a diff view is brought up on the inlay click.
          val fileEditorManager = FileEditorManagerEx.getInstanceEx(project)
          val openedFile = fileEditorManager.openFiles.first()
          with(openedFile as InsightsDiffVirtualFile) {
            val expectedContext =
              buildContextDataForDiff(targetFile, lineNumber = 14, gitRepository)
            assertThat(provider.insightsContext).isEqualTo(expectedContext)
          }
        } finally {
          Disposer.dispose(console)
          cleanUpListenersFromEditorMouseHoverPopupManager()
        }
      }
    }
  }

  private fun initGitRepoAndCommit(project: Project, root: VirtualFile): GitRepository {
    GitRepositoryInitializer.getInstance()!!.initRepository(project, root, true)

    git(project, GitCommand.CONFIG, listOf("user.name", "someone"), root)
    git(project, GitCommand.CONFIG, listOf("user.email", "someone@google.com"), root)
    git(project, GitCommand.COMMIT, listOf("-m", "initial"), root)

    val repositories = GitUtil.getRepositoryManager(project).repositories
    GitUtil.updateRepositories(repositories)

    return repositories.single()
  }

  private fun buildAppVcsInfo(repo: GitRepository): AppVcsInfo {
    return AppVcsInfo.ValidInfo(
      listOf(
        RepoInfo(
          vcsKey = VCS_CATEGORY.GIT,
          rootPath = PROJECT_ROOT_PREFIX,
          revision = repo.currentRevision.toString(),
        )
      )
    )
  }

  private fun buildContextDataForDiff(
    targetFile: VirtualFile,
    lineNumber: Int,
    repo: GitRepository,
  ): ContextDataForDiff {
    return ContextDataForDiff(
      vcsKey = VCS_CATEGORY.GIT,
      revision = repo.currentRevision.toString(),
      filePath = targetFile.toVcsFilePath(),
      lineNumber = lineNumber,
      origin = null,
    )
  }
}
