/*
 * Copyright (C) 2021 The Android Open Source Project
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
package org.jetbrains.android.uipreview

import com.android.tools.idea.rendering.IdeaModuleProvider
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.TestOnly
import java.util.function.Supplier

/**
 * Class providing the context for a ModuleClassLoader in which is being used.
 * Gradle class resolution depends only on [Module] but ASWB will also need the file to be able to
 * correctly resolve the classes.
 *
 * This should be a short living object since it retains a string reference to a [Module].
 */
class ModuleRenderContext private constructor(private val moduleProvider: IdeaModuleProvider, val fileProvider: Supplier<PsiFile?>) {
  val isDisposed: Boolean
    get() = module.isDisposed

  val module: Module
    get() = moduleProvider.getIdeaModule()

  companion object {
    @JvmStatic
    fun forFile(module: IdeaModuleProvider, fileProvider: Supplier<PsiFile?>) = ModuleRenderContext(module, fileProvider)

    /**
     * Always use one of the methods that can provide a file, only use this for testing.
     */
    @TestOnly
    @JvmStatic
    fun forModule(module: Module) = ModuleRenderContext({ module }) { null }
  }
}