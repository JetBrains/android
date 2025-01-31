/*
 * Copyright 2024 The Android Open Source Project
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
package com.android.tools.idea.backup

import com.android.tools.idea.testing.WaitForIndexRule
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.TemporaryDirectory
import java.nio.file.Path
import kotlin.io.path.deleteExisting
import kotlin.io.path.pathString
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Tests for [BackupFileHistory] */
@RunWith(JUnit4::class)
class BackupFileHistoryTest {
  private val projectRule = ProjectRule()
  private val temporaryFolder =
    TemporaryFolder(TemporaryDirectory.generateTemporaryPath("").parent.toFile())

  @get:Rule val rule = RuleChain(projectRule, WaitForIndexRule(projectRule), temporaryFolder)

  private val project
    get() = projectRule.project

  @Test
  fun getHistory() {
    val backupFileHistory = BackupFileHistory(project)
    val files =
      listOf(
          temporaryFolder.newFile("1.txt"),
          temporaryFolder.newFile("2.txt"),
          temporaryFolder.newFile("3.txt"),
        )
        .map { it.path }
    backupFileHistory.setFileHistory(files)

    assertThat(backupFileHistory.getFileHistory()).containsExactlyElementsIn(files)
  }

  @Test
  fun setHistory_removesNonExistingFiles() {
    val backupFileHistory = BackupFileHistory(project)
    val files =
      listOf(
          temporaryFolder.newFile("1.txt"),
          temporaryFolder.newFile("2.txt"),
          temporaryFolder.newFile("3.txt"),
        )
        .map { it.path }
    backupFileHistory.setFileHistory(files + "non-existing-file.txt")

    assertThat(backupFileHistory.getFileHistory()).containsExactlyElementsIn(files)
  }

  @Test
  fun setHistory_removesDirectories() {
    val backupFileHistory = BackupFileHistory(project)
    val file = temporaryFolder.newFile("file.txt").path
    val dir = temporaryFolder.newFolder("dir").path
    backupFileHistory.setFileHistory(listOf(file, dir))

    assertThat(backupFileHistory.getFileHistory()).containsExactly(file)
  }

  @Test
  fun setHistory_relativeToProject_removesNonExistingFiles() {
    val backupFileHistory = BackupFileHistory(project)
    val files =
      listOf(
          temporaryFolder.newFile("1.txt"),
          temporaryFolder.newFile("2.txt"),
          temporaryFolder.newFile("3.txt"),
        )
        .map { it.path }
    val relativeToProject = files.map { Path.of(it).relativeToProject(project).pathString }
    backupFileHistory.setFileHistory(relativeToProject + "non-existing-file.txt")

    assertThat(backupFileHistory.getFileHistory()).containsExactlyElementsIn(files)
  }

  @Test
  fun getHistory_removesNonExistingFiles() {
    val backupFileHistory = BackupFileHistory(project)
    val file1 = temporaryFolder.newFile("1.txt")
    val file2 = temporaryFolder.newFile("2.txt")
    val file3 = temporaryFolder.newFile("3.txt")
    backupFileHistory.setFileHistory(listOf(file1.path, file2.path, file3.path))
    file2.delete()

    assertThat(backupFileHistory.getFileHistory()).containsExactly(file1.path, file3.path)
  }

  @Test
  fun getHistory_relativeToProject_removesNonExistingFiles() {
    val backupFileHistory = BackupFileHistory(project)
    val file1 = temporaryFolder.newFile("1.txt").toPath()
    val file2 = temporaryFolder.newFile("2.txt").toPath()
    val file3 = temporaryFolder.newFile("3.txt").toPath()

    val relativeToProject =
      listOf(file1, file2, file3).map { it.relativeToProject(project) }.map { it.pathString }
    backupFileHistory.setFileHistory(relativeToProject)
    file2.deleteExisting()

    assertThat(backupFileHistory.getFileHistory())
      .containsExactly(file1.pathString, file3.pathString)
  }

  @Test
  fun getHistory_updatesPropertyWhenFileDeleted() {
    val backupFileHistory = BackupFileHistory(project)
    val file1 = temporaryFolder.newFile("1.txt")
    val file2 = temporaryFolder.newFile("2.txt")
    val file3 = temporaryFolder.newFile("3.txt")
    backupFileHistory.setFileHistory(listOf(file1.path, file2.path, file3.path))

    // Delete file2, get history and then recreate it.
    file2.delete()
    backupFileHistory.getFileHistory()
    temporaryFolder.newFile("2.txt")

    // File2 should not show up in history
    assertThat(backupFileHistory.getFileHistory()).containsExactly(file1.path, file3.path)
  }

  @Test
  fun getHistory_noFilesExist() {
    val backupFileHistory = BackupFileHistory(project)
    backupFileHistory.setFileHistory(listOf("file1", "file2"))

    assertThat(backupFileHistory.getFileHistory()).isEmpty()
  }

  @Test
  fun getHistory_removesDuplicates() {
    val backupFileHistory = BackupFileHistory(project)
    val file1 = temporaryFolder.newFile("1.txt")
    backupFileHistory.setFileHistory(listOf(file1.path, file1.path))

    assertThat(backupFileHistory.getFileHistory()).containsExactly(file1.path)
  }
}
