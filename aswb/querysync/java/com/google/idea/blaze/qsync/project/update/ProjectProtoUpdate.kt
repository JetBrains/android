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
package com.google.idea.blaze.qsync.project.update

import com.google.idea.blaze.common.Context
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.qsync.artifacts.BuildArtifact
import com.google.idea.blaze.qsync.deps.DependencyBuildContext
import com.google.idea.blaze.qsync.project.BlazeProjectDataStorage
import com.google.idea.blaze.qsync.project.BuildGraphData
import com.google.idea.blaze.qsync.project.ProjectPath
import com.google.idea.blaze.qsync.project.ProjectProto
import com.google.idea.blaze.qsync.project.QuerySyncLanguage
import com.intellij.util.containers.with
import java.nio.file.Path
import kotlin.collections.plus
import kotlin.collections.plusAssign

/**
 * Helper class for making a number of updates to the project proto.
 *
 *
 * This class provides a convenient way of accessing and updating various interesting parts of
 * the project proto, such as the `.workspace` module and libraries by name.
 */
class ProjectProtoUpdate(existingProject: ProjectProto.Project) {
  /**
   * A library is a target external to the project scope.
   */
  interface LibraryUpdater {
    fun addClassJars(jars: Collection<ProjectPath>)
    fun addSourceJars(jars: Collection<ProjectPath>)
  }

  /**
   * A module is a target in the scope of the project.
   */
  interface ModuleUpdater {
    fun addAndroidResourceJavaPackage(pkg: String)
    fun addExternalAndroidLibrary(externalAndroidLibrary: ProjectProto.ExternalAndroidLibrary)
    fun contentEntry(root: ProjectPath, updater: ContentEntryUpdater.() -> Unit)
  }

  /**
   * A content entry is a directory that is analysed by the IDE and contains source code folders.
   *
   * Note, that all files are "indexed" by the IDE regardless of whether they are in a source folder or not.
   */
  interface ContentEntryUpdater {
    fun addSourceRoot(root: ProjectPath, javaPackage: String, isTest: Boolean, isGenerated: Boolean)
  }

  /**
   * A CcWorkspace holds configuration of native targets.
   */
  interface CcWorkspaceUpdater {
    fun target(target: Label, updater: CcTargetUpdater.() -> Unit)
    fun putFlagSets(flagSetId: String, flagSet: ProjectProto.CcCompilerFlagSet)
  }

  /**
   * A CcTarget represents a native build target that may have different configurations (compilation contexts).
   */
  interface CcTargetUpdater {
    fun addSourceFile(file: ProjectProto.CcSourceFile)
    fun addContext(context: ProjectProto.CcCompilationContext)
  }

  /**
   * An artifact directory is a directory under the IDE project directory and is populated with copies of remote artifacts in a structure
   * resembling the original artifact tree under bazel-out and in a way which is suitable for the IDE.
   */
  interface ArtifactDirectoryUpdater {
    fun addIfNewer(
      artifactPath: Path,
      artifact: BuildArtifact,
      buildContext: DependencyBuildContext,
      transform: ProjectProto.ProjectArtifact.ArtifactTransform = ProjectProto.ProjectArtifact.ArtifactTransform.COPY,
    ): ProjectPath.ProjectRelativeProjectPath?

    fun addExternalRepository(
      repositoryName: String,
      absolutePath: Path,
      buildContext: DependencyBuildContext,
    )
  }

  private val project: ProjectProto.Project.Builder = existingProject.toBuilder()
  private val workspaceModule: ProjectProto.Module.Builder = getWorkspaceModuleBuilder(project)

  /** Gets a builder for a library, creating it if it doesn't already exist.  */
  fun library(name: Label, updater: LibraryUpdater.() -> Unit) {
    object : LibraryUpdater {
      private fun update(name: Label, updater: ProjectProto.Library.() -> ProjectProto.Library) {
        project.libraries.compute(name) { key, library ->
          updater((library ?: ProjectProto.Library(key, listOf(), listOf())))
        }
      }

      override fun addClassJars(jars: Collection<ProjectPath>) {
        if (jars.isEmpty()) return
        update(name) { copy(classesJarList = classesJarList + jars) }
      }

      override fun addSourceJars(jars: Collection<ProjectPath>) {
        if (jars.isEmpty()) return
        update(name) { copy(sourcesList = sourcesList + jars) }
      }
    }.updater()
  }

