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

import com.android.tools.idea.projectsystem.ApplicationProjectContext
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.AndroidFacet

/**
 * A reference to an Android build target.
 *
 * [AndroidBuildTargetReference] provides access to an [AndroidFacet] that refers to an IDE module containing source code files from the
 * [buildTarget] and to the [buildTarget] reference, which can be passed to the current project system to refer to this specific build
 * target.
 */
interface AndroidBuildTargetReference {
  val facet: AndroidFacet
  val module: Module get() = facet.module
  val project: Project get() = module.project
  val buildTarget: BuildTargetReference

  private data class Impl(override val facet: AndroidFacet, override val buildTarget: BuildTargetReference) : AndroidBuildTargetReference

  companion object {
    /**
     * Obtains a reference to a build target that contains the given [targetFile].
     *
     * A [facet] is required to refer to an Android module containing the [targetFile]. This is a temporary measure used to cross-validate
     * usages during the transition from facets to [AndroidBuildTargetReference]s.
     */
    @JvmStatic
    fun from(facet: AndroidFacet, targetFile: VirtualFile): AndroidBuildTargetReference {
      return Impl(facet, BuildTargetReference.from(facet.module, targetFile))
    }

    /**
     * Obtains a reference to a build target that was used to build the running application (if known).
     */
    @JvmStatic
    fun from(applicationProjectContext: ApplicationProjectContext): AndroidBuildTargetReference? = error("Not yet implemented")

    /**
     * Returns an instance of `BuildTargetReference` that refers to code under the modules (or the group of main, androidTest etc. modules).
     *
     * Depending on the active project system instances returned by this method may result in incorrect resources being used or resources
     * being not found.
     */
    @JvmStatic
    fun gradleOnly(facet: AndroidFacet): AndroidBuildTargetReference {
      return Impl(facet, BuildTargetReference.gradleOnly(facet.module))
    }
  }
}
