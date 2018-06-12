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
import com.android.ide.common.symbols.SymbolIo
import com.android.ide.common.symbols.SymbolJavaType
import com.android.ide.common.symbols.SymbolTable
import com.android.resources.ResourceType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiType
import org.jetbrains.android.augment.AndroidLightField.FieldModifier
import org.jetbrains.android.augment.ResourceTypeClassBase
import java.io.File

/**
 * Top-level R class for an AARv2 used in namespaced mode, backed by the AAR's [AbstractResourceRepository] that's assumed not to change.
 *
 * It only contains entries for resources included in the library itself, not any of its dependencies.
 */
class NamespacedAarPackageRClass(
  psiManager: PsiManager,
  packageName: String,
  private val aarResources: AbstractResourceRepository,
  private val resourceNamespace: ResourceNamespace
) : AndroidPackageRClassBase(psiManager, packageName) {

  override fun doGetInnerClasses(): Array<PsiClass> {
    return aarResources.getAvailableResourceTypes(resourceNamespace)
      .map { NamespacedAarResourceTypeClass(this, it, resourceNamespace, aarResources) }
      .toTypedArray()
  }
}

/**
 * Implementation of [ResourceTypeClassBase] used by [NamespacedAarPackageRClass].
 */
private class NamespacedAarResourceTypeClass(
  parent: PsiClass,
  resourceType: ResourceType,
  private val resourceNamespace: ResourceNamespace,
  private val aarResources: AbstractResourceRepository
) : ResourceTypeClassBase(parent, resourceType) {

  override fun doGetFields(): Array<PsiField> {
    return buildResourceFields(
      null,
      aarResources,
      resourceNamespace,
      FieldModifier.NON_FINAL,
      resourceType,
      containingClass
    )
  }
}

/**
 * Top-level R class for an AAR used in non-namespaced mode, created from the symbol file (`R.txt`).
 *
 * It contains entries for resources present in the AAR as well as all its dependencies, which is how the build system generates the R class
 * from the symbol file at build time.
 */
class NonNamespacedAarPackageRClass(
  psiManager: PsiManager,
  packageName: String,
  symbolFile: File
) : AndroidPackageRClassBase(psiManager, packageName) {

  /**
   * [SymbolTable] read from the symbol file.
   */
  private val symbolTable: SymbolTable? = symbolFile.takeIf { it.exists() }?.let { SymbolIo.readFromAapt(it, packageName) }

  override fun doGetInnerClasses(): Array<out PsiClass> {
    return symbolTable
             ?.resourceTypes
             ?.map { NonNamespacedResourceTypeClass(this, it, symbolTable) }
             ?.toTypedArray()
           ?: PsiClass.EMPTY_ARRAY
  }
}

/**
 * Implementation of [ResourceTypeClassBase] used by [NonNamespacedAarPackageRClass].
 */
private class NonNamespacedResourceTypeClass(
  parent: PsiClass,
  resourceType: ResourceType,
  private val symbolTable: SymbolTable
) : ResourceTypeClassBase(parent, resourceType) {

  override fun doGetFields(): Array<PsiField> {
    return buildResourceFields(
      symbolTable.getSymbolByResourceType(resourceType).associateBy({ it.name }, { it.javaType.toPsiType() }),
      resourceType,
      containingClass,
      FieldModifier.NON_FINAL
    )
  }

  private fun SymbolJavaType.toPsiType() = when (this) {
    SymbolJavaType.INT -> PsiType.INT
    SymbolJavaType.INT_LIST -> ResourceTypeClassBase.INT_ARRAY
  }
}
