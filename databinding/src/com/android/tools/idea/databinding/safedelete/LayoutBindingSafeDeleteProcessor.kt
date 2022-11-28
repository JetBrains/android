/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.databinding.safedelete

import com.android.resources.ResourceFolderType
import com.android.tools.idea.databinding.module.LayoutBindingModuleCache
import com.android.tools.idea.res.getFolderType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.safeDelete.NonCodeUsageSearchInfo
import com.intellij.refactoring.safeDelete.SafeDeleteProcessor
import com.intellij.refactoring.safeDelete.SafeDeleteProcessorDelegate
import com.intellij.usageView.UsageInfo
import org.jetbrains.android.AndroidResourceFileSafeDeleteProcessor
import org.jetbrains.android.facet.AndroidFacet

class LayoutBindingSafeDeleteProcessor : SafeDeleteProcessorDelegate {
  // Our safe delete processor should act like a regular android layout safe delete processor with
  // a bit of extra handling for binding references. We can delegate most of the work.
  private val delegateProcessor = SafeDeleteProcessorDelegate.EP_NAME.findExtensionOrFail(
    AndroidResourceFileSafeDeleteProcessor::class.java)

  override fun handlesElement(element: PsiElement): Boolean {
    if (!delegateProcessor.handlesElement(element)) return false

    // If AndroidResourceFileSafeDeleteProcessor handles this, it should imply that `element` is a
    // file like "layout/xyz.xml" or "values/strings.xml". In other words, a PsiFile inside a resource
    // directory inside an Android module.
    val resourceFile = element as? PsiFile ?: return false
    if (getFolderType(resourceFile) != ResourceFolderType.LAYOUT) return false

    val facet = AndroidFacet.getInstance(element) ?: return false
    val cache = LayoutBindingModuleCache.getInstance(facet)

    return cache.bindingLayoutGroups.asSequence()
      .flatMap { it.layouts.asSequence() }
      .any { layout -> layout.file == resourceFile.virtualFile }
  }

  override fun findUsages(element: PsiElement,
                          allElementsToDelete: Array<PsiElement>,
                          result: MutableList<UsageInfo>): NonCodeUsageSearchInfo? {
    val resourceFile = element as PsiFile
    val facet = AndroidFacet.getInstance(element)!!
    val cache = LayoutBindingModuleCache.getInstance(facet)

    cache.bindingLayoutGroups
      .filter { group -> group.layouts.any { layout -> layout.file == resourceFile.virtualFile } }
      .flatMap { group -> cache.getLightBindingClasses(group) }
      .forEach { bindingClass -> SafeDeleteProcessor.findGenericElementUsages(bindingClass, result, allElementsToDelete) }

    return delegateProcessor.findUsages(element, allElementsToDelete, result)
  }

  override fun getElementsToSearch(element: PsiElement,
                                   allElementsToDelete: Collection<PsiElement>): Collection<PsiElement>? {
    return delegateProcessor.getElementsToSearch(element, allElementsToDelete)
  }

  override fun getAdditionalElementsToDelete(element: PsiElement,
                                             allElementsToDelete: Collection<PsiElement>,
                                             askUser: Boolean): Collection<PsiElement>? {
    return delegateProcessor.getAdditionalElementsToDelete(element, allElementsToDelete, askUser)
  }

  override fun findConflicts(element: PsiElement, allElementsToDelete: Array<PsiElement>): Collection<String>? {
    return delegateProcessor.findConflicts(element, allElementsToDelete)
  }

  override fun preprocessUsages(project: Project, usages: Array<UsageInfo>): Array<UsageInfo?>? {
    return delegateProcessor.preprocessUsages(project, usages)
  }

  override fun prepareForDeletion(element: PsiElement) {
    delegateProcessor.prepareForDeletion(element)
  }

  override fun isToSearchInComments(element: PsiElement): Boolean {
    return delegateProcessor.isToSearchInComments(element)
  }

  override fun setToSearchInComments(element: PsiElement, enabled: Boolean) {
    delegateProcessor.setToSearchInComments(element, enabled)
  }

  override fun isToSearchForTextOccurrences(element: PsiElement): Boolean {
    return delegateProcessor.isToSearchForTextOccurrences(element)
  }

  override fun setToSearchForTextOccurrences(element: PsiElement, enabled: Boolean) {
    return delegateProcessor.setToSearchForTextOccurrences(element, enabled)
  }
}