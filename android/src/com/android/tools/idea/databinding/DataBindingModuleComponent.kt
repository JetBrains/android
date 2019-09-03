/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tools.idea.databinding.util.DataBindingUtil
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.PsiModificationTrackerImpl
import com.intellij.psi.impl.java.stubs.index.JavaAnnotationIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.stubindex.KotlinAnnotationsIndex
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.evaluateString
import org.jetbrains.uast.toUElementOfType

/**
 * Data binding utilities that apply to each module
 */
class DataBindingModuleComponent(val module: Module) {

  // Cache the set of binding adapter attributes for fast lookup during XML markup and autocompletion.
  // This cache is refreshed on every Java change.
  private val cachedBindingAdapterAttributes = CachedValuesManager.getManager(module.project).createCachedValue({
    CachedValueProvider.Result.create(
      computeBindingAdapterAttributes(),
      (PsiManager.getInstance(module.project).modificationTracker as PsiModificationTrackerImpl).forLanguages {
        lang -> lang.`is`(JavaLanguage.INSTANCE) || lang.`is`(KotlinLanguage.INSTANCE)
      })
  }, false)

  /**
   * Returns the (possibly cached) set of attributes defined by `@BindingAdapter` annotations.
   * Must be called from a read action.
   */
  fun getBindingAdapterAttributes(): Set<String> {
    assert(ApplicationManager.getApplication().isReadAccessAllowed)
    return cachedBindingAdapterAttributes.value
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
  private fun computeBindingAdapterAttributes(): Set<String> {
    val androidFacet = AndroidFacet.getInstance(module) ?: return emptySet()
    val mode = DataBindingUtil.getDataBindingMode(androidFacet)
    if (mode == DataBindingMode.NONE) {
      return emptySet()
    }

    val scope = module.getModuleWithDependenciesAndLibrariesScope(false)
    val annotations = findJavaAndKotlinAnnotations(mode.bindingAdapter, scope, module.project)

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
}
