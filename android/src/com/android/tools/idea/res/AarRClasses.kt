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
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.resources.ResourceRepository
import com.android.ide.common.symbols.Symbol
import com.android.ide.common.symbols.SymbolIo
import com.android.ide.common.symbols.SymbolJavaType
import com.android.ide.common.symbols.SymbolTable
import com.android.resources.ResourceType
import com.google.common.collect.ImmutableList
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiManager
import org.jetbrains.android.augment.AndroidLightField.FieldModifier
import org.jetbrains.android.augment.InnerRClassBase
import org.jetbrains.android.augment.InnerRClassBase.buildResourceFields
import org.jetbrains.android.augment.StyleableAttrFieldUrl
import java.io.File
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Top-level R class for an AARv2 used in namespaced mode, backed by the AAR [ResourceRepository] that's assumed not to change.
 *
 * It only contains entries for resources included in the library itself, not any of its dependencies.
 */
class SmallAarRClass(
  psiManager: PsiManager,
  library: Library,
  private val packageName: String,
  private val aarResources: ResourceRepository,
  private val resourceNamespace: ResourceNamespace
) : AndroidRClassBase(psiManager, packageName) {

  init {
    setModuleInfo(library)
  }

  override fun getQualifiedName(): String? = "$packageName.R"

  override fun doGetInnerClasses(): Array<PsiClass> {
    return aarResources.getResourceTypes(resourceNamespace)
      .mapNotNull { if (it.hasInnerClass) SmallAarInnerRClass(this, it, resourceNamespace, aarResources) else null }
      .toTypedArray()
  }

  override fun getInnerClassesDependencies(): ModificationTracker = ModificationTracker.NEVER_CHANGED
}

/**
 * Implementation of [InnerRClassBase] used by [SmallAarRClass].
 */
private class SmallAarInnerRClass(
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
      this
    )
  }

  override fun getFieldsDependencies(): ModificationTracker = ModificationTracker.NEVER_CHANGED
}

/**
 * Top-level R class for an AAR used in non-namespaced mode, created from the symbol file (`R.txt`).
 *
 * It contains entries for resources present in the AAR as well as all its dependencies, which is how the build system generates the R class
 * from the symbol file at build time.
 */
class TransitiveAarRClass(
  psiManager: PsiManager,
  library: Library,
  private val packageName: String,
  private val symbolFile: File
) : AndroidRClassBase(psiManager, packageName) {

  init {
    setModuleInfo(library)
  }

  private val parsingLock = ReentrantLock()

  override fun getQualifiedName(): String? = "$packageName.R"

  override fun getInnerClasses(): Array<PsiClass> {
    return if (myClassCache.hasUpToDateValue()) {
      myClassCache.value
    }
    else {
      // Make sure we don't start parsing symbolFile from multiple threads in parallel.
      parsingLock.withLock {
        myClassCache.value
      }
    }
  }

  override fun doGetInnerClasses(): Array<out PsiClass> {
    val symbolTable = symbolFile.takeIf { it.exists() }?.let {
      try {
        LOG.debug { "Parsing ${symbolFile.path}" }
        SymbolIo.readFromAaptNoValues(it, packageName)
      }
      catch (e: IOException) {
        LOG.warn("Failed to build R class from ${symbolFile.path}", e)
        null
      }
    } ?: SymbolTable.builder().build()

    return symbolTable
             .resourceTypes
             .map { TransitiveAarInnerRClass(this, it, symbolTable) }
             .toTypedArray()
  }

  companion object {
    private val LOG: Logger = Logger.getInstance(TransitiveAarRClass::class.java)
  }

  override fun getInnerClassesDependencies(): ModificationTracker = ModificationTracker.NEVER_CHANGED
}

/**
 * Implementation of [InnerRClassBase] used by [TransitiveAarRClass].
 *
 * It eagerly computes names and types of fields and releases the [SymbolTable].
 */
private class TransitiveAarInnerRClass(
  parent: PsiClass,
  resourceType: ResourceType,
  symbolTable: SymbolTable
) : InnerRClassBase(parent, resourceType) {

  private val styleableFields: ImmutableList<String>
  private val styleableAttrFields: ImmutableList<StyleableAttrFieldUrl>
  private val otherFields: ImmutableList<String>

  init {
    val styleableFieldsBuilder = ImmutableList.builder<String>()
    val styleableAttrFieldsBuilder = ImmutableList.builder<StyleableAttrFieldUrl>()
    val otherFieldsBuilder = ImmutableList.builder<String>()
    for (symbol in symbolTable.getSymbolByResourceType(resourceType)) {
      when (symbol.javaType) {
        SymbolJavaType.INT -> otherFieldsBuilder.add(symbol.canonicalName)
        SymbolJavaType.INT_LIST -> {
          styleableFieldsBuilder .add(symbol.canonicalName)
          (symbol as? Symbol.StyleableSymbol)?.children?.forEach {
            val (packageName, attrName) = getNameComponents(it)
            val attrNamespace = if (packageName.isNullOrEmpty()) {
              ResourceNamespace.RES_AUTO
            } else {
              ResourceNamespace.fromPackageName(packageName)
            }
            styleableAttrFieldsBuilder.add(StyleableAttrFieldUrl(
              ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.STYLEABLE, symbol.canonicalName),
              ResourceReference.attr(attrNamespace, attrName))
            )
          }
        }
        else -> error("Unknown symbol type ${symbol.javaType}")
      }
    }

    otherFields = otherFieldsBuilder.build()
    styleableFields = styleableFieldsBuilder.build()
    styleableAttrFields = styleableAttrFieldsBuilder.build()
  }

  /**
   * The R.txt for an Aar gets transformed into a [SymbolTable].
   * Example R.txt:
   *    int[] styleable NewView { 0x0101016e, 0x01010393}
   *    int styleable NewView_android_drawableBottom 0
   *    int styleable NewView_otherAttr 1
   * The styleable attr in the symbol table are available as a list of attrs for each styleables, in the form:
   *    ${package_name}:${attr_name} eg. android:drawableBottom
   * or if there is no package name, just the attr_name eg. otherAttr
   *
   * Note that there is no way to know from the R.txt alone, whether a styleable attr is an overridden framework attr, or simply an attr
   * with the android_ prefix.
   */
  private fun getNameComponents(name: String): Pair<String?, String> {
    // This only work on non-namespaced aars, or where the only namespace used is "android".
    val listOfComponents = name.split(':')
    return when (listOfComponents.size) {
      2 -> Pair(listOfComponents[0], listOfComponents[1])
      else -> Pair(null, name)
    }
  }

  override fun doGetFields(): Array<PsiField> {
    return buildResourceFields(
      otherFields,
      styleableFields,
      styleableAttrFields,
      resourceType,
      this,
      FieldModifier.NON_FINAL
    )
  }

  override fun getFieldsDependencies(): ModificationTracker = ModificationTracker.NEVER_CHANGED
}