  fun module(name: Label, updater: ModuleUpdater.() -> Unit) {
    object: ModuleUpdater {
      override fun addAndroidResourceJavaPackage(pkg: String) {
        workspaceModule.androidSourcePackages += pkg
      }

      override fun addExternalAndroidLibrary(externalAndroidLibrary: ProjectProto.ExternalAndroidLibrary) {
        workspaceModule.androidExternalLibraries += externalAndroidLibrary
      }

      override fun contentEntry(root: ProjectPath, updater: ContentEntryUpdater.() -> Unit) {
        // This is a linear scan but it is fine for now since we don't have many content entries and we add each one only once.
        var contentEntryBuilder =
          workspaceModule
            .contentEntries
            .getOrPut(root) { ProjectProto.ContentEntry(root = root, sourceFolders = listOf(), excludes = listOf()) }
        object: ContentEntryUpdater {
          override fun addSourceRoot(
            root: ProjectPath,
            javaPackage: String,
            isTest: Boolean,
            isGenerated: Boolean,
          ) {
            contentEntryBuilder = contentEntryBuilder.copy(
              sourceFolders = contentEntryBuilder.sourceFolders +
                              ProjectProto.SourceFolder(
                                projectPath = root,
                                isGenerated = isGenerated,
                                isTest = isTest,
                                packagePrefix = javaPackage,
                              )
            )
          }
        }.updater()
        workspaceModule.contentEntries[root] = contentEntryBuilder
      }
    }.updater()
  }

  fun ccWorkspace(updater: CcWorkspaceUpdater.() -> Unit) {
    val targets = project.ccWorkspace.targets.toMutableMap()
    val flagSets = project.ccWorkspace.flagSets.toMutableMap()
    object: CcWorkspaceUpdater {
      override fun target(
        target: Label,
        updater: CcTargetUpdater.() -> Unit,
      ) {
        val existing = targets[target]
        val sources = existing?.sources?.toMutableMap() ?: mutableMapOf()
        val contexts = existing?.contexts?.toMutableMap() ?: mutableMapOf()
        object: CcTargetUpdater {
          override fun addSourceFile(file: ProjectProto.CcSourceFile) {
            sources[file.workspacePath] = file
          }

          override fun addContext(context: ProjectProto.CcCompilationContext) {
            contexts[context.id] = context
          }
        }.updater()
        targets[target] = ProjectProto.CcTarget(target, sources, contexts)
      }

      override fun putFlagSets(flagSetId: String, flagSet: ProjectProto.CcCompilerFlagSet) {
        flagSets[flagSetId] = flagSet
      }
    }.updater()
    project.ccWorkspace = ProjectProto.CcWorkspace(targets.toMap(), flagSets.toMap())
  }

  fun artifactDirectory(path: ProjectPath.ProjectRelativeProjectPath, updater: ArtifactDirectoryUpdater.() -> Unit) {
    var updated = false
    val contents = project.artifactDirectories.directoriesMap[path]?.contents?.toMutableMap() ?: mutableMapOf()
    object: ArtifactDirectoryUpdater {
      override fun addIfNewer(
        artifactPath: Path,
        artifact: BuildArtifact,
        buildContext: DependencyBuildContext,
        transform: ProjectProto.ProjectArtifact.ArtifactTransform,
      ): ProjectPath.ProjectRelativeProjectPath? {
        val relativePath = artifactPath.toString()
        val existing = contents[relativePath]
        if (existing != null && existing.fromBuild > buildContext.startTime()) {
          // we already have the same artifact from a more recent build.
          return null
        }
        contents[relativePath] =
          ProjectProto.ProjectArtifact(
            target = artifact.target(),
            buildArtifact = ProjectProto.BuildArtifact(artifact.digest()),
            fromBuild = buildContext.startTime(),
            transform = transform
          )
        updated = true
        return path.resolveChild(artifactPath)
      }

      override fun addExternalRepository(
        repositoryName: String,
        absolutePath: Path,
        buildContext: DependencyBuildContext,
      ) {
        val existing = contents[repositoryName]
        if (existing != null && existing.fromBuild > buildContext.startTime()) {
          // we already have the same artifact from a more recent build.
          return
        }
        contents[repositoryName] =
          ProjectProto.ExternalRepository(
            name = repositoryName,
            bazelRepositoryAbsolutePath = absolutePath,
            fromBuild = buildContext.startTime()
          )
        updated = true
      }
    }.updater()
    if (updated) {
      project.artifactDirectories =
        project.artifactDirectories.copy(
          directoriesMap = project.artifactDirectories.directoriesMap.with(path, ProjectProto.ArtifactDirectoryContents(contents)))
    }
  }

  fun build(): ProjectProto.Project {
    if (project.ccWorkspace.targets.isNotEmpty()) {
      project.activeLanguages += QuerySyncLanguage.CC
    }
    return project.build()
  }

  companion object {
    private fun getWorkspaceModuleBuilder(project: ProjectProto.Project.Builder): ProjectProto.Module.Builder {
      return project
               .modules
               .firstOrNull { it.name == BlazeProjectDataStorage.WORKSPACE_MODULE_NAME }
             ?: ProjectProto.Module.Builder(BlazeProjectDataStorage.WORKSPACE_MODULE_NAME)
    }
  }
}