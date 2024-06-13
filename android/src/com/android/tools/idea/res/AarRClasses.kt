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
import com.android.resources.ResourceVisibility
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiManager
import org.jetbrains.android.AndroidResolveScopeEnlarger.Companion.AAR_ADDRESS_KEY
import org.jetbrains.android.AndroidResolveScopeEnlarger.Companion.LIGHT_CLASS_KEY
import org.jetbrains.android.augment.AndroidLightField.FieldModifier
import org.jetbrains.android.augment.InnerRClassBase
import org.jetbrains.android.augment.StyleableAttrFieldUrl
import java.io.File
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Top-level R class for an AARv2 used in namespaced mode, backed by the AAR [ResourceRepository]
 * that's assumed not to change.
 *
 * It only contains entries for resources included in the library itself, not any of its
 * dependencies.
 *
 * @param psiManager [PsiManager] of project used to create light elements
 * @param library [Library] of AAR which is added to module info of class so that it is found by
 *   Kotlin plugin
 * @param packageName Package of resources taken from AAR
 * @param aarResources The resources in the AAR
 * @param resourceNamespace Namespace taken from package name of AAR
 * @param aarAddress Address of an AAR eg. com.android.support:recyclerview-v7:28.0.0@aar
 */
class SmallAarRClass(
  psiManager: PsiManager,
  library: Library,
  packageName: String,
  private val aarResources: ResourceRepository,
  private val resourceNamespace: ResourceNamespace,
  aarAddress: String,
) : AndroidRClassBase(psiManager, packageName) {

  init {
    setModuleInfo(library)
    val lightVirtualFile = myFile.viewProvider.virtualFile
    lightVirtualFile.putUserData(LIGHT_CLASS_KEY, SmallAarRClass::class.java)
    lightVirtualFile.putUserData(AAR_ADDRESS_KEY, aarAddress)
  }

  override fun getQualifiedName(): String? = "$packageName.R"

  override fun doGetInnerClasses(): Array<PsiClass> {
    return aarResources
      .getResourceTypes(resourceNamespace)
      .mapNotNull {
        if (it.hasInnerClass) SmallAarInnerRClass(this, it, resourceNamespace, aarResources)
        else null
      }
      .toTypedArray()
  }

  override fun getInnerClassesDependencies(): ModificationTracker =
    ModificationTracker.NEVER_CHANGED
}

/** Implementation of [InnerRClassBase] used by [SmallAarRClass]. */
private class SmallAarInnerRClass(
  parent: PsiClass,
  resourceType: ResourceType,
  private val resourceNamespace: ResourceNamespace,
  private val aarResources: ResourceRepository,
) : InnerRClassBase(parent, resourceType) {

  override fun doGetFields(): Array<PsiField> {
    return buildResourceFields(
      aarResources,
      resourceNamespace,
      FieldModifier.NON_FINAL,
      resourceType,
      this,
    )
  }

  override val fieldsDependencies: ModificationTracker = ModificationTracker.NEVER_CHANGED
}

/**
 * Top-level R class for an AAR used in non-namespaced mode, created from the symbol file (`R.txt`).
 *
 * It contains entries for resources present in the AAR as well as all its dependencies, which is
 * how the build system generates the R class from the symbol file at build time.
 *
 * @param psiManager [PsiManager] of project used to create light elements
 * @param library [Library] of AAR which is added to module info of class so that it is found by
 *   Kotlin plugin
 * @param packageName Package of resources taken from AAR
 * @param symbolFile Symbol file (`R.txt`) containing information needed to generate non-namespaced
 *   R class
 * @param aarAddress Address of an AAR eg. com.android.support:recyclerview-v7:28.0.0@aar
 */
