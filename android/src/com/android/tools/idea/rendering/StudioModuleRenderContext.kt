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

import com.android.tools.rendering.ModuleRenderContext
import com.android.tools.rendering.api.IdeaModuleProvider
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.base.util.module
import java.util.function.Supplier

/**
 * Studio specific implementation of [ModuleRenderContext].
 *
 * This should be a short living object since it retains a strong reference to a [Module].
 */
class StudioModuleRenderContext private constructor(
  private val moduleProvider: IdeaModuleProvider,
  override val fileProvider: Supplier<PsiFile?>
) : ModuleRenderContext {
  override val isDisposed: Boolean
    get() = module.isDisposed

  override val module: Module
    get() = moduleProvider.getIdeaModule()

  companion object {
    @JvmStatic
    fun forFile(module: IdeaModuleProvider, fileProvider: Supplier<PsiFile?>) =
      StudioModuleRenderContext(module, fileProvider)

    /** Always use one of the methods that can provide a file, only use this for testing. */
    @TestOnly
    @JvmStatic
    fun forModule(module: Module) = StudioModuleRenderContext({ module }) { null }

    @JvmStatic
    fun forFile(file: PsiFile): ModuleRenderContext {
      val filePointer = runReadAction { SmartPointerManager.createPointer(file) }
      val module = runReadAction { file.module!! }
      return forFile({ module }) { runReadAction { filePointer.element } }
    }
  }
}