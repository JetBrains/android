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
import com.android.tools.idea.kotlin.getQualifiedName
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiField
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.idea.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.util.findAnnotation
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.containingClass


/**
 * LocalInspection for a class annotated with @ContentEntity and its fields in Kotlin files.
 *
 * Reports:
 * - @ContentEntity declares more that one field annotated with @ContentPrimaryKey
 * - @ContentEntity does not define a @ContentPrimaryKey
 * - @ContentPrimaryKey or @ContentColumn are not in a class annotated with @ContentEntity
 * - Class annotated with @ContentEntity has a field that is not annotated with @ContentColumn or @ContentPrimaryKey
 */
class ContentEntityInspectionKotlin : AbstractKotlinInspection() {
  companion object {
    private val columnsFQNames = setOf(CONTENT_PRIMARY_KEY_ANNOTATION, CONTENT_COLUMN_ANNOTATION)
  }

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    if (!StudioFlags.CONTENT_ACCESS_SUPPORT_ENABLED.get()) return PsiElementVisitor.EMPTY_VISITOR

    return object : KtVisitorVoid() {
      override fun visitAnnotationEntry(annotationEntry: KtAnnotationEntry) {
        super.visitAnnotationEntry(annotationEntry)
        if (annotationEntry.fqNameMatches(columnsFQNames)) {
          val entity = annotationEntry.containingClass()

          if (entity?.hasAnnotation(CONTENT_ENTITY_ANNOTATION) != true) {
            // @ContentPrimaryKey or @ContentColumn are not in a class annotated with @ContentEntity.
            holder.registerProblem(
              annotationEntry,
              ContentAccessBundle.message("not.in.content.entity", StringUtil.getShortName(annotationEntry.getQualifiedName()!!))
            )
            return
          }
        }
        if (annotationEntry.fqNameMatches(CONTENT_PRIMARY_KEY_ANNOTATION)) {
          // Assume it's a correct entity, because it passed the first branch.
          val entity = annotationEntry.containingClass()!!
          val primaryKeysCount = entity.primaryConstructorParameters.count { it.hasAnnotation(CONTENT_PRIMARY_KEY_ANNOTATION) }
          if (primaryKeysCount > 1) {
            // @ContentEntity declares more that one field annotated with @ContentPrimaryKey.
            holder.registerProblem(annotationEntry, ContentAccessBundle.message("more.than.one.primary.keys"))
          }
        }
        if (annotationEntry.fqNameMatches(CONTENT_ENTITY_ANNOTATION)) {
          val entity = annotationEntry.containingClass() ?: return
          val hasPrimaryKey = entity.primaryConstructorParameters.any { it.hasAnnotation(CONTENT_PRIMARY_KEY_ANNOTATION) }
          if (!hasPrimaryKey) {
            // @ContentEntity does not define a @ContentPrimaryKey.
            holder.registerProblem(annotationEntry, ContentAccessBundle.message("zero.primary.keys"))
          }
        }
      }

      override fun visitParameter(parameter: KtParameter) {
        super.visitParameter(parameter)
        val entity = parameter.parentOfType<KtPrimaryConstructor>()?.containingClass() ?: return
        if (entity.isData() && entity.hasAnnotation(CONTENT_ENTITY_ANNOTATION)) {
          if (!parameter.hasAnnotation(CONTENT_COLUMN_ANNOTATION) && !parameter.hasAnnotation(CONTENT_PRIMARY_KEY_ANNOTATION)) {
            // An object annotated with @ContentEntity has a field that is not annotated with @ContentColumn or @ContentPrimaryKey.
            holder.registerProblem(parameter, ContentAccessBundle.message("every.field.should.be.annotated"))
          }
        }
      }
    }
  }
}

/**
 * LocalInspection for a class annotated with @ContentEntity and its fields in Java files.
 *
 * Reports:
 * - @ContentEntity declares more that one field annotated with @ContentPrimaryKey
 * - @ContentEntity does not define a @ContentPrimaryKey
 * - @ContentPrimaryKey or @ContentColumn are not in a class annotated with @ContentEntity
 * - Class annotated with @ContentEntity has a field that is not annotated with @ContentColumn or @ContentPrimaryKey
 */
class ContentEntityInspection : AbstractBaseJavaLocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    if (!StudioFlags.CONTENT_ACCESS_SUPPORT_ENABLED.get()) return PsiElementVisitor.EMPTY_VISITOR

    return object : JavaElementVisitor() {
      override fun visitAnnotation(annotation: PsiAnnotation) {
        val qualifiedName = annotation.qualifiedName
        if (qualifiedName == CONTENT_PRIMARY_KEY_ANNOTATION || qualifiedName == CONTENT_COLUMN_ANNOTATION) {
          val entity = annotation.parentOfType<PsiClass>()

          if (entity == null || !entity.hasAnnotation(CONTENT_ENTITY_ANNOTATION)) {
            // @ContentPrimaryKey or @ContentColumn are not in a class annotated with @ContentEntity.
            holder.registerProblem(
              annotation,
              ContentAccessBundle.message("not.in.content.entity", StringUtil.getShortName(qualifiedName))
            )
            return
          }
        }

        if (qualifiedName == CONTENT_PRIMARY_KEY_ANNOTATION) {
          // Assume it's a correct entity, because it passed the first if statement.
          val entity = annotation.parentOfType<PsiClass>()!!
          val primaryKeysCount = entity.allFields.count { it.hasAnnotation(CONTENT_PRIMARY_KEY_ANNOTATION) }
          if (primaryKeysCount > 1) {
            // @ContentEntity declares more that one field annotated with @ContentPrimaryKey.
            holder.registerProblem(annotation, ContentAccessBundle.message("more.than.one.primary.keys"))
          }
        }

        if (qualifiedName == CONTENT_ENTITY_ANNOTATION) {
          val entity = annotation.parentOfType<PsiClass>() ?: return
          val hasPrimaryKey = entity.allFields.any { it.hasAnnotation(CONTENT_PRIMARY_KEY_ANNOTATION) }
          if (!hasPrimaryKey) {
            // @ContentEntity does not define a @ContentPrimaryKey.
            holder.registerProblem(annotation, ContentAccessBundle.message("zero.primary.keys"))
          }
        }
      }

      override fun visitField(field: PsiField) {
        if (field.containingClass?.hasAnnotation(CONTENT_ENTITY_ANNOTATION) != true) {
          return
        }
        if (!field.hasAnnotation(CONTENT_COLUMN_ANNOTATION) && !field.hasAnnotation(CONTENT_PRIMARY_KEY_ANNOTATION)) {
          // An object annotated with @ContentEntity has a field that is not annotated with @ContentColumn or @ContentPrimaryKey.
          holder.registerProblem(field, ContentAccessBundle.message("every.field.should.be.annotated"))
        }
      }
    }
  }
}

private fun KtAnnotated.hasAnnotation(fqn: String) = findAnnotation(FqName(fqn)) != null