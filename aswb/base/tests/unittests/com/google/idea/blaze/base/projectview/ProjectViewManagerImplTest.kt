/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.projectview

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ProjectViewManagerImplTest {

  @Test
  fun testResolveWorkspaceRoot_relative() {
    val projectViewFile = File("/path/to/project/.bazelproject")
    val workspaceLocation = "../workspace"
    val resolved = ProjectViewManagerImpl.resolveWorkspaceRoot(projectViewFile, workspaceLocation)
    assertThat(resolved.path).isEqualTo("/path/to/workspace")
  }

  @Test
  fun testResolveWorkspaceRoot_absolute() {
    val projectViewFile = File("/path/to/project/.bazelproject")
    val workspaceLocation = "/other/path/workspace"
    val resolved = ProjectViewManagerImpl.resolveWorkspaceRoot(projectViewFile, workspaceLocation)
    assertThat(resolved.path).isEqualTo("/other/path/workspace")
  }
}
