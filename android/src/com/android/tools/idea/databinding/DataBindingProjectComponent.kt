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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiPackage
import com.intellij.psi.impl.file.PsiPackageImpl
import com.intellij.psi.impl.java.stubs.index.JavaAnnotationIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.idea.stubindex.KotlinAnnotationsIndex
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.evaluateString
import org.jetbrains.uast.toUElementOfType
import java.util.ArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * Keeps data binding information related to a project
 */
class DataBindingProjectComponent(val project: Project) : ModificationTracker {
  private val dataBindingEnabledModules: CachedValue<Array<AndroidFacet>>
  private val modificationCount = AtomicLong(0)
  private val dataBindingPsiPackages = Maps.newConcurrentMap<String, PsiPackage>()

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
  }

  fun hasAnyDataBindingEnabledFacet(): Boolean = getDataBindingEnabledFacets().isNotEmpty()

  fun getDataBindingEnabledFacets(): Array<AndroidFacet> = dataBindingEnabledModules.value

  override fun getModificationCount(): Long = modificationCount.toLong()

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

  private fun findJavaAndKotlinAnnotations(fqName: String, scope: GlobalSearchScope, project: Project): Sequence<UAnnotation> {
    // We avoid using AnnotatedElementsSearch here, because that returns annotated elements rather than the
    // annotations themselves, and for Kotlin it also wraps everything in KtLightElements.
    val shortName = fqName.substringAfterLast('.')
    val javaAnnotations: Sequence<PsiElement> = JavaAnnotationIndex.getInstance().get(shortName, project, scope).asSequence()
    val kotlinAnnotations: Sequence<PsiElement> = KotlinAnnotationsIndex.getInstance().get(shortName, project, scope).asSequence()
    return (javaAnnotations + kotlinAnnotations)
      .mapNotNull { it.toUElementOfType<UAnnotation>() }
      .filter { it.qualifiedName == fqName }
  }

  /**
   * Find all @BindingAdapter annotations in the given module, and compute
   * the associated set of binding adapter attribute names.
   */
  private fun computeBindingAdapterAttributes(module: Module): Set<String> {
    val androidFacet = AndroidFacet.getInstance(module) ?: return emptySet()
    val mode = DataBindingUtil.getDataBindingMode(androidFacet)
    if (mode == DataBindingMode.NONE) {
      return emptySet()
    }

    val scope = androidFacet.module.getModuleWithDependenciesAndLibrariesScope(false)
    val annotations = findJavaAndKotlinAnnotations(mode.bindingAdapter, scope, project)

    val allAttributes = mutableSetOf<String>()
    for (annotation in annotations) {
      val value = annotation.findDeclaredAttributeValue("value") ?: continue
      val expressions = when (value) {
        is UCallExpression -> value.valueArguments.asSequence() // Unwraps array initializers.
        else -> sequenceOf(value)
      }
      expressions
        .mapNotNull { it.evaluateString() }
        .forEach { allAttributes.add(it) }
    }

    return allAttributes
  }

  /**
   * Returns the (possibly cached) set of attributes defined by `@BindingAdapter` annotations.
   * Must be called from a read action.
   */
  fun getBindingAdapterAttributes(module: Module): Set<String> {
    assert(ApplicationManager.getApplication().isReadAccessAllowed)
    // Cache the set of binding adapter attributes for fast lookup during XML markup and autocompletion.
    // This cache is refreshed on every Java change.
    return CachedValuesManager.getManager(module.project).getCachedValue(module) {
      CachedValueProvider.Result.create(
        computeBindingAdapterAttributes(module),
        PsiModificationTracker.MODIFICATION_COUNT
      )
    }
  }
}
