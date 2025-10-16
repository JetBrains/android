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
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import org.jetbrains.annotations.TestOnly

/** A path to a project artifact, either in the workspace or the project directory.  */
sealed interface ProjectPath: ProjectProtoModel {
  val innerPath: Path
  fun resolveChild(child: Path): ProjectPath
  fun withInnerJarPath(innerPath: Path): ProjectPath
  @TestOnly
  fun getTestValue(): String

  sealed interface SourceCodeRepositoryRelativeProjectPath: ProjectPath

  @JvmRecord
  data class WorkspaceRelativeProjectPath(val relativePath: Path, override val innerPath: Path): SourceCodeRepositoryRelativeProjectPath {
    override fun resolveChild(child: Path) = copy(relativePath = relativePath.resolve(child))
    override fun withInnerJarPath(innerPath: Path) = copy(innerPath = innerPath)
    override fun getTestValue(): String = testValue(relativePath, innerPath)
  }

  @JvmRecord
  data class ExternalRepositoryRelativeProjectPath(
    val externalRepositoryName: String,
    val relativePath: Path,
    override val innerPath: Path,
  ): SourceCodeRepositoryRelativeProjectPath {
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

  class Resolver(val workspaceRoot: Path, val projectRoot: Path, val projectExternalRepositoriesRoot: Path) {
    fun resolve(projectPath: ProjectPath): Path {
      return when(projectPath) {
        is WorkspaceRelativeProjectPath -> workspaceRoot.resolve(projectPath.relativePath)
        is ExternalRepositoryRelativeProjectPath ->
          projectExternalRepositoriesRoot.resolve(projectPath.externalRepositoryName).resolve(projectPath.relativePath)
        is ProjectRelativeProjectPath -> projectRoot.resolve(projectPath.relativePath)
        is AbsoluteProjectPath -> projectPath.absolutePath
      }
    }

    companion object {
      @JvmStatic
      fun create(workspaceRoot: Path, projectRoot: Path, projectExternalRepositoriesRoot: Path): Resolver =
        Resolver(
          workspaceRoot = workspaceRoot,
          projectRoot = projectRoot,
          projectExternalRepositoriesRoot = projectExternalRepositoriesRoot
        )
    }
  }

  interface ExternalRepositoryFinder {
    fun find(name: String): Path?

    companion object{
      private val known: MutableMap<Path, Boolean> = ConcurrentHashMap();
      @JvmStatic
      fun createAndPrepare(workspaceRoot: Path): ExternalRepositoryFinder {
        val outputBase = runCatching {
          // External repositories are supported by Bazel only.
          // See https://bazel.build/remote/output-directories: bazel-out is a symlink to $output_base/execroot/_main/bazel_out
          val bazelOut = workspaceRoot.resolve("bazel-out")
          if (Files.exists(bazelOut))
            bazelOut.toRealPath().resolve("../../..").toRealPath()
          else
            workspaceRoot
        }.getOrDefault(workspaceRoot)

        return object: ExternalRepositoryFinder{
          override fun find(name: String): Path? {
            return outputBase
              .resolve("external")
              .resolve(name)
              .takeIf { path ->
                known.computeIfAbsent(path) {
                  Files.exists(it) &&
                  !(
                    // Paths pointing into the <workspace>/external itself do not need to be redirected through `.external` dir.
                    Files.isSymbolicLink(path) &&
                    Files.readSymbolicLink(path) == workspaceRoot.resolve("external").resolve(name)
                   )
                }
              }
          }
        }
      }

      @JvmStatic
      @TestOnly
      fun createEmptyForTests(): ExternalRepositoryFinder {
        return object: ExternalRepositoryFinder {
          override fun find(name: String): Path? = null
        }
      }
    }
  }

  companion object {
    @JvmStatic
    fun workspaceRelative(relativePath: Path, externalRepositoryFinder: ExternalRepositoryFinder): SourceCodeRepositoryRelativeProjectPath {
      val (externalRepositoryName, remainder) = relativePath.maybeExternalRepositoryName(externalRepositoryFinder)
      return when (externalRepositoryName) {
        null -> WorkspaceRelativeProjectPath(relativePath, innerPath = Path.of(""))
        else -> ExternalRepositoryRelativeProjectPath(externalRepositoryName, remainder, innerPath = Path.of(""))
      }
    }

    @JvmStatic
    @TestOnly
    fun workspaceRelativeForTests(relativePath: Path): SourceCodeRepositoryRelativeProjectPath {
      return WorkspaceRelativeProjectPath(relativePath = relativePath, innerPath = Path.of(""))
    }

    @JvmStatic
    fun externalRepositoryRelative(externalRepositoryName: String, relativePath: Path): ExternalRepositoryRelativeProjectPath {
      return ExternalRepositoryRelativeProjectPath(externalRepositoryName, relativePath = relativePath, innerPath = Path.of(""))
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

private fun Path.maybeExternalRepositoryName(externalRepositoryFinder: ProjectPath.ExternalRepositoryFinder): Pair<String?, Path> {
  // See https://bazel.build/remote/output-directories
  if (nameCount > 1 && startsWith(Path.of("external"))) {
    val name = getName(1).toString()
    if (externalRepositoryFinder.find(name) != null) {
      return name to subpath(2, nameCount)
    }
  }
  return null to this
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
