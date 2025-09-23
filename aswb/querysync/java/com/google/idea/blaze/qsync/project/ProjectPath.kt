/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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

import com.google.common.base.Preconditions
import java.nio.file.Path
import org.jetbrains.annotations.TestOnly

/** A path to a project artifact, either in the workspace or the project directory.  */
sealed interface ProjectPath: ProjectProtoModel {
  val innerPath: Path
  fun resolveChild(child: Path): ProjectPath
  fun withInnerJarPath(innerPath: Path): ProjectPath
  @TestOnly
  fun getTestValue(): String

  @JvmRecord
  data class WorkspaceRelativeProjectPath(val relativePath: Path, override val innerPath: Path): ProjectPath{
    override fun resolveChild(child: Path) = copy(relativePath = relativePath.resolve(child))
    override fun withInnerJarPath(innerPath: Path) = copy(innerPath = innerPath)
    override fun getTestValue(): String = testValue(relativePath, innerPath)
  }

  @JvmRecord
  data class ProjectRelativeProjectPath(val relativePath: Path, override val innerPath: Path): ProjectPath{
    override fun resolveChild(child: Path) = copy(relativePath = relativePath.resolve(child))
    override fun withInnerJarPath(innerPath: Path) = copy(innerPath = innerPath)
    override fun getTestValue(): String = testValue(relativePath, innerPath)
  }

  @JvmRecord
  data class AbsoluteProjectPath(val absolutePath: Path, override val innerPath: Path): ProjectPath{
    override fun resolveChild(child: Path) = copy(absolutePath = absolutePath.resolve(child))
    override fun withInnerJarPath(innerPath: Path) = copy(innerPath = innerPath)
    override fun getTestValue(): String = testValue(absolutePath, innerPath)
  }

  class Resolver(val workspaceRoot: Path, val projectRoot: Path) {
    fun resolve(projectPath: ProjectPath): Path {
      return when(projectPath) {
        is WorkspaceRelativeProjectPath -> workspaceRoot.resolve(projectPath.relativePath)
        is ProjectRelativeProjectPath -> projectRoot.resolve(projectPath.relativePath)
        is AbsoluteProjectPath -> projectPath.absolutePath
      }
    }

    companion object {
      @JvmStatic
      fun create(workspaceRoot: Path, projectRoot: Path): Resolver = Resolver(workspaceRoot = workspaceRoot, projectRoot = projectRoot)
    }
  }

  companion object {
    @JvmField
    val WORKSPACE_ROOT: WorkspaceRelativeProjectPath = WorkspaceRelativeProjectPath(relativePath = Path.of(""), innerPath = Path.of(""))

    @JvmStatic
    fun workspaceRelative(relativePath: Path): WorkspaceRelativeProjectPath {
      return WorkspaceRelativeProjectPath(relativePath = relativePath, innerPath = Path.of(""))
    }

    @JvmStatic
    fun projectRelative(relativePath: Path): ProjectRelativeProjectPath {
        return ProjectRelativeProjectPath(relativePath = relativePath, innerPath = Path.of(""))
    }

    @JvmStatic
    fun absolute(absolutePath: Path): AbsoluteProjectPath {
      Preconditions.checkArgument(absolutePath.isAbsolute, absolutePath)
      return AbsoluteProjectPath(absolutePath = absolutePath, innerPath = Path.of(""))
    }
  }
}

private fun testValue(relativePath: Path, innerPath: Path): String {
  return buildString {
    append(relativePath)
    if (innerPath.toString().isNotEmpty()) {
      append("!")
      append(innerPath)
    }
  }
}
