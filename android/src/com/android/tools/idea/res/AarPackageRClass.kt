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

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.AbstractResourceRepository
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiManager
import org.jetbrains.android.augment.AarResourceTypeClass

class AarPackageRClass(
  psiManager: PsiManager,
  packageName: String,
  private val aarResources: AbstractResourceRepository,
  private val resourceNamespace: ResourceNamespace
) : AndroidPackageRClassBase(psiManager, packageName) {

  override fun doGetInnerClasses(): Array<PsiClass> {
    return aarResources.getAvailableResourceTypes(resourceNamespace)
      .map { AarResourceTypeClass(this, it, resourceNamespace, aarResources) }
      .toTypedArray()
  }
}
