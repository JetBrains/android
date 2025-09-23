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
import com.google.idea.blaze.qsync.project.LanguageClassProto.LanguageClass
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
  /**
   * A library is a target external to the project scope.
   */
  interface LibraryUpdater {
    fun addClassJars(jars: Collection<Path>)
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
    fun addContexts(compilationContext: ProjectProto.CcCompilationContext)
    fun putFlagSets(flagSetId: String, build: ProjectProto.CcCompilerFlagSet)
  }

  private val project: ProjectProto.Project.Builder = existingProject.toBuilder()
  private val workspaceModule: ProjectProto.Module.Builder = getWorkspaceModuleBuilder(project)
  private val libraries: MutableMap<String, ProjectProto.Library> = project.libraryList.associateBy { it.name }.toMutableMap()
  private val artifactDirs: MutableMap<Path, ArtifactDirectoryBuilder> = hashMapOf()

  fun context(): Context<*> = context
  fun buildGraph(): BuildGraphData = buildGraph

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

  fun module(name: Label, updater: ModuleUpdater.() -> Unit) {
    object: ModuleUpdater {
      override fun addAndroidResourceJavaPackage(pkg: String) {
        workspaceModule.addAndroidSourcePackages(pkg)
      }

      override fun addExternalAndroidLibrary(externalAndroidLibrary: ProjectProto.ExternalAndroidLibrary) {
        workspaceModule.addAndroidExternalLibraries(externalAndroidLibrary)
      }

      override fun contentEntry(root: ProjectPath, updater: ContentEntryUpdater.() -> Unit) {
        // This is a linear scan but it is fine for now since we don't have many content entries and we add each one only once.
        val contentEntryBuilder = workspaceModule.contentEntriesBuilderList.firstOrNull {it.root == root}
                                  ?: let { workspaceModule.addContentEntriesBuilder().setRoot(root.toProto()) }
        object: ContentEntryUpdater {
          override fun addSourceRoot(
            root: ProjectPath,
            javaPackage: String,
            isTest: Boolean,
            isGenerated: Boolean,
          ) {
            contentEntryBuilder
              .addSourcesBuilder()
              .setProjectPath(root.toProto())
              .setPackagePrefix(javaPackage)
              .setIsTest(isTest)
              .setIsGenerated(isGenerated)
          }
        }.updater()
      }
    }.updater()
  }

  fun ccWorkspace(updater: CcWorkspaceUpdater.() -> Unit) {
    object: CcWorkspaceUpdater {
      override fun addContexts(compilationContext: ProjectProto.CcCompilationContext) {
        project.ccWorkspaceBuilder.addContexts(compilationContext)
      }

      override fun putFlagSets(flagSetId: String, build: ProjectProto.CcCompilerFlagSet) {
        project.ccWorkspaceBuilder.putFlagSets(flagSetId, build)
      }
    }.updater()
  }

  fun artifactDirectory(path: ProjectPath): ArtifactDirectoryBuilder {
    val projectPath = path as ProjectPath.ProjectRelativeProjectPath
    return artifactDirs.computeIfAbsent(projectPath.relativePath) { path -> ArtifactDirectoryBuilder(path) }
  }

  fun build(): ProjectProto.Project {
    artifactDirs.values.forEach { it.addToArtifactDirectories(project.getArtifactDirectoriesBuilder()) }
    if (project.getCcWorkspaceBuilder().getContextsCount() > 0) {
      if (!project.activeLanguagesList.contains(LanguageClass.LANGUAGE_CLASS_CC)) {
        project.addActiveLanguages(LanguageClass.LANGUAGE_CLASS_CC)
      }
    }
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
             ?: project.addModulesBuilder()
               .setName(BlazeProjectDataStorage.WORKSPACE_MODULE_NAME)
               .setType(ProjectProto.ModuleType.MODULE_TYPE_DEFAULT)
    }
  }
}
