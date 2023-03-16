/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.actions.annotations

import com.android.tools.idea.lint.common.findAnnotation
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.annotationsByClassId
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.annotations.KtAnnotated as KtAnnotatedSymbol
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.idea.caches.resolve.analyze as analyzeK1
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.plugin.isK2Plugin
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

/**
 * Like [KtModifierListOwner.addAnnotation] in modifierListModifactor.kt,
 * but fixes the bug in that method where it will correctly insert the
 * specified [useSiteTarget] in added annotations, but if there is already
 * an annotation there with the same fully qualified name but with a
 * *different* use site, it will simply return.
 *
 * The body of this method is identical to the original one in
 * [KtModifierListOwner.addAnnotation], except that it calls
 * [KtModifierListOwner.findAnnotationWithUsageSite] instead of
 * [KtModifierListOwner.findAnnotation].
 */
// TODO(jsjeon): Once available, use upstream util in `AnnotationModificationUtils`
fun KtModifierListOwner.addAnnotationWithUsageSite(
  annotationFqName: FqName,
  annotationInnerText: String? = null,
  useSiteTarget: AnnotationUseSiteTarget?,
  whiteSpaceText: String = "\n",
  addToExistingAnnotation: ((KtAnnotationEntry) -> Boolean)? = null
): Boolean {
  val useSiteTargetPrefix = useSiteTarget?.let { "${it.renderName}:" } ?: ""
  val annotationText = when (annotationInnerText) {
    null -> "@${useSiteTargetPrefix}${annotationFqName.render()}"
    else -> "@${useSiteTargetPrefix}${annotationFqName.render()}($annotationInnerText)"
  }

  val psiFactory = KtPsiFactory(this)
  val modifierList = modifierList

  if (modifierList == null) {
    val addedAnnotation = addAnnotationEntry(psiFactory.createAnnotationEntry(annotationText))
    ShortenReferencesFacility.getInstance().shorten(addedAnnotation)
    return true
  }

  // This is the change from the original:
  // val entry = findAnnotation(annotationFqName)
  val entry = findAnnotationWithUsageSite(annotationFqName, useSiteTarget)

  if (entry == null) {
    // no annotation
    val newAnnotation = psiFactory.createAnnotationEntry(annotationText)
    val addedAnnotation = modifierList.addBefore(newAnnotation, modifierList.firstChild) as KtElement
    val whiteSpace = psiFactory.createWhiteSpace(whiteSpaceText)
    modifierList.addAfter(whiteSpace, addedAnnotation)

    ShortenReferencesFacility.getInstance().shorten(addedAnnotation)
    return true
  }

  if (addToExistingAnnotation != null) {
    return addToExistingAnnotation(entry)
  }

  return false
}

/**
 * Like [KtAnnotated.findAnnotation], but also takes an
 * [AnnotationUseSiteTarget] and filters the returned entry to one which
 * matches the specified use site.
 */
// TODO(jsjeon): Once available, use upstream util in `AnnotationModificationUtils`
@OptIn(KtAllowAnalysisOnEdt::class)
fun KtAnnotated.findAnnotationWithUsageSite(annotationFqName: FqName, useSiteTarget: AnnotationUseSiteTarget?): KtAnnotationEntry? {
  if (annotationEntries.isEmpty()) return null

  if (isK2Plugin()) {
    allowAnalysisOnEdt {
      analyze(this) {
        val annotatedSymbol = (this@findAnnotationWithUsageSite as? KtDeclaration)?.getSymbol() as? KtAnnotatedSymbol
        val annotations = annotatedSymbol?.annotationsByClassId(ClassId.topLevel(annotationFqName))
        return annotations?.firstOrNull { annoApp ->
          annoApp.useSiteTarget == useSiteTarget
        }?.psi as? KtAnnotationEntry
      }
    }
  } else {
    val context = analyzeK1(bodyResolveMode = BodyResolveMode.PARTIAL)
    val descriptor = context[BindingContext.DECLARATION_TO_DESCRIPTOR, this] ?: return null

    // Make sure all annotations are resolved
    descriptor.annotations.toList()

    return annotationEntries.firstOrNull { entry ->
      val annotationDescriptor = context.get(BindingContext.ANNOTATION, entry)
      // This extra filtering line is the change from the original:
      entry.useSiteTarget?.getAnnotationUseSiteTarget() == useSiteTarget &&
      annotationDescriptor?.fqName == annotationFqName
    }
  }
}