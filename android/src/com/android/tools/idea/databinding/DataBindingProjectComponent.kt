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
package com.android.tools.idea.databinding

import com.google.common.collect.Maps
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiLiteral
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiPackage
import com.intellij.psi.impl.file.PsiPackageImpl
import com.intellij.psi.search.ProjectScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.ParameterizedCachedValue
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.android.facet.AndroidFacet
import java.util.ArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * Keeps data binding information related to a project
 */
class DataBindingProjectComponent(val project: Project) : ModificationTracker {
  private val dataBindingEnabledModules: CachedValue<Array<AndroidFacet>>
  private val bindingAdapterAnnotations: ParameterizedCachedValue<Collection<PsiModifierListOwner>, Module>
  private val modificationCount = AtomicLong(0)
  private val dataBindingPsiPackages = Maps.newConcurrentMap<String, PsiPackage>()

  val dataBindingEnabledFacets: Array<AndroidFacet>
    get() = dataBindingEnabledModules.value

  init {
    dataBindingEnabledModules = CachedValuesManager.getManager(project).createCachedValue({
      val modules = ModuleManager.getInstance(project).modules
      val facets = ArrayList<AndroidFacet>()
      for (module in modules) {
        val facet = AndroidFacet.getInstance(module) ?: continue
        if (DataBindingUtil.isDataBindingEnabled(facet)) {
          facets.add(facet)
        }
      }

      modificationCount.incrementAndGet()
      CachedValueProvider.Result.create(
        facets.toTypedArray(),
        DataBindingUtil.getDataBindingEnabledTracker(),
        ModuleManager.getInstance(project))
    }, false)

    bindingAdapterAnnotations = CachedValuesManager.getManager(project).createParameterizedCachedValue({ module ->
       val androidFacet = AndroidFacet.getInstance(module)
       val adapterClass: PsiClass?
       if (androidFacet != null) {
         val facade = JavaPsiFacade.getInstance(project)
         val mode = DataBindingUtil.getDataBindingMode(androidFacet)
         if (mode == DataBindingMode.NONE) {
           adapterClass = null
         }
         else {
           adapterClass = facade.findClass(mode.bindingAdapter, module.getModuleWithDependenciesAndLibrariesScope(false))
         }
       }
       else {
         adapterClass = null
       }
       val psiElements: Collection<PsiModifierListOwner>
       if (adapterClass == null) {
         psiElements = emptyList()
       }
       else {
         // ProjectScope used. ModuleWithDependencies does not seem to work
         psiElements = AnnotatedElementsSearch.searchElements(
           adapterClass,
           ProjectScope.getAllScope(project),
           PsiMethod::class.java).findAll()
       }

       // Cached value that will be refreshed in every Java change
       CachedValueProvider.Result.create(psiElements,
                 PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT,
                 ModuleManager.getInstance(project))
     }, false)
  }

  fun hasAnyDataBindingEnabledFacet(): Boolean {
    return dataBindingEnabledFacets.isNotEmpty()
  }

  override fun getModificationCount(): Long {
    return modificationCount.toLong()
  }

  /**
   * Returns a [PsiPackage] instance for the given package name.
   *
   * If it does not exist in the cache, a new one is created.
   *
   * @param packageName The qualified package name
   * @return A [PsiPackage] that represents the given qualified name
   */
  @Synchronized
  fun getOrCreateDataBindingPsiPackage(packageName: String): PsiPackage {
    return dataBindingPsiPackages.computeIfAbsent(packageName) {
      object : PsiPackageImpl(PsiManager.getInstance(project), packageName) {
        override fun isValid(): Boolean = true
      }
    }
  }

  /**
   * Convert the passed annotation initialization into a [Iterable] of [PsiLiteral] values
   */
  private fun getPsiLiterals(annotationMemberValue: PsiAnnotationMemberValue): Iterable<PsiLiteral> {
    if (annotationMemberValue is PsiArrayInitializerMemberValue) {
      return annotationMemberValue.initializers
        .filter { PsiLiteral::class.java.isInstance(it) }
        .map { PsiLiteral::class.java.cast(it) }
    }

    return if (annotationMemberValue is PsiLiteral) {
      listOf(annotationMemberValue)
    }
    else listOf()
  }

  /**
   * Returns the list of attributes defined by `@BindingAdapter` annotations
   */
  fun getBindingAdapterAttributes(module: Module): List<String> {
    return bindingAdapterAnnotations.getValue(module)
      .mapNotNull { it.modifierList }
      .flatMap { it.annotations.asIterable() }
      .mapNotNull { it.findAttributeValue("value") }
      .flatMap { getPsiLiterals(it) }
      .mapNotNull { it.value }
      .distinct()
      .map { it.toString() }
  }
}
