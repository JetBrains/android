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
package com.android.tools.idea.lint.common

import com.android.tools.lint.detector.api.Context
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.KtAnnotated as KtAnnotatedSymbol
import org.jetbrains.kotlin.analysis.api.annotations.annotationsByClassId
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KtPropertySymbol
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.plugin.isK2Plugin
import org.jetbrains.kotlin.idea.util.findAnnotation as findAnnotationK1
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.renderer.render

/** Returns the [PsiFile] associated with a given lint [Context]. */
fun Context.getPsiFile(): PsiFile? {
  val request = driver.request
  val project = (request as LintIdeRequest).project
  if (project.isDisposed) {
    return null
  }
  val file = VfsUtil.findFileByIoFile(file, false) ?: return null
  return file.getPsiFileSafely(project)
}

/** Checks if this [KtProperty] has a backing field or implements get/set on its own. */
@OptIn(KtAllowAnalysisOnEdt::class)
internal fun KtProperty.hasBackingField(): Boolean {
  allowAnalysisOnEdt {
    analyze(this) {
      val propertySymbol =
        this@hasBackingField.getVariableSymbol() as? KtPropertySymbol ?: return false
      return propertySymbol.hasBackingField
    }
  }
}

/**
 * Looks up the [PsiFile] for a given [VirtualFile] in a given [Project], in a safe way (meaning it
 * will acquire a read lock first, and will check that the file is valid
 */
fun VirtualFile.getPsiFileSafely(project: Project): PsiFile? {
  return ApplicationManager.getApplication()
    .runReadAction(
      (Computable {
        when {
          project.isDisposed -> null
          isValid -> PsiManager.getInstance(project).findFile(this)
          else -> null
        }
      })
    )
}

// TODO(jsjeon): Once available, use upstream util in `AnnotationModificationUtils`
fun KtModifierListOwner.addAnnotation(
  annotationFqName: FqName,
  annotationInnerText: String? = null,
  useSiteTarget: AnnotationUseSiteTarget? = null,
  searchForExistingEntry: Boolean = true,
  whiteSpaceText: String = "\n",
  addToExistingAnnotation: ((KtAnnotationEntry) -> Boolean)? = null
): Boolean {
  val useSiteTargetPrefix = useSiteTarget?.let { "${it.renderName}:" } ?: ""
  val annotationText =
    when (annotationInnerText) {
      null -> "@${useSiteTargetPrefix}${annotationFqName.render()}"
      else -> "@${useSiteTargetPrefix}${annotationFqName.render()}($annotationInnerText)"
    }

  val psiFactory = KtPsiFactory(project)
  val modifierList = modifierList

  if (modifierList == null) {
    val addedAnnotation = addAnnotationEntry(psiFactory.createAnnotationEntry(annotationText))
    ShortenReferencesFacility.getInstance().shorten(addedAnnotation)
    return true
  }

  val entry =
    if (searchForExistingEntry) (this as? KtDeclaration)?.findAnnotation(annotationFqName) else null
  if (entry == null) {
    // no annotation
    val newAnnotation = psiFactory.createAnnotationEntry(annotationText)
    val addedAnnotation =
      modifierList.addBefore(newAnnotation, modifierList.firstChild) as KtElement
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

// TODO(jsjeon): Once available, use upstream util in `AnnotationModificationUtils`
@OptIn(KtAllowAnalysisOnEdt::class)
fun KtAnnotated.findAnnotation(fqName: FqName): KtAnnotationEntry? =
  if (isK2Plugin()) {
    allowAnalysisOnEdt {
      analyze(this) {
        val annotatedSymbol =
          (this@findAnnotation as? KtDeclaration)?.getSymbol() as? KtAnnotatedSymbol
        val annotations = annotatedSymbol?.annotationsByClassId(ClassId.topLevel(fqName))
        annotations?.singleOrNull()?.psi as? KtAnnotationEntry
      }
    }
  } else {
    findAnnotationK1(fqName)
  }
