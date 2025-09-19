/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.deps

import com.google.common.base.Preconditions
import com.google.idea.blaze.common.Context
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.qsync.project.BlazeProjectDataStorage
import com.google.idea.blaze.qsync.project.BuildGraphData
import com.google.idea.blaze.qsync.project.ProjectPath
import com.google.idea.blaze.qsync.project.ProjectProto
import java.nio.file.Path

/**
 * Helper class for making a number of updates to the project proto.
 *
 *
 * This class provides a convenient way of accessing and updating various interesting parts of
 * the project proto, such as the `.workspace` module and libraries by name.
 */
class ProjectProtoUpdate(
  existingProject: ProjectProto.Project,
  private val buildGraph: BuildGraphData,
  private val context: Context<*>
) {
  interface LibraryUpdater {
    fun addClassJars(jars: Collection<Path>)
    fun addSourceJars(jars: Collection<ProjectPath>)
  }

  private val project: ProjectProto.Project.Builder = existingProject.toBuilder()
  private val workspaceModule: ProjectProto.Module.Builder = getWorkspaceModuleBuilder(project)
  private val libraries: MutableMap<String, ProjectProto.Library> = project.libraryList.associateBy { it.name }.toMutableMap()
  private val artifactDirs: MutableMap<Path, ArtifactDirectoryBuilder> = hashMapOf()

  fun context(): Context<*> = context
  fun project(): ProjectProto.Project.Builder = project
  fun buildGraph(): BuildGraphData = buildGraph
  fun workspaceModule(): ProjectProto.Module.Builder = workspaceModule

  /** Gets a builder for a library, creating it if it doesn't already exist.  */
  fun library(name: Label, updater: LibraryUpdater.() -> Unit) {
    object: LibraryUpdater {
      private fun update(name: Label, updater: ProjectProto.Library.Builder.() -> ProjectProto.Library.Builder) {
        libraries.compute(name.toString()) {
          key, library ->
          (library?.toBuilder() ?: ProjectProto.Library.newBuilder().setName(key))
            .let { updater(it) }
            .build()
        }
      }

      override fun addClassJars(jars: Collection<Path>) {
        if (jars.isEmpty()) return
        update(name) {
          addAllClassesJar(jars.map { ProjectProto.JarDirectory.newBuilder().setPath(it.toString()).build() })
        }
      }

      override fun addSourceJars(jars: Collection<ProjectPath>) {
        if (jars.isEmpty()) return
        update(name) {
          addAllSources(jars.map { ProjectProto.LibrarySource.newBuilder().setSrcjar(it.toProto()).build() })
        }
      }
    }.updater()
  }

  fun artifactDirectory(path: ProjectPath): ArtifactDirectoryBuilder {
    Preconditions.checkState(path.rootType() == ProjectPath.Root.PROJECT)
    return artifactDirs.computeIfAbsent(path.relativePath()) { path -> ArtifactDirectoryBuilder(path) }
  }

  fun build(): ProjectProto.Project {
    artifactDirs.values.forEach { it.addToArtifactDirectories(project.getArtifactDirectoriesBuilder()) }
    return project
      .clearLibrary()
      .addAllLibrary(libraries.values)
      .build()
  }

  companion object {
    private fun getWorkspaceModuleBuilder(project: ProjectProto.Project.Builder): ProjectProto.Module.Builder {
      return project
               .modulesBuilderList
               .firstOrNull { it.name == BlazeProjectDataStorage.WORKSPACE_MODULE_NAME }
             ?: throw IllegalArgumentException(
               ("Module with name ${BlazeProjectDataStorage.WORKSPACE_MODULE_NAME} not found in project proto.")
             )
    }
  }
}
