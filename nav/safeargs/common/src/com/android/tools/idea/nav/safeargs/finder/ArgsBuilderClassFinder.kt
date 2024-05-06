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
package com.android.tools.idea.nav.safeargs.finder

import com.android.tools.idea.nav.safeargs.module.SafeArgsCacheModuleService
import com.android.tools.idea.nav.safeargs.psi.java.LightArgsBuilderClass
import com.intellij.openapi.project.Project
import org.jetbrains.android.facet.AndroidFacet

/** A finder that can find instances of [LightArgsBuilderClass] by qualified name / package. */
class ArgsBuilderClassFinder(project: Project) : SafeArgsClassFinderBase(project) {
  override fun findAll(facet: AndroidFacet): List<LightArgsBuilderClass> {
    return SafeArgsCacheModuleService.getInstance(facet)
      .args
      .flatMap { it.innerClasses.toList() }
      .filterIsInstance<LightArgsBuilderClass>()
  }
}
