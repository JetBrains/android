/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.lint.common

import com.android.SdkConstants
import com.android.tools.lint.client.api.LintClient
import com.google.common.collect.Maps
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.GeneratedSourcesFilter
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ArrayUtil
import java.io.File

/**
 * An [LintIdeProject] represents a lint project, which typically corresponds to a [Module], but can
 * also correspond to a library "project" such as an android library.
 */
open class LintIdeProject protected constructor(client: LintClient, dir: File, referenceDir: File) :
  com.android.tools.lint.detector.api.Project(client, dir, referenceDir) {

  companion object {
    /** Creates a set of projects for the given IntelliJ modules */
    fun create(
      client: LintIdeClient,
      files: List<VirtualFile>?,
      vararg modules: Module,
    ): List<com.android.tools.lint.detector.api.Project> {
      val projects = ArrayList<com.android.tools.lint.detector.api.Project>()

      val projectMap = Maps.newHashMap<com.android.tools.lint.detector.api.Project, Module>()
      val moduleMap = Maps.newHashMap<Module, com.android.tools.lint.detector.api.Project>()
      if (!files.isNullOrEmpty()) {
        for (module in modules) {
          addProjects(client, module, files, moduleMap, projectMap, projects)
        }
      } else {
        for (module in modules) {
          addProjects(client, module, null, moduleMap, projectMap, projects)
        }
      }

      client.setModuleMap(projectMap)

      if (projects.size > 1) {
        // Partition the projects up such that we only return projects that aren't
        // included by other projects (e.g. because they are library projects)
        val roots = HashSet<com.android.tools.lint.detector.api.Project>(projects)
        for (project in projects) {
          roots.removeAll(project.getAllLibraries().toSet())
        }
        return roots.toList()
      } else {
        return projects
      }
    }

    /**
     * Creates a project for a single file. Also, optionally creates a main project for the file, if
     * applicable.
     *
     * @param client the lint client
     * @param file the file to create a project for
     * @param module the module to create a project for
     * @return a project for the file, as well as a project (or null) for the main Android module
     */
    fun createForSingleFile(
      client: LintIdeClient,
      file: VirtualFile?,
      module: Module,
    ): Pair<
      com.android.tools.lint.detector.api.Project,
      com.android.tools.lint.detector.api.Project,
    > {
      // TODO: Can make this method even more lightweight: we don't need to
      //    initialize anything in the project (source paths etc) other than the
      //    metadata necessary for this file's type
      val project = createModuleProject(client, module)
      val main: LintModuleProject? = null
      val projectMap = Maps.newHashMap<com.android.tools.lint.detector.api.Project, Module>()
      if (project != null) {
        project.setDirectLibraries(listOf<com.android.tools.lint.detector.api.Project>())
        if (file != null) {
          project.addFile(VfsUtilCore.virtualToIoFile(file))
        }
        projectMap[project] = module
        project.isGradleRootHolder = true
      }
      client.setModuleMap(projectMap)

      return Pair.create(project, main)
    }

    /**
     * Recursively add lint projects for the given module, and any other module or library it
     * depends on, and also populate the reverse maps so we can quickly map from a lint project to a
     * corresponding module/library (used by the lint client
     */
    private fun addProjects(
      client: LintClient,
      module: Module,
      files: List<VirtualFile>?,
      moduleMap: MutableMap<Module, com.android.tools.lint.detector.api.Project>,
      projectMap: MutableMap<com.android.tools.lint.detector.api.Project, Module>,
      projects: MutableList<com.android.tools.lint.detector.api.Project>,
    ) {
      if (moduleMap.containsKey(module)) {
        return
      }

      val project: LintModuleProject = createModuleProject(client, module) ?: return

      project.ideaProject = module.project
      project.isGradleRootHolder =
        File(project.dir, SdkConstants.FN_SETTINGS_GRADLE).exists() ||
          File(project.dir, SdkConstants.FN_SETTINGS_GRADLE_KTS).exists() ||
          File(project.dir, SdkConstants.FN_SETTINGS_GRADLE_DECLARATIVE).exists()
      projects.add(project)
      moduleMap[module] = project
      projectMap[project] = module

      if (processFileFilter(module, files, project)) {
        // No need to process dependencies when doing single file analysis
        return
      }

      val dependencies: MutableList<com.android.tools.lint.detector.api.Project> = ArrayList()
      val entries = ModuleRootManager.getInstance(module).orderEntries

      // Loop in the reverse order to resolve dependencies on the libraries, so that if a library
      // is required by two higher level libraries it can be inserted in the correct place.
      val deps = ArrayList<Module>()
      var i = entries.size
      while (--i >= 0) {
        val orderEntry = entries[i]
        if (orderEntry is ModuleOrderEntry) {

          if (orderEntry.scope == DependencyScope.COMPILE) {
            val depModule = orderEntry.module

            if (depModule != null) {
              deps.add(depModule)
            }
          }
        }
      }
      for (depModule in deps) {
        val p = moduleMap[depModule]
        if (p != null) {
          dependencies.add(p)
        } else {
          addProjects(client, depModule, files, moduleMap, projectMap, dependencies)
        }
      }

      project.setDirectLibraries(dependencies)
    }

    /**
     * Checks whether we have a file filter (e.g. a set of specific files to check in the module
     * rather than all files, and if so, and if all the files have been found, returns true)
     */
    fun processFileFilter(
      module: Module,
      files: List<VirtualFile>?,
      project: com.android.tools.lint.detector.api.Project,
    ): Boolean {
      if (!files.isNullOrEmpty()) {
        val allMatched =
          ApplicationManager.getApplication()
            .runReadAction(
              Computable {
                var matched = true
                for (file in files) {
                  if (module.moduleContentScope.accept(file)) {
                    project.addFile(VfsUtilCore.virtualToIoFile(file))
                  } else {
                    matched = false
                  }
                }
                matched
              }
            )

        if (allMatched) {
          // We're only scanning a subset of files (typically the current file in the editor);
          // in that case, don't initialize all the libraries etc
          project.directLibraries = listOf<com.android.tools.lint.detector.api.Project>()
          return true
        }
      }
      return false
    }

    /** Creates a new module project */
    private fun createModuleProject(client: LintClient, module: Module): LintModuleProject? {
      val dir = getLintProjectDirectory(module) ?: return null
      val project = LintModuleProject(client, dir, dir, module)
      project.ideaProject = module.project

      client.registerProject(dir, project)
      return project
    }

    /** Returns the directory lint would use for a project wrapping the given module */
    fun getLintProjectDirectory(module: Module): File? {
      val contentRoots = ModuleRootManager.getInstance(module).contentRoots
      return if (contentRoots.size == 1) {
        VfsUtilCore.virtualToIoFile(contentRoots[0])
      } else {
        module.getModuleDir()
      }
    }
  }

  override fun initialize() {
    // NOT calling super: super performs ADT/ant initialization. Here we want to use
    // the gradle data instead
  }

  override fun getIdeaProject(): Project? {
    if (client is LintIdeClient) {
      return (client as LintIdeClient).myProject
    }
    return super.getIdeaProject()
  }

  open class LintModuleProject(
    client: LintClient,
    dir: File,
    referenceDir: File,
    private val module: Module,
  ) : LintIdeProject(client, dir, referenceDir) {
    override fun isAndroidProject(): Boolean {
      return false
    }

    private fun includeTests(): Boolean {
      val model = getBuildModule()
      if (model != null) {
        return model.lintOptions.checkTestSources
      }
      return false
    }

    override fun getJavaSourceFolders(): List<File> {
      if (javaSourceFolders == null) {
        val sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots(false)
        val dirs = ArrayList<File>(sourceRoots.size)
        val project = module.project
        for (root in sourceRoots) {
          if (GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(root, project)) {
            // Skip generated sources; they're supposed to be returned by
            // getGeneratedSourceFolders()
            continue
          }
          dirs.add(VfsUtilCore.virtualToIoFile(root))
        }
        javaSourceFolders = dirs
      }

      return javaSourceFolders
    }

    override fun getGeneratedSourceFolders(): List<File> {
      if (generatedSourceFolders == null) {
        val sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots(includeTests())
        val dirs = ArrayList<File>(sourceRoots.size)
        val project = module.project
        for (root in sourceRoots) {
          if (GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(root, project)) {
            dirs.add(VfsUtilCore.virtualToIoFile(root))
          }
        }
        generatedSourceFolders = dirs
      }

      return generatedSourceFolders
    }

    override fun getTestSourceFolders(): List<File> {
      if (testSourceFolders == null) {
        val manager = ModuleRootManager.getInstance(module)
        val sourceRoots = manager.getSourceRoots(false)
        val sourceAndTestRoots = manager.getSourceRoots(true)
        val project = module.project
        val dirs = ArrayList<File>(sourceAndTestRoots.size)
        for (root in sourceAndTestRoots) {
          if (!ArrayUtil.contains(root, *sourceRoots)) {
            if (GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(root, project)) {
              // Skip generated sources
              continue
            }
            dirs.add(VfsUtilCore.virtualToIoFile(root))
          }
        }
        testSourceFolders = dirs
      }
      return testSourceFolders
    }

    override fun getJavaClassFolders(): List<File> {
      if (LintIdeClient.SUPPORT_CLASS_FILES) {
        if (javaClassFolders == null) {
          val extension = CompilerModuleExtension.getInstance(module)
          val folder = extension?.compilerOutputPath
          javaClassFolders =
            if (folder != null) {
              listOf(VfsUtilCore.virtualToIoFile(folder))
            } else {
              emptyList()
            }
        }

        return javaClassFolders
      }

      return emptyList()
    }

    override fun getJavaLibraries(includeProvided: Boolean): List<File> {
      if (LintIdeClient.SUPPORT_CLASS_FILES) {
        if (javaLibraries == null) {
          javaLibraries = ArrayList<File>()

          val entries = ModuleRootManager.getInstance(module).orderEntries

          // loop in the inverse order to resolve dependencies on the libraries, so
          // that if a library is required by two higher level libraries it can be
          // inserted in the correct place
          for (i in entries.indices.reversed()) {
            val orderEntry = entries[i]
            if (orderEntry is LibraryOrderEntry) {
              val classes = orderEntry.getRootFiles(OrderRootType.CLASSES)
              for (file in classes) {
                javaLibraries.add(VfsUtilCore.virtualToIoFile(file))
              }
            }
          }
        }

        return javaLibraries
      }

      return emptyList()
    }
  }
}
