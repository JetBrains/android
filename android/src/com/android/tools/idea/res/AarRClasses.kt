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
import com.android.ide.common.resources.ResourceRepository
import com.android.ide.common.symbols.Symbol
import com.android.ide.common.symbols.SymbolIo
import com.android.ide.common.symbols.SymbolJavaType
import com.android.ide.common.symbols.SymbolTable
import com.android.ide.common.symbols.canonicalizeValueResourceName
import com.android.resources.ResourceType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiType
import org.jetbrains.android.augment.AndroidLightField.FieldModifier
import org.jetbrains.android.augment.InnerRClassBase
import org.jetbrains.android.augment.InnerRClassBase.buildResourceFields
import java.io.File
import java.io.IOException

/**
 * Top-level R class for an AARv2 used in namespaced mode, backed by the AAR [ResourceRepository] that's assumed not to change.
 *
 * It only contains entries for resources included in the library itself, not any of its dependencies.
 */
class NamespacedAarRClass(
  psiManager: PsiManager,
  private val packageName: String,
  private val aarResources: ResourceRepository,
  private val resourceNamespace: ResourceNamespace
) : AndroidRClassBase(psiManager, packageName) {
  override fun getQualifiedName(): String? = "$packageName.R"

  override fun doGetInnerClasses(): Array<PsiClass> {
    return aarResources.getResourceTypes(resourceNamespace)
      .mapNotNull { if (it.hasInnerClass) NamespacedAarInnerRClass(this, it, resourceNamespace, aarResources) else null }
      .toTypedArray()
  }
}

/**
 * Implementation of [InnerRClassBase] used by [NamespacedAarRClass].
 */
private class NamespacedAarInnerRClass(
  parent: PsiClass,
  resourceType: ResourceType,
  private val resourceNamespace: ResourceNamespace,
  private val aarResources: ResourceRepository
) : InnerRClassBase(parent, resourceType) {

  override fun doGetFields(): Array<PsiField> {
    return buildResourceFields(
      aarResources,
      resourceNamespace,
      FieldModifier.NON_FINAL,
      { _, _ -> true},
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
class NonNamespacedAarRClass(
  psiManager: PsiManager,
  private val packageName: String,
  symbolFile: File
) : AndroidRClassBase(psiManager, packageName) {

  override fun getQualifiedName(): String? = "$packageName.R"

  /**
   * [SymbolTable] read from the symbol file or an empty one if we failed to find or parse it.
   */
  private val symbolTable: SymbolTable = symbolFile.takeIf { it.exists() }?.let {
    try {
      SymbolIo.readFromAaptNoValues(it, packageName)
    }
    catch (e: IOException) {
      LOG.warn("Failed to build R class from ${symbolFile.path}", e)
      null
    }
  } ?: SymbolTable.builder().build()

  override fun doGetInnerClasses(): Array<out PsiClass> {
    return symbolTable
             .resourceTypes
             .map { NonNamespacedInnerRClass(this, it, symbolTable) }
             .toTypedArray()
  }

  companion object {
    private val LOG: Logger = Logger.getInstance(NonNamespacedAarRClass::class.java)
  }
}

/**
 * Implementation of [InnerRClassBase] used by [NonNamespacedAarRClass].
 */
private class NonNamespacedInnerRClass(
  parent: PsiClass,
  resourceType: ResourceType,
  private val symbolTable: SymbolTable
) : InnerRClassBase(parent, resourceType) {

  override fun doGetFields(): Array<PsiField> {
    return buildResourceFields(
      symbolTable.getSymbolByResourceType(resourceType).fold(HashMap<String, PsiType>()) { map, symbol ->
          map[symbol.canonicalName] = symbol.javaType.toPsiType()
          if (symbol is Symbol.StyleableSymbol) {
            for (childName in symbol.children) {
              map["${symbol.canonicalName}_${canonicalizeValueResourceName(childName)}"] = PsiType.INT
            }
          }
          map
        },
      resourceType,
      containingClass,
      FieldModifier.NON_FINAL
    )
  }

  private fun SymbolJavaType.toPsiType() = when (this) {
    SymbolJavaType.INT -> PsiType.INT
    SymbolJavaType.INT_LIST -> InnerRClassBase.INT_ARRAY
  }
}
