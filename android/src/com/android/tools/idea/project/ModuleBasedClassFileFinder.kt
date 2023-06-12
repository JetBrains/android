/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.project

import com.android.tools.idea.projectsystem.ClassFileFinder
import com.android.tools.idea.projectsystem.findClassFileInOutputRoot
import com.android.tools.idea.projectsystem.isAndroidTestModule
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile

private val LOG = Logger.getInstance(ModuleBasedClassFileFinder::class.java)

private fun Logger.debugIfEnabled(msg: String) {
  if (isDebugEnabled) debug(msg)
}

/**
 * The default implementation of [ClassFileFinder] which searches for class files
 * among the transitive dependencies of its [module], as determined by [ModuleRootManager].
 *
 * [ModuleBasedClassFileFinder] only checks the JPS output directory for each module,
 * but subclasses may override [findClassFileInModule] to check other build system-specific
 * outputs.
 */
open class ModuleBasedClassFileFinder(val module: Module): ClassFileFinder {
  override fun findClassFile(fqcn: String) = findClassFile(module, fqcn, mutableSetOf())

  /**
   * Searches for the class file corresponding to [fqcn] by looking in the given
   * [module] and its transitive dependencies.
   */
  private fun findClassFile(module: Module, fqcn: String, visited: MutableSet<Module>): VirtualFile? {
    if (!visited.add(module) || module.isDisposed) return null

    LOG.debugIfEnabled("findClassFile(module=$module, fqcn=$fqcn)})")

    try {
      val classFile = findClassFileInModuleWithLogging(module, fqcn)
      if (classFile != null) return classFile

      ModuleRootManager.getInstance(module).getDependencies(module.isAndroidTestModule()).forEach { depModule ->
        val classFile = findClassFile(depModule, fqcn, visited)
        if (classFile != null) return classFile
      }
    } catch (t: Throwable) {
      if (module.isDisposed) {
        return null
      }
      throw t
    }

    return null
  }

  /**
   * Wraps the call to [findClassFileInModule] with debug logging.
   */
  private fun findClassFileInModuleWithLogging(module: Module, fqcn: String): VirtualFile? {
    LOG.debugIfEnabled("findClassInModule(module=$module, fqcn=$fqcn)")

    return findClassFileInModule(module, fqcn).also {
      if (it == null) LOG.debugIfEnabled("  Class not found")
    }
  }

  /**
   * [findClassFile] calls this method for each module in the transitive closure of
   * [ModuleBasedClassFileFinder.module] until this method returns a non-null file.
   *
   * By default, this method simply checks the compiler output directory as given
   * by [CompilerModuleExtension.getCompilerOutputPath], which is where modules built
   * with JPS store their compiled output.
   */
  protected open fun findClassFileInModule(module: Module, fqcn: String): VirtualFile? {
    return CompilerModuleExtension.getInstance(module)?.compilerOutputPath?.let { findClassFileInOutputRoot(it, fqcn) }
  }
}
