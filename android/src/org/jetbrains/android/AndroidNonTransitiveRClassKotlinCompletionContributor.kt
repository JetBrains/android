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
package org.jetbrains.android

import com.android.SdkConstants.R_CLASS
import com.android.tools.idea.kotlin.getPreviousInQualifiedChain
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.res.AndroidRClassBase
import com.android.tools.idea.res.ModuleRClass
import com.android.tools.idea.res.ProjectLightResourceClassService
import com.android.tools.idea.util.androidFacet
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.VariableLookupItem
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference
import com.intellij.psi.util.parentOfType
import com.intellij.util.ProcessingContext
import org.jetbrains.android.augment.ResourceRepositoryInnerRClass
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference.ShorteningMode.NO_SHORTENING
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance

/**
 * Provides references to all accessible resource fields, when a user types "R.resourceType." for kotlin files.
 */
class AndroidNonTransitiveRClassKotlinCompletionContributor : CompletionContributor() {

  init {
    extend(
      CompletionType.BASIC,
      PlatformPatterns.psiElement()
        .withSuperParent(1, KtSimpleNameExpression::class.java)
        .withSuperParent(2, KtDotQualifiedExpression::class.java),
      object : CompletionProvider<CompletionParameters>(){
        override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
          addNonLocalResourceFields(parameters, result)
        }
      }
    )
  }

 fun addNonLocalResourceFields(parameters: CompletionParameters, result: CompletionResultSet) {
   val element = parameters.position
   val facet = element.androidFacet ?: return
   val moduleSystem = element.getModuleSystem() ?: return
   if (moduleSystem.isRClassTransitive) return
   val expression = element.parent as? KtSimpleNameExpression ?: return
   if (!matchesRequiredFormat(expression)) return
   val innerRClassElement = expression.getPreviousInQualifiedChain() as? KtSimpleNameExpression ?: return
   val references = (innerRClassElement as PsiElement).references
   if (references.isEmpty()) return
   val innerRClass = PsiMultiReference(references, references.last().element).resolve() as? ResourceRepositoryInnerRClass ?: return
   val moduleRClass = innerRClass.containingClass as? ModuleRClass ?: return
   val rClassesAccessibleFromModule = ProjectLightResourceClassService.getInstance(element.project)
     .getLightRClassesAccessibleFromModule(facet.module, false)
     .filter { !(it is ModuleRClass && it.facet == moduleRClass.facet) }
   val list = rClassesAccessibleFromModule
     .flatMap { it.findInnerClassByName(innerRClass.resourceType.getName(), false)?.allFields?.toList() ?: emptyList() }
     .map { NonTransitiveResourceFieldLookupElement(it) }
   result.addAllElements(list)
  }

  /**
   * Verifies the kotlin reference matches "R.resourceType.%resourceName%" without resolving, no package prefix allowed.
   */
  private fun matchesRequiredFormat(expression: KtSimpleNameExpression): Boolean {
    val innerRClassReference = expression.getPreviousInQualifiedChain() as? KtSimpleNameExpression ?: return false
    val rClassReference = innerRClassReference.getPreviousInQualifiedChain() as? KtSimpleNameExpression ?: return false
    val possiblePackageReference = rClassReference.getPreviousInQualifiedChain()
    return (rClassReference as PsiElement).text == R_CLASS && possiblePackageReference == null
  }
}

/**
 * Lookup element for resources shown on R.resourceType. elements that are not from the current Module R class
 *
 * Custom insert handler is provided to insert the correct package name of the R class of the selected resource.
 */
class NonTransitiveResourceFieldLookupElement(val element: PsiField) : VariableLookupItem(element) {
  override fun handleInsert(context: InsertionContext) {
    super.handleInsert(context)

    // All Kotlin insertion handlers do this, possibly to post-process adding a new import in the call to super above.
    val psiDocumentManager = PsiDocumentManager.getInstance(element.project)
    psiDocumentManager.commitAllDocuments()
    psiDocumentManager.doPostponedOperationsAndUnblockDocument(context.document)

    val firstChild = context.file.findElementAt(context.startOffset)
                       ?.parentOfType<KtExpression>()
                       ?.getPreviousInQualifiedChain()
                       ?.getPreviousInQualifiedChain() as? PsiElement ?: return
    val rClass = element.parent.parent as? AndroidRClassBase ?: return
    if (firstChild.text == R_CLASS) {
      (firstChild.references?.firstIsInstance<KtSimpleNameReference>())?.bindToElement(rClass, NO_SHORTENING)
    }
  }
}