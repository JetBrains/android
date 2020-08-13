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
package com.android.tools.idea.lang.contentAccess

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.kotlin.fqNameMatches
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.util.findAnnotation
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.annotations.argumentValue
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

private val contentAccessObjectMethods = setOf(CONTENT_QUERY_ANNOTATION,
                                               CONTENT_UPDATE_ANNOTATION,
                                               CONTENT_DELETE_ANNOTATION,
                                               CONTENT_INSERT_ANNOTATION)

/**
 * LocalInspection for a class annotated with @ContentAccessObject and its methods in Kotlin files.
 *
 * Reports:
 * - when a ContentAccessObject's method doesn't have information about a ContentEntity
 */
class ContentAccessObjectInspectionKotlin : AbstractKotlinInspection() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    if (!StudioFlags.CONTENT_ACCESS_SUPPORT_ENABLED.get()) return PsiElementVisitor.EMPTY_VISITOR

    return object : KtVisitorVoid() {
      override fun visitAnnotationEntry(annotationEntry: KtAnnotationEntry) {
        super.visitAnnotationEntry(annotationEntry)
        if (annotationEntry.fqNameMatches(contentAccessObjectMethods) && annotationEntry.parent?.parent is KtNamedFunction) {
          val entityFromParam = annotationEntry
            .analyze(BodyResolveMode.PARTIAL)
            .get(BindingContext.ANNOTATION, annotationEntry)
            ?.argumentValue(CONTENT_ENTITY_PARAM)

          if (entityFromParam == null) {
            val contentAccessObjectAnn = annotationEntry.containingClass()?.findAnnotation(FqName(CONTENT_ACCESS_OBJECT_ANNOTATION))
            val entityFromObject = contentAccessObjectAnn
              ?.analyze(BodyResolveMode.PARTIAL)
              ?.get(BindingContext.ANNOTATION, contentAccessObjectAnn)
              ?.argumentValue(CONTENT_ENTITY_PARAM)

            if (entityFromObject == null) {
              holder.registerProblem(
                annotationEntry,
                ContentAccessBundle.message("can.not.resolve.entity", annotationEntry.shortName!!.asString())
              )
            }
          }
        }
      }
    }
  }
}

/**
 * LocalInspection for a class annotated with @ContentAccessObject and its methods in Java files.
 *
 * Reports:
 * - when a ContentAccessObject's method doesn't have information about a ContentEntity
 */
class ContentAccessObjectInspection : AbstractBaseJavaLocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    if (!StudioFlags.CONTENT_ACCESS_SUPPORT_ENABLED.get()) return PsiElementVisitor.EMPTY_VISITOR

    return object : JavaElementVisitor() {
      override fun visitAnnotation(annotation: PsiAnnotation) {
        if (!contentAccessObjectMethods.any { annotation.qualifiedName == it } || annotation.parent?.parent !is PsiMethod) {
          return
        }
        val entityFromParam = annotation.findAttribute(CONTENT_ENTITY_PARAM)

        if (entityFromParam == null) {
          val contentAccessObjectAnn = (annotation.parent.parent as PsiMethod)
            .containingClass?.getAnnotation(CONTENT_ACCESS_OBJECT_ANNOTATION)
          val entityFromObject = contentAccessObjectAnn?.findAttribute(CONTENT_ENTITY_PARAM)

          if (entityFromObject == null) {
            holder.registerProblem(
              annotation,
              ContentAccessBundle.message("can.not.resolve.entity", StringUtil.getShortName(annotation.qualifiedName!!))
            )
          }
        }
        super.visitAnnotation(annotation)
      }
    }
  }
}