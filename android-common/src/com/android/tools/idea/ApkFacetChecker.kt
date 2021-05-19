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
package com.android.tools.idea

import com.android.utils.reflection.qualifiedName
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key

/**
 * This class exists so that is possible to check if a module is associate to ApkFacet or not, without depending on ApkFacet directly.
 * It is initialized by ApkFacetCheckerInitializer, that exists in core and has direct access to ApkFacet.
 */
class ApkFacetChecker(private val myHasApkFacet: (module: Module) -> Boolean) {
  companion object {
    private val KEY: Key<ApkFacetChecker> = Key.create(Companion::KEY.qualifiedName)

    @JvmStatic fun hasApkFacet(module: Module): Boolean {
      val checker = module.project.getUserData(KEY)
      // if initialization works correctly this should never be null
      checkNotNull(checker)

      return checker.myHasApkFacet(module)
    }
  }

  fun storeInProject(project: Project) {
    project.putUserData(KEY, this)
  }
}