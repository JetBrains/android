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
 * An entity that encapsulates the notion of a build target reference.
 *
 * It is supposed to be used with project system specific extension points (tokens) to obtain services needed for rendering in the context
 * of a specific build target.
 *
 * Instances of `BuildTargetReference` should be obtained via its companion objects, which in the future will delegate the instantiation
 * to the project system.
 *
 * Note: In the case of the Gradle project system an implementation of `BuildTargetReference` is likely to simply wrap an instance of the
 * `AndroidFacet`.
 */
interface BuildTargetReference {
  val facet: AndroidFacet
  val module: Module get() = facet.module
  val project: Project get() = module.project

  companion object {
    private data class GradleOnlyBuildTargetReference(override val facet: AndroidFacet): BuildTargetReference

    @JvmStatic
    fun from(facet: AndroidFacet, targetFile: VirtualFile): BuildTargetReference = gradleOnly(facet)

    @JvmStatic
    fun from(applicationProjectContext: ApplicationProjectContext): BuildTargetReference = error("Not yet implemented")

    /**
     * Returns an instance of `BuildTargetReference` that refers to code under the modules (or the group of main, androidTest etc. modules).
     *
     * Depending on the active project system instances returned by this method may result in incorrect resources being used or resources
     * being not found.
     */
    @JvmStatic
    fun gradleOnly(facet: AndroidFacet): BuildTargetReference {
      return GradleOnlyBuildTargetReference(facet)
    }
  }
}
