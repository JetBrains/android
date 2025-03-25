/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.tools.idea.rendering.classloading.loaders.ProjectSystemClassLoader
import com.android.tools.idea.rendering.tokens.BuildSystemFilePreviewServices.Companion.getBuildSystemFilePreviewServices
import com.intellij.openapi.module.Module
import org.jetbrains.annotations.TestOnly

/**
 * Studio specific module render context.
 */
open class StudioModuleRenderContext protected constructor(
  val buildTargetReference: BuildTargetReference,
)  {

  open fun createInjectableClassLoaderLoader(): ProjectSystemClassLoader {
    return ProjectSystemClassLoader { fqcn ->
      val classFileFinder =
        buildTargetReference.getBuildSystemFilePreviewServices().getRenderingServices(buildTargetReference).classFileFinder
        ?: return@ProjectSystemClassLoader null
      return@ProjectSystemClassLoader classFileFinder.findClassFile(fqcn)
    }
  }

  companion object {
    @JvmStatic
    fun forBuildTargetReference(buildTargetReference: BuildTargetReference) =
      StudioModuleRenderContext(buildTargetReference)

    /** Always use one of the methods that can provide a file, only use this for testing. */
    @TestOnly
    @JvmStatic
    fun forModule(module: Module) = StudioModuleRenderContext(BuildTargetReference.gradleOnly(module))
  }
}
