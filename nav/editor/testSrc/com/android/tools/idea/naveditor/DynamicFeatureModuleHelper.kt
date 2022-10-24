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
package com.android.tools.idea.naveditor

import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.project.DefaultProjectSystem
import com.android.tools.idea.projectsystem.AndroidModuleSystem
import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.testing.Dependencies
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.libraries.Library
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.android.AndroidTestCase

fun addDynamicFeatureModule(moduleName: String, module: Module, fixture: JavaCodeInsightTestFixture) {
  val project = module.project
  val dynamicFeatureModule = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), moduleName,
                                                   fixture.tempDirFixture.findOrCreateDir(moduleName))
  AndroidTestCase.addAndroidFacetAndSdk(dynamicFeatureModule, false)

  val newModuleSystem = object : AndroidModuleSystem by DefaultModuleSystem(module) {
    override fun getDynamicFeatureModules(): List<Module> = listOf(dynamicFeatureModule)
  }

  val newProjectSystem = object : AndroidProjectSystem by DefaultProjectSystem(project) {
    override fun getModuleSystem(module: Module): AndroidModuleSystem = newModuleSystem
  }

  ProjectSystemService.getInstance(project).replaceProjectSystemForTests(newProjectSystem)

  Dependencies.add(fixture, "navigation/navigation-runtime",
                   "navigation/navigation-common",
                   "navigation/navigation-fragment",
                   "fragment/fragment")

  val lib = findFragmentLibrary(module)
  ModuleRootModificationUtil.addDependency(dynamicFeatureModule, lib, DependencyScope.PROVIDED, true)
}

private fun findFragmentLibrary(module: Module): Library {
  var library: Library? = null
  OrderEnumerator.orderEntries(module).forEachLibrary {
    if (it.name == "fragment.aar") {
      library = it
      false
    }
    else {
      true
    }
  }
  return library!!
}
