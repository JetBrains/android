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
package com.android.tools.idea.util

import com.android.testutils.AssumeUtil.assumeWindows
import com.android.testutils.AssumeUtil.assumeIsLinux
import com.android.tools.idea.testing.WaitForIndexRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.guessProjectDir
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path
import kotlin.io.path.pathString

/**
 * Tests for `PathExtensions` file
 */
class PathExtensionsTest {
  private val projectRule = ProjectRule()

  @get:Rule
  val rule = RuleChain(projectRule, WaitForIndexRule(projectRule))

  private val project get() = projectRule.project
  private val projectRoot get() = project.guessProjectDir()!!.toNioPath()

  @Test
  fun relativeToProject_notInProject_linux() {
    assumeIsLinux()
    assertThat(Path.of("/foo/bar").relativeToProject(project).pathString).isEqualTo("/foo/bar")
  }

  @Test
  fun relativeToProject_inProject_linux() {
    assumeIsLinux()
    assertThat(projectRoot.resolve("foo/bar").relativeToProject(project).pathString).isEqualTo("foo/bar")
  }

  @Test
  fun absoluteInProject_notInProject_linux() {
    assumeIsLinux()
    assertThat(Path.of("/foo/bar").absoluteInProject(project).pathString).isEqualTo("/foo/bar")
  }

  @Test
  fun absoluteInProject_inProject_linux() {
    assumeIsLinux()
    assertThat(Path.of("foo/bar").absoluteInProject(project).pathString).isEqualTo(projectRoot.resolve("foo/bar").pathString)
  }

  @Test
  fun relativeToProject_notInProject_windows() {
    assumeWindows()
    assertThat(Path.of("C:/foo/bar").relativeToProject(project).pathString).isEqualTo("C:\\foo\\bar")
  }

  @Test
  fun relativeToProject_inProject_windows() {
    assumeWindows()
    assertThat(projectRoot.resolve("foo/bar").relativeToProject(project).pathString).isEqualTo("foo\\bar")
  }

  @Test
  fun absoluteInProject_notInProject_windows() {
    assumeWindows()
    assertThat(Path.of("C:\\foo\\bar").absoluteInProject(project).pathString).isEqualTo("C:\\foo\\bar")
  }

  @Test
  fun absoluteInProject_inProject_windows() {
    assumeWindows()
    assertThat(Path.of("foo\\bar").absoluteInProject(project).pathString).isEqualTo(projectRoot.resolve("foo/bar").pathString)
  }
}