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
package com.android.tools.rendering

import com.android.tools.rendering.classloading.loaders.CachingClassLoaderLoader
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiFile
import java.util.function.Supplier

/**
 * Class providing the context for a ModuleClassLoader in which is being used. Gradle class
 * resolution depends only on [Module] but ASWB will also need the file to be able to correctly
 * resolve the classes.
 */
interface ModuleRenderContext {
  val fileProvider: Supplier<PsiFile?>

  val isDisposed: Boolean

  val module: Module?

  /**
   * Creates [CachingClassLoaderLoader] for the classes of this context that might change in time.
   * That could happen if e.g. the sources were recompiled and new class files were generated.
   */
  fun createInjectableClassLoaderLoader(): CachingClassLoaderLoader
}
