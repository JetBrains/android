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

import com.intellij.psi.PsiClass

/** Information about module dependencies required for rendering. */
interface RenderModuleDependencies {
  val dependsOnAppCompat: Boolean

  val dependsOnAndroidXAppCompat: Boolean

  val dependsOnAndroidX: Boolean

  fun reportMissingSdkDependency(logger: IRenderLogger)

  /** Returns a list R-classes fqcns from the module and all of its dependencies. */
  val rClassesNames: List<String>

  fun findPsiClassInModuleAndDependencies(fqcn: String): PsiClass?
}