class TransitiveAarRClass(
  psiManager: PsiManager,
  library: Library,
  packageName: String,
  private val symbolFile: File,
  aarAddress: String,
) : AndroidRClassBase(psiManager, packageName) {

  init {
    setModuleInfo(library)
    val lightVirtualFile = myFile.viewProvider.virtualFile
    lightVirtualFile.putUserData(LIGHT_CLASS_KEY, TransitiveAarRClass::class.java)
    lightVirtualFile.putUserData(AAR_ADDRESS_KEY, aarAddress)
  }

  private val parsingLock = ReentrantLock()

  override fun getQualifiedName(): String? = "$packageName.R"

  override fun getInnerClasses(): Array<PsiClass> {
    return if (myClassCache.hasUpToDateValue()) {
      myClassCache.value
    } else {
      // Make sure we don't start parsing symbolFile from multiple threads in parallel.
      parsingLock.withLock { myClassCache.value }
    }
  }

  override fun doGetInnerClasses(): Array<out PsiClass> {
    val symbolTable =
      symbolFile
        .takeIf { it.exists() }
        ?.let {
          try {
            LOG.debug { "Parsing ${symbolFile.path}" }
            SymbolIo.readFromAaptNoValues(it, packageName)
          } catch (e: IOException) {
            LOG.warn("Failed to build R class from ${symbolFile.path}", e)
            null
          }
        } ?: SymbolTable.builder().build()

    return symbolTable.resourceTypes
      .map { TransitiveAarInnerRClass(this, it, symbolTable) }
      .toTypedArray()
  }

  companion object {
    private val LOG: Logger = Logger.getInstance(TransitiveAarRClass::class.java)
  }

  override fun getInnerClassesDependencies(): ModificationTracker =
    ModificationTracker.NEVER_CHANGED
}

/**
 * Implementation of [InnerRClassBase] used by [TransitiveAarRClass].
 *
 * It eagerly computes names and types of fields and releases the [SymbolTable].
 */
private class TransitiveAarInnerRClass(
  parent: PsiClass,
  resourceType: ResourceType,
  symbolTable: SymbolTable,
) : InnerRClassBase(parent, resourceType) {

  private val otherFields: Map<String, ResourceVisibility>
  private val styleableFields: Map<String, ResourceVisibility>
  private val styleableAttrFields: List<StyleableAttrFieldUrl>

  init {
    val otherFieldsBuilder = ImmutableMap.builder<String, ResourceVisibility>()
    val styleableFieldsBuilder = ImmutableMap.builder<String, ResourceVisibility>()
    val styleableAttrFieldsBuilder = ImmutableList.builder<StyleableAttrFieldUrl>()
    for (symbol in symbolTable.getSymbolByResourceType(resourceType)) {
      when (symbol.javaType) {
        SymbolJavaType.INT ->
          otherFieldsBuilder.put(symbol.canonicalName, symbol.resourceVisibility)
        SymbolJavaType.INT_LIST -> {
          styleableFieldsBuilder.put(symbol.canonicalName, symbol.resourceVisibility)
          (symbol as? Symbol.StyleableSymbol)?.children?.forEach {
            val (packageName, attrName) = getNameComponents(it)
            val attrNamespace =
              if (packageName.isNullOrEmpty()) {
                ResourceNamespace.RES_AUTO
              } else {
                ResourceNamespace.fromPackageName(packageName)
              }
            styleableAttrFieldsBuilder.add(
              StyleableAttrFieldUrl(
                ResourceReference(
                  ResourceNamespace.RES_AUTO,
                  ResourceType.STYLEABLE,
                  symbol.canonicalName,
                ),
                ResourceReference.attr(attrNamespace, attrName),
              )
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
   * The R.txt for an Aar gets transformed into a [SymbolTable]. Example R.txt: int[] styleable
   * NewView { 0x0101016e, 0x01010393} int styleable NewView_android_drawableBottom 0 int styleable
   * NewView_otherAttr 1 The styleable attr in the symbol table are available as a list of attrs for
   * each styleables, in the form: ${package_name}:${attr_name} eg. android:drawableBottom or if
   * there is no package name, just the attr_name eg. otherAttr
   *
   * Note that there is no way to know from the R.txt alone, whether a styleable attr is an
   * overridden framework attr, or simply an attr with the android_ prefix.
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
      FieldModifier.NON_FINAL,
    )
  }

  override val fieldsDependencies: ModificationTracker = ModificationTracker.NEVER_CHANGED
}
