/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.rendering

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile

/**
 * An entity that encapsulates the notion of a build target reference.
 *
 * It is supposed to be used with project system specific extension points (tokens) to obtain services needed for rendering in the context
 * of a specific build target.
 *
 * Instances of `BuildTargetReference` should be obtained via its companion objects, which in the future will delegate the instantiation
 * to the project system.
 *
 * Note: In the case of the Gradle project system an implementation of `BuildTargetReference` is likely to simply wrap an IDE module.
 */
interface BuildTargetReference {
  val project: Project
  val module: Module

  private data class GradleOnlyBuildTargetReference(override val module: Module): BuildTargetReference {
    override val project: Project
      get() = module.project
  }

  companion object {
    /**
     * Obtains a reference to a build target that contains the given [targetFile].
     *
     * A [module] is required to contain the [targetFile]. This is a temporary measure used to cross-validate usages during the transition
     * from facets/modules to [BuildTargetReference]s.
     */
    @JvmStatic
    fun from(module: Module, targetFile: VirtualFile): BuildTargetReference {
      if (ApplicationManager.getApplication().isUnitTestMode) {
        // NOTE: This method has two parameters even though `targetFile` seems enough. This is to make sure correct migration of all
        // callers from facet based to target file based services. It is important for a narrower scope defined by the file to be nested
        // in a wider scope defined by the facet as otherwise different code paths may try to obtain services from different modules.
        // The following check is supposed to catch cases when, for example, a caller passes a resource file from a dependency or the
        // framework as a target file that is supposed to define the build ocntext.
        runReadAction {
          if (!ModuleRootManager.getInstance(module).fileIndex.isInContent(targetFile)) {
            error("'$targetFile' is not under '${module}' content roots")
          }
        }
      }
      return gradleOnly(module)
    }

    /**
     * Obtains a reference to a build target that contains the given [targetFile].
     */
    @JvmStatic
    fun from(targetFile: PsiFile): BuildTargetReference? {
      return from(runReadAction { ModuleUtilCore.findModuleForPsiElement(targetFile) } ?: return null, targetFile.originalFile.virtualFile)
    }

    /**
     * Returns an instance of `BuildTargetReference` that refers to code under the modules (or the group of main, androidTest etc. modules).
     *
     * Depending on the active project system instances returned by this method may result in incorrect resources being used or resources
     * being not found.
     */
    @JvmStatic
    fun gradleOnly(module: Module): BuildTargetReference {
      return GradleOnlyBuildTargetReference(module)
    }
  }
}