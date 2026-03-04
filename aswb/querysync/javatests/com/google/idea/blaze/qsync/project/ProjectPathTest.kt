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
package com.google.idea.blaze.qsync.project

import com.google.common.truth.Expect
import com.google.idea.blaze.qsync.project.ProjectPath.ExternalRepositoryFinder
import java.nio.file.Path
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ProjectPathTest {

  @get:Rule val expect: Expect = Expect.create()

  @Test
  fun workspaceRelative_withoutExternalRepository_returnsWorkspaceRelativeProjectPath() {
    val relativePath = Path.of("some/path")
    val finder = ExternalRepositoryFinder.createEmptyForTests()
    val projectPath = ProjectPath.workspaceRelative(relativePath, finder)

    expect.that(projectPath).isInstanceOf(ProjectPath.WorkspaceRelativeProjectPath::class.java)
    val workspacePath = projectPath as ProjectPath.WorkspaceRelativeProjectPath
    expect.that(workspacePath.relativePath).isEqualTo(relativePath)
    expect.that(workspacePath.innerPath).isEqualTo(Path.of(""))
  }

  @Test
  fun workspaceRelative_withExternalRepository_returnsExternalRepositoryRelativeProjectPath() {
    val relativePath = Path.of("external/my_repo/some/path")
    val finder =
      object : ExternalRepositoryFinder {
        override fun find(name: String): Path? = if (name == "my_repo") Path.of("/some/workspace/external/my_repo") else null
      }

    val projectPath = ProjectPath.workspaceRelative(relativePath, finder)

    expect.that(projectPath).isInstanceOf(ProjectPath.ExternalRepositoryRelativeProjectPath::class.java)
    val externalPath = projectPath as ProjectPath.ExternalRepositoryRelativeProjectPath
    expect.that(externalPath.externalRepositoryName).isEqualTo("my_repo")
    expect.that(externalPath.relativePath).isEqualTo(Path.of("some/path"))
    expect.that(externalPath.innerPath).isEqualTo(Path.of(""))
  }

  @Test
  fun externalRepositoryRelative_returnsExternalRepositoryRelativeProjectPath() {
    val projectPath = ProjectPath.externalRepositoryRelative("my_repo", Path.of("some/path"))

    expect.that(projectPath).isInstanceOf(ProjectPath.ExternalRepositoryRelativeProjectPath::class.java)
    expect.that(projectPath.externalRepositoryName).isEqualTo("my_repo")
    expect.that(projectPath.relativePath).isEqualTo(Path.of("some/path"))
    expect.that(projectPath.innerPath).isEqualTo(Path.of(""))
  }

  @Test
  fun projectRelative_returnsProjectRelativeProjectPath() {
    val projectPath = ProjectPath.projectRelative(Path.of("some/path"))

    expect.that(projectPath).isInstanceOf(ProjectPath.ProjectRelativeProjectPath::class.java)
    expect.that(projectPath.relativePath).isEqualTo(Path.of("some/path"))
    expect.that(projectPath.innerPath).isEqualTo(Path.of(""))
  }

  @Test
  fun absolute_withAbsolutePath_returnsAbsoluteProjectPath() {
    val absolutePath = Path.of("/absolute/path")
    val projectPath = ProjectPath.absolute(absolutePath)

    expect.that(projectPath).isInstanceOf(ProjectPath.AbsoluteProjectPath::class.java)
    expect.that(projectPath.absolutePath).isEqualTo(absolutePath)
    expect.that(projectPath.innerPath).isEqualTo(Path.of(""))
  }

  @Test
  fun absolute_withRelativePath_throwsException() {
    val relativePath = Path.of("relative/path")
    assertThrows(IllegalArgumentException::class.java) { ProjectPath.absolute(relativePath) }
  }

  @Test
  fun resolveChild_appendsChildPath_forAllImplementations() {
    val childPath = Path.of("child")

    val workspacePath = ProjectPath.workspaceRelativeForTests(Path.of("some/path")).resolveChild(childPath)
    expect.that((workspacePath as ProjectPath.WorkspaceRelativeProjectPath).relativePath).isEqualTo(Path.of("some/path/child"))

    val externalPath = ProjectPath.externalRepositoryRelative("repo", Path.of("some/path")).resolveChild(childPath)
    expect.that((externalPath as ProjectPath.ExternalRepositoryRelativeProjectPath).relativePath).isEqualTo(Path.of("some/path/child"))

    val projectPath = ProjectPath.projectRelative(Path.of("some/path")).resolveChild(childPath)
    expect.that((projectPath as ProjectPath.ProjectRelativeProjectPath).relativePath).isEqualTo(Path.of("some/path/child"))

    val absolutePath = ProjectPath.absolute(Path.of("/some/path")).resolveChild(childPath)
    expect.that((absolutePath as ProjectPath.AbsoluteProjectPath).absolutePath).isEqualTo(Path.of("/some/path/child"))
  }

  @Test
  fun withInnerJarPath_setsInnerPath_forAllImplementations() {
    val innerJarPath = Path.of("inner/jar/path")

    val workspacePath = ProjectPath.workspaceRelativeForTests(Path.of("some/path")).withInnerJarPath(innerJarPath)
    expect.that(workspacePath.innerPath).isEqualTo(innerJarPath)

    val externalPath = ProjectPath.externalRepositoryRelative("repo", Path.of("some/path")).withInnerJarPath(innerJarPath)
    expect.that(externalPath.innerPath).isEqualTo(innerJarPath)

    val projectPath = ProjectPath.projectRelative(Path.of("some/path")).withInnerJarPath(innerJarPath)
    expect.that(projectPath.innerPath).isEqualTo(innerJarPath)

    val absolutePath = ProjectPath.absolute(Path.of("/some")).withInnerJarPath(innerJarPath)
    expect.that(absolutePath.innerPath).isEqualTo(innerJarPath)
  }

  @Test
  fun getTestValue_formatsCorrectly_withoutInnerPath() {
    expect.that(ProjectPath.workspaceRelativeForTests(Path.of("some/path")).getTestValue()).isEqualTo("some/path")
    expect.that(ProjectPath.externalRepositoryRelative("repo", Path.of("some/path")).getTestValue()).isEqualTo("some/path")
    expect.that(ProjectPath.projectRelative(Path.of("some/path")).getTestValue()).isEqualTo("some/path")
    expect.that(ProjectPath.absolute(Path.of("/some/path")).getTestValue()).isEqualTo("/some/path")
  }

  @Test
  fun getTestValue_formatsCorrectly_withInnerPath() {
    val innerJarPath = Path.of("inner/jar/path")
    expect
      .that(ProjectPath.workspaceRelativeForTests(Path.of("some/path")).withInnerJarPath(innerJarPath).getTestValue())
      .isEqualTo("some/path!inner/jar/path")
    expect
      .that(ProjectPath.externalRepositoryRelative("repo", Path.of("some/path")).withInnerJarPath(innerJarPath).getTestValue())
      .isEqualTo("some/path!inner/jar/path")
    expect
      .that(ProjectPath.projectRelative(Path.of("some/path")).withInnerJarPath(innerJarPath).getTestValue())
      .isEqualTo("some/path!inner/jar/path")
    expect
      .that(ProjectPath.absolute(Path.of("/some/path")).withInnerJarPath(innerJarPath).getTestValue())
      .isEqualTo("/some/path!inner/jar/path")
  }

  @Test
  fun resolver_resolvesWorkspaceRelativePath() {
    val resolver =
      ProjectPath.Resolver(
        workspaceRoot = Path.of("/workspace"),
        projectRoot = Path.of("/project"),
        projectExternalRepositoriesRoot = Path.of("/project/.external"),
      )
    val projectPath = ProjectPath.workspaceRelativeForTests(Path.of("some/path"))
    expect.that(resolver.resolve(projectPath)).isEqualTo(Path.of("/workspace/some/path"))
  }

  @Test
  fun resolver_resolvesExternalRepositoryRelativePath() {
    val resolver =
      ProjectPath.Resolver(
        workspaceRoot = Path.of("/workspace"),
        projectRoot = Path.of("/project"),
        projectExternalRepositoriesRoot = Path.of("/project/.external"),
      )
    val projectPath = ProjectPath.externalRepositoryRelative("my_repo", Path.of("some/path"))
    expect.that(resolver.resolve(projectPath)).isEqualTo(Path.of("/project/.external/my_repo/some/path"))
  }

  @Test
  fun resolver_resolvesProjectRelativePath() {
    val resolver =
      ProjectPath.Resolver(
        workspaceRoot = Path.of("/workspace"),
        projectRoot = Path.of("/project"),
        projectExternalRepositoriesRoot = Path.of("/project/.external"),
      )
    val projectPath = ProjectPath.projectRelative(Path.of("some/path"))
    expect.that(resolver.resolve(projectPath)).isEqualTo(Path.of("/project/some/path"))
  }

  @Test
  fun resolver_resolvesAbsolutePath() {
    val resolver =
      ProjectPath.Resolver(
        workspaceRoot = Path.of("/workspace"),
        projectRoot = Path.of("/project"),
        projectExternalRepositoriesRoot = Path.of("/project/.external"),
      )
    val projectPath = ProjectPath.absolute(Path.of("/absolute/path"))
    expect.that(resolver.resolve(projectPath)).isEqualTo(Path.of("/absolute/path"))
  }
}
