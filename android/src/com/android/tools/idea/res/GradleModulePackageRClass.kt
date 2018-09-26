/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.res

import com.android.builder.model.AaptOptions
import com.intellij.psi.PsiManager
import org.jetbrains.android.dom.manifest.AndroidManifestUtils
import org.jetbrains.android.facet.AndroidFacet

class ModuleRClass(
  psiManager: PsiManager,
  facet: AndroidFacet,
  namespacing: AaptOptions.Namespacing
) : ResourceRepositoryRClass(
  psiManager,
  facet.module,
  MainResources(facet, namespacing)
) {
  class MainResources(val facet: AndroidFacet, val namespacing: AaptOptions.Namespacing) : ResourcesSource {
    override fun getPackageName(): String? = AndroidManifestUtils.getPackageName(facet)
    override fun getResourceRepository() = ResourceRepositoryManager.getOrCreateInstance(facet).getAppResources(true)!!
    override fun getResourceNamespace() = ResourceRepositoryManager.getOrCreateInstance(facet).namespace
  }
}

class ModuleTestRClass(
  psiManager: PsiManager,
  facet: AndroidFacet,
  namespacing: AaptOptions.Namespacing
) : ResourceRepositoryRClass(
  psiManager,
  facet.module,
  TestResources(facet, namespacing)
) {
  class TestResources(val facet: AndroidFacet, val namespacing: AaptOptions.Namespacing) : ResourcesSource {
    override fun getPackageName() = AndroidManifestUtils.getTestPackageName(facet)
    override fun getResourceRepository() = ResourceRepositoryManager.getOrCreateInstance(facet).testAppResources
    override fun getResourceNamespace() = ResourceRepositoryManager.getOrCreateInstance(facet).namespace
  }
}

