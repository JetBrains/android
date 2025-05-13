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
package com.android.tools.idea.lint.common

import com.android.tools.lint.detector.api.ClassContext
import com.android.tools.lint.detector.api.Location
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInsight.intention.AddAnnotationFix
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.lang.java.JavaLanguage
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandAction
import com.intellij.modcommand.Presentation
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiClassInitializer
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.SyntheticElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.util.findAnnotation
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTypeParameter

class AnnotateQuickFix(
  project: Project,
  private val displayName: String?,
  private val familyName: String?,
  private val annotationSource: String,
  private val replace: Boolean,
  range: Location?,
  private val selectPattern: String? = null,
) : ModCommandAction {
  private val rangePointer = LintIdeFixPerformer.getRangePointer(project, range)

  override fun getFamilyName(): @IntentionFamilyName String {
    return familyName ?: "Annotate"
  }

  override fun getPresentation(context: ActionContext): Presentation? {
    return if (findPsiTarget(context) != null) {
      @Suppress("UsePropertyAccessSyntax") // property syntax would access private nullable field
      Presentation.of(displayName ?: getFamilyName())
    } else {
      null
    }
  }

  override fun perform(context: ActionContext): ModCommand {
    val element = findPsiTarget(context) ?: return ModCommand.nop()

    return ModCommand.psiUpdate(element) { e, updater ->
      val element = applyFixFun(e)

      // Select text in the inserted or updated annotation
      if (element != null && selectPattern != null) {
        val text = element.text
        val result = Regex(selectPattern).find(text) ?: return@psiUpdate
        val groups = result.groups
        val group = if (groups.size > 1) groups[1] else groups[0]
        if (group != null) {
          val startIndex = group.range.first
          val begin = element.textOffset + startIndex
          val range = TextRange(begin, begin + group.value.length)
          updater.select(range)
          return@psiUpdate
        }
      }
    }
  }

  fun findPsiTarget(context: ActionContext): PsiElement? {
    val rangeFile = rangePointer?.element?.containingFile
    var element: PsiElement = context.findLeaf()!!

    if (
      rangeFile != null &&
        !(rangeFile.containingFile != context.file &&
          context.file.originalFile == rangeFile.containingFile)
    ) {
      val range = rangePointer.range
      val newStartElement = rangeFile.findElementAt(range!!.startOffset)
      if (newStartElement != null) {
        element = newStartElement
      }
    }
    return when (element.language) {
      JavaLanguage.INSTANCE -> findJavaAnnotationTarget(element)
      KotlinLanguage.INSTANCE -> findKotlinAnnotationTarget(element)
      else -> null
    }
  }

  fun applyFixFun(element: PsiElement): PsiElement? {
    when (element.language) {
      JavaLanguage.INSTANCE -> {
        val owner = element as PsiModifierListOwner
        val project = element.project
        val factory = JavaPsiFacade.getInstance(project).elementFactory
        val newAnnotation = factory.createAnnotationFromText(annotationSource, element)
        val annotationName = newAnnotation.qualifiedName ?: return null
        val annotation = AnnotationUtil.findAnnotation(owner, annotationName)
        if (annotation != null && annotation !is SyntheticElement && replace) {
          return annotation.replace(newAnnotation)
        } else {
          if (annotation != null && !replace) {
            // AddAnnotationFix will *not* add repeated annotations, so
            // we'll need to do this more manually
            return annotation.parent.addBefore(newAnnotation, annotation)
          }
          val attributes = newAnnotation.parameterList.attributes
          AddAnnotationFix(annotationName, element, attributes)
            .invoke(project, null, element.containingFile)
          return element.annotations.find { a ->
            a.qualifiedName == annotationName &&
              // The annotation fix sometimes applies changes to the attributes
              // (such as importing class constants) so we only compare by attribute names
              a.parameterList.attributes.map { it.name } == attributes.map { it.name }
          }
        }
      }
      KotlinLanguage.INSTANCE -> {
        if (element !is KtModifierListOwner) return null
        val psiFactory = KtPsiFactory(element.project, markGenerated = true)
        val annotationEntry = psiFactory.createAnnotationEntry(annotationSource)
        val fqName = annotationSource.removePrefix("@").substringAfter(':').substringBefore('(')
        val classId = ClassId.fromString(ClassContext.getInternalName(fqName))
        val existing = element.findAnnotation(classId)
        val addedAnnotation =
          if (existing != null && existing !is SyntheticElement && replace) {
            existing.replace(annotationEntry) as KtAnnotationEntry
          } else {
            element.addAnnotationEntry(annotationEntry)
          }
        val shortened = ShortenReferencesFacility.getInstance().shorten(addedAnnotation)
        return shortened?.parentOfType<KtAnnotationEntry>(true)
      }
    }
    return null
  }
}

fun PsiElement.isAnnotationTarget(): Boolean {
  return this is KtClassOrObject ||
    (this is KtFunction && this !is KtFunctionLiteral) ||
    (this is KtProperty && !isLocal && hasBackingField()) ||
    this is KtPropertyAccessor
}

fun KtElement.isNewLineNeededForAnnotation(): Boolean {
  return !(this is KtParameter || this is KtTypeParameter || this is KtPropertyAccessor)
}

fun findJavaAnnotationTarget(element: PsiElement?): PsiModifierListOwner? {
  val modifier = PsiTreeUtil.getParentOfType(element, PsiModifierListOwner::class.java, false)
  return if (modifier is PsiClassInitializer || modifier is PsiAnonymousClass) {
    findJavaAnnotationTarget(modifier.parent)
  } else {
    modifier
  }
}

private fun findKotlinAnnotationTarget(element: PsiElement) =
  PsiTreeUtil.findFirstParent(element, false) { it.isAnnotationTarget() }
