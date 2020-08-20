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

import com.android.tools.idea.projectsystem.ScopeType
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.getScopeType
import com.intellij.openapi.module.Module
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiVariable
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.kotlin.asJava.classes.KtUltraLightClass
import org.jetbrains.kotlin.idea.inspections.AbstractPrimitiveRangeToInspection.Companion.constantValueOrNull
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

/**
 * Creates and stores [ContentAccessSchema] available in given module.
 */
class ContentAccessSchemaManager(val module: Module) {
  companion object {
    fun getInstance(module: Module): ContentAccessSchemaManager = module.getService(ContentAccessSchemaManager::class.java)!!
  }

  private val schemas = ScopeType.values().associate { it to createCachedValue(it) }
  private val pointerManager = SmartPointerManager.getInstance(module.project)

  private fun createCachedValue(scope: ScopeType): CachedValue<ContentAccessSchema> {
    return CachedValuesManager.getManager(module.project).createCachedValue {
      CachedValueProvider.Result(buildSchema(module, scope), PsiModificationTracker.MODIFICATION_COUNT)
    }
  }

  private fun buildSchema(module: Module, scopeType: ScopeType): ContentAccessSchema? {
    val scope = module.getModuleSystem().getResolveScope(scopeType)
    val entityAnnotationClass = JavaPsiFacade.getInstance(module.project).findClass(CONTENT_ENTITY_ANNOTATION, scope) ?: return null

    val entitiesClasses = AnnotatedElementsSearch.searchPsiClasses(entityAnnotationClass, scope)
    val entities = entitiesClasses.map { entityClass ->
      val columns: Array<PsiVariable> =
        when (entityClass) {
          is KtUltraLightClass -> entityClass.constructors[0].parameters.safeAs<Array<PsiVariable>>()
          is PsiClass -> entityClass.fields.safeAs<Array<PsiVariable>>()
          else -> null
        }
        ?: return null
      val contentAccessColumns = columns.mapNotNull { column ->
        val annotation = column.getAnnotation(CONTENT_COLUMN_ANNOTATION)
                         ?: column.getAnnotation(CONTENT_PRIMARY_KEY_ANNOTATION)
                         ?: return@mapNotNull null
        val nameElement = annotation.findAttributeValue(COLUMN_NAME).safeAs<PsiReferenceExpression>()?.resolve()

        ContentAccessColumn(
          pointerManager.createSmartPsiElementPointer(column),
          nameElement?.let { pointerManager.createSmartPsiElementPointer(it) },
          pointerManager.createSmartPsiElementPointer(annotation)
        )
      }
      ContentAccessEntity(pointerManager.createSmartPsiElementPointer(entityClass), contentAccessColumns)
    }

    return ContentAccessSchema(entities)
  }

  /**
   * Returns the [ContentAccessSchema] visible from the given [PsiFile] or null if ContentAccess is not used in the project.
   *
   * The schema is cached in the file and recomputed after a change to PSI.
   */
  fun getSchema(psiFile: PsiFile): ContentAccessSchema? {
    var vFile = psiFile.originalFile.virtualFile ?: return null
    // When we are inside Editing Fragment vFile does not belong to module. We need to use original one.
    if (vFile is LightVirtualFile && vFile.originalFile != null) {
      vFile = vFile.originalFile
    }
    if (!module.moduleContentScope.contains(vFile)) return null

    val scopeType = module.getModuleSystem().getScopeType(vFile, module.project)
    return schemas[scopeType]?.value
  }
}

/**
 * Stores classes annotated with @ContentEntity annotation as [ContentAccessEntity].
 */
data class ContentAccessSchema(
  val contentAccessEntities: List<ContentAccessEntity>
) {
  fun findEntity(psiClass: PsiClass) = contentAccessEntities.find { it.definingElement == psiClass }
}

/**
 * Represents a class annotated with @ContentEntity.
 */
data class ContentAccessEntity(
  private val elementPointer: SmartPsiElementPointer<PsiClass>,
  val columns: List<ContentAccessColumn> = emptyList()
) {
  val name = elementPointer.element?.name
  val definingElement = elementPointer.element
}

/**
 * Represents fields annotated with @ContentColumn or @ContentPrimaryKey annotation.
 */
data class ContentAccessColumn(
  private val elementPointer: SmartPsiElementPointer<*>,
  val constant: SmartPsiElementPointer<*>?,
  private val annotation: SmartPsiElementPointer<PsiAnnotation>
) {
  val isPrimaryKey = annotation.element?.qualifiedName == CONTENT_PRIMARY_KEY_ANNOTATION
  val definingElement = elementPointer.element
  val name
    get():String? {
      if (constant != null) {
        return when (constant.element) {
          is PsiField -> (constant.element as PsiField).computeConstantValue().safeAs()
          is KtProperty -> (constant.element as KtProperty).constantValueOrNull().safeAs()
          else -> null
        }
      }
      return (annotation.element as PsiAnnotation).findAttributeValue(COLUMN_NAME).safeAs<PsiLiteralExpression>()?.value.safeAs()
    }
}