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

import com.android.testutils.AssumeUtil
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import java.nio.file.Path
import kotlin.io.path.pathString
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever

/** Tests for [BackupDialog] */
@RunWith(JUnit4::class)
class PathUtilTest {
  private val projectRule = ProjectRule()
  private val temporaryFolder = TemporaryFolder()

  @get:Rule val rule = RuleChain(projectRule, temporaryFolder)

  private val projectDir by lazy {
    temporaryFolder.newFolder("home/user/projects/project").toPath()
  }

  private val project by lazy {
    spy(projectRule.project).apply { whenever(basePath).thenReturn(projectDir.pathString) }
  }

  @Test
  fun relativeToProject_inProject() {
    val path = Path.of("$projectDir/foo/bar")

    assertThat(path.relativeToProject(project)).isEqualTo(Path.of("foo/bar"))
  }

  @Test
  fun relativeToProject_outOfProject() {
    val path = Path.of("/tmp/home/user/tmp/foo/bar")

    assertThat(path.relativeToProject(project)).isEqualTo(Path.of("/tmp/home/user/tmp/foo/bar"))
  }

  @Test
  fun absoluteInProject_inProject() {
    val path = Path.of("foo/bar")

    assertThat(path.absoluteInProject(project)).isEqualTo(Path.of("$projectDir/foo/bar"))
  }

  @Test
  fun absoluteInProject_outOfProject() {
    val path = temporaryFolder.newFolder("home/user/tmp/foo").resolve("bar").toPath()

    assertThat(path.absoluteInProject(projectRule.project)).isEqualTo(path)
  }

  @Test
  fun isValid() {
    val path = Path.of("foo", "bar")

    assertThat(path.isValid()).isTrue()
  }

  @Test
  fun isValid_blank() {
    val path = Path.of("foo", "  ", "bar")

    assertThat(path.isValid()).isFalse()
  }

  @Test
  fun isValid_backslash() {
    AssumeUtil.assumeNotWindows()
    val path = Path.of("foo", "\\", "bar")

    assertThat(path.isValid()).isFalse()
  }
}
