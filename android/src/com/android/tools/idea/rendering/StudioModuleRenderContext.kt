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

import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.rendering.classloading.loaders.ProjectSystemClassLoader
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import org.jetbrains.annotations.TestOnly
import java.util.function.Supplier

/**
 * Studio specific module render context.
 */
open class StudioModuleRenderContext protected constructor(
  val buildTargetReference: BuildTargetReference,
  val fileProvider: Supplier<PsiFile?>
)  {

  open fun createInjectableClassLoaderLoader(): ProjectSystemClassLoader {
    return ProjectSystemClassLoader { fqcn ->
      val moduleSystem = (buildTargetReference.moduleIfNotDisposed ?: return@ProjectSystemClassLoader null).getModuleSystem()

      val psiFile = fileProvider.get()
      val virtualFile = psiFile?.virtualFile

      return@ProjectSystemClassLoader moduleSystem
        .getClassFileFinderForSourceFile(virtualFile)
        .findClassFile(fqcn)
    }
  }

  companion object {
    @JvmStatic
    fun forFile(buildTargetReference: BuildTargetReference, fileProvider: Supplier<PsiFile?>) =
      StudioModuleRenderContext(buildTargetReference, fileProvider)

    /** Always use one of the methods that can provide a file, only use this for testing. */
    @TestOnly
    @JvmStatic
    fun forModule(module: Module) = StudioModuleRenderContext(BuildTargetReference.gradleOnly(module)) { null }

    @JvmStatic
    fun forFile(buildTargetReference: BuildTargetReference, file: PsiFile): StudioModuleRenderContext {
      val filePointer = runReadAction { SmartPointerManager.createPointer(file) }
      return forFile(buildTargetReference) { runReadAction { filePointer.element } }
    }
  }
}
