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
import com.android.tools.rendering.ModuleRenderContext
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.base.util.module
import java.lang.ref.WeakReference
import java.util.function.Supplier

/**
 * Studio specific implementation of [ModuleRenderContext].
 */
open class StudioModuleRenderContext protected constructor(
  module: Module,
  override val fileProvider: Supplier<PsiFile?>
) : ModuleRenderContext {
  private val moduleWeakRef = WeakReference(module)
  override val isDisposed: Boolean
    get() = module?.isDisposed ?: true

  override val module: Module?
    get() = moduleWeakRef.get()
  override fun createInjectableClassLoaderLoader(): ProjectSystemClassLoader {
    val moduleRef = WeakReference(module)
    return ProjectSystemClassLoader { fqcn ->
      val module = moduleRef.get()
      if (module == null || module.isDisposed) return@ProjectSystemClassLoader null

      val psiFile = fileProvider.get()
      val virtualFile = psiFile?.virtualFile

      return@ProjectSystemClassLoader module.getModuleSystem()
        .getClassFileFinderForSourceFile(virtualFile)
        .findClassFile(fqcn)
    }
  }

  companion object {
    @JvmStatic
    fun forFile(module: Module, fileProvider: Supplier<PsiFile?>) =
      StudioModuleRenderContext(module, fileProvider)

    /** Always use one of the methods that can provide a file, only use this for testing. */
    @TestOnly
    @JvmStatic
    fun forModule(module: Module) = StudioModuleRenderContext(module) { null }

    @JvmStatic
    fun forFile(file: PsiFile): ModuleRenderContext {
      val filePointer = runReadAction { SmartPointerManager.createPointer(file) }
      val module = runReadAction { file.module!! }
      return forFile(module) { runReadAction { filePointer.element } }
    }
  }
}