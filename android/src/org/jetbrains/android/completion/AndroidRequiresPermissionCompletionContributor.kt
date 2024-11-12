/*
 * Copyright (C) 2024 The Android Open Source Project
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
package org.jetbrains.android.completion

import com.android.tools.idea.kotlin.fqNameMatches
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionWeigher
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parents
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference.ShorteningMode
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtValueArgument

private const val MANIFEST_PERMISSION_FQ_NAME = "android.Manifest.permission"
private const val REQUIRES_PERMISSION_FQ_NAME = "androidx.annotation.RequiresPermission"

/**
 * Key used to identify lookup elements inserted by this contributor. This is used when weighing
 * elements to quickly promote them to the top of the list.
 */
private val LOOKUP_ELEMENT_KEY =
  Key.create<Boolean>(
    "org.jetbrains.android.completion.AndroidRequiresPermissionCompletionContributor"
  )

/**
 * Base class completion contributor for suggesting permissions constants from
 * android.Manifest.permission when within the androidx.annotation.RequiresPermission attribute.
 */
sealed class AndroidRequiresPermissionCompletionContributor : CompletionContributor() {
  protected abstract val insertHandler: InsertHandler<LookupElement>

  override fun fillCompletionVariants(
    parameters: CompletionParameters,
    result: CompletionResultSet,
  ) {
    // Check that completion is happening inside a RequiresPermission annotation.
    val position = parameters.position
    if (!isRequiresPermissionAnnotation(position)) return

    ProgressManager.checkCanceled()

    // Get the list of available permissions.
    val permissionClass =
      JavaPsiFacade.getInstance(position.project)
        .findClass(MANIFEST_PERMISSION_FQ_NAME, position.resolveScope) ?: return
    val permissionFields = permissionClass.fields

    ProgressManager.checkCanceled()

    // Add all the permissions as lookup elements.
    for (field in permissionFields) {
      val lookupElement =
        LookupElementBuilder.create(field)
          .withInsertHandler(insertHandler)
          .appendTailText(" ($MANIFEST_PERMISSION_FQ_NAME)", true)
          .apply { putUserData(LOOKUP_ELEMENT_KEY, true) }
      result.addElement(lookupElement)
    }

    // Pass through any other elements that don't also correspond to the same permissions.
    result.runRemainingContributors(parameters) {
      if (it.lookupElement.psiElement?.navigationElement !in permissionFields) {
        result.passResult(it)
      }
    }
  }

  protected abstract fun isRequiresPermissionAnnotation(position: PsiElement): Boolean
}

class AndroidKotlinRequiresPermissionCompletionContributor :
  AndroidRequiresPermissionCompletionContributor() {

  override fun isRequiresPermissionAnnotation(position: PsiElement): Boolean {
    val valueArgument = position.parentOfType<KtValueArgument>(true) ?: return false
    val annotationEntry = valueArgument.parentOfType<KtAnnotationEntry>() ?: return false

    return annotationEntry.fqNameMatches(REQUIRES_PERMISSION_FQ_NAME)
  }

  override val insertHandler =
    object : InsertHandler<LookupElement> {
      override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val target = item.psiElement ?: return
        val refExp =
          context.file.findElementAt(context.startOffset)?.parent as? KtReferenceExpression
            ?: return
        (refExp.mainReference as? KtSimpleNameReference)?.bindToElement(
          target,
          ShorteningMode.FORCED_SHORTENING,
        )
      }
    }
}

class AndroidJavaRequiresPermissionCompletionContributor :
  AndroidRequiresPermissionCompletionContributor() {

  override fun isRequiresPermissionAnnotation(position: PsiElement): Boolean {
    val annotation = position.parentOfType<PsiAnnotation>() ?: return false
    return annotation.qualifiedName == REQUIRES_PERMISSION_FQ_NAME
  }

  override val insertHandler =
    object : InsertHandler<LookupElement> {
      override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val target = item.psiElement as? PsiField ?: return

        val expr = context.getParent() as? PsiReferenceExpression ?: return

        expr.bindToElement(target)
        context.getMaximalParentOfType<PsiReferenceExpression>()?.let {
          JavaCodeStyleManager.getInstance(context.project).shortenClassReferences(it)
        }
      }

      private fun InsertionContext.getParent(): PsiElement? =
        file.findElementAt(startOffset)?.parent

      private inline fun <reified T : PsiElement> InsertionContext.getMaximalParentOfType() =
        getParent()?.parents(true)?.firstOrNull { it.parent !is T }
    }
}

/** Completion weigher that ranks permissions entries higher than other entries. */
class AndroidRequiresPermissionCompletionWeigher : CompletionWeigher() {
  override fun weigh(element: LookupElement, location: CompletionLocation): Comparable<Nothing> {
    return if (element.getUserData(LOOKUP_ELEMENT_KEY) == true) 1 else 0
  }
}
