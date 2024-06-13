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

import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.databinding.util.DataBindingUtil
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.plugins.groovy.lang.psi.impl.stringValue

/**
 * A module-level service which provides utility functions for querying / caching data extracted
 * from data binding annotations.
 */
class DataBindingAnnotationsService(val module: Module) {
  companion object {
    @JvmStatic
    fun getInstance(facet: AndroidFacet) =
      facet.module.getService(DataBindingAnnotationsService::class.java)!!
  }

  // Cache the set of binding adapter attributes for fast lookup during XML markup and
  // autocompletion.
  // This cache is refreshed on every Java change.
  private val cachedBindingAdapterAttributes =
    CachedValuesManager.getManager(module.project)
      .createCachedValue(
        {
          CachedValueProvider.Result.create(
            computeBindingAdapterAttributes(),
            AndroidPsiUtils.getPsiModificationTrackerIgnoringXml(module.project),
          )
        },
        false,
      )

  /**
   * Returns the (possibly cached) set of attributes defined by `@BindingAdapter` annotations. Must
   * be called from a read action.
   */
  fun getBindingAdapterAttributes(): Set<String> {
    assert(ApplicationManager.getApplication().isReadAccessAllowed)
    return cachedBindingAdapterAttributes.value
  }

  private fun findJavaAndKotlinAnnotations(
    fqName: String,
    scope: GlobalSearchScope,
    project: Project,
  ): List<PsiAnnotation> {
    val facade = JavaPsiFacade.getInstance(project)
    val bindingAdapterAnnotation = facade.findClass(fqName, scope) ?: return emptyList()
    return AnnotatedElementsSearch.searchElements(
        bindingAdapterAnnotation,
        scope,
        PsiMethod::class.java,
      )
      .mapNotNull { annotatedMethod -> AnnotationUtil.findAnnotation(annotatedMethod, fqName) }
  }

  /**
   * Find all @BindingAdapter annotations in the given module, and compute the associated set of
   * binding adapter attribute names.
   */
  private fun computeBindingAdapterAttributes(): Set<String> {
    val androidFacet = AndroidFacet.getInstance(module) ?: return emptySet()
    val mode = DataBindingUtil.getDataBindingMode(androidFacet)
    if (mode == DataBindingMode.NONE) {
      return emptySet()
    }

    val scope = module.getModuleWithDependenciesAndLibrariesScope(false)
    val annotations = findJavaAndKotlinAnnotations(mode.bindingAdapter, scope, module.project)
    return annotations
      .asSequence()
      .mapNotNull { it.findAttributeValue("value") }
      .map { attributeValue ->
        if (attributeValue is PsiArrayInitializerMemberValue) {
          attributeValue.initializers.toList()
        } else {
          listOf(attributeValue)
        }
      }
      .flatten()
      .mapNotNull { it.stringValue() }
      .toSortedSet()
  }
}
