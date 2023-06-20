/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.dagger

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager

/** Checks if project uses Dagger (any module depends on Dagger) */
@Service
class DaggerDependencyChecker(private val project: Project) {

  fun isDaggerPresent(): Boolean =
    CachedValuesManager.getManager(project).getCachedValue(project) {
      CachedValueProvider.Result(
        calculateIsDaggerPresent(),
        ProjectRootModificationTracker.getInstance(project)
      )
    }

  private fun calculateIsDaggerPresent(): Boolean {
    return JavaPsiFacade.getInstance(project)
      .findClass(DAGGER_MODULE_ANNOTATION, GlobalSearchScope.allScope(project)) != null
  }
}
