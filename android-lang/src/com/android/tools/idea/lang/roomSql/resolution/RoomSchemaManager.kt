/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.lang.roomSql.resolution

import com.android.tools.idea.lang.roomSql.*
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.impl.ResolveScopeManager
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiUtil

private val LOG = Logger.getInstance(RoomSchemaManager::class.java)

/** Utility for constructing a [RoomSchema] using IDE indices. */
class RoomSchemaManager(val project: Project) {
  companion object {
    fun getInstance(project: Project): RoomSchemaManager? = ServiceManager.getService(project, RoomSchemaManager::class.java)
  }

  /**
   * Returns the [RoomSchema] visible from the given [PsiFile] or null if Room is not used in the project.
   *
   * The schema is cached in the file and recomputed after a change to java structure.
   *
   * @see PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT
   */
  fun getSchema(psiFile: PsiFile): RoomSchema? = CachedValuesManager.getManager(project).getCachedValue(
      psiFile, { CachedValueProvider.Result(buildSchema(psiFile), PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT) })

  private val constantEvaluationHelper = JavaPsiFacade.getInstance(project).constantEvaluationHelper

  /** Builds the schema using IJ indexes. */
  private fun buildSchema(psiFile: PsiFile): RoomSchema? {
    LOG.debug("Recalculating Room schema for file ", psiFile)
    val scope = ResolveScopeManager.getInstance(project).getResolveScope(psiFile)

    val psiFacade = JavaPsiFacade.getInstance(project)
    val entityAnnotation = psiFacade.findClass(ENTITY_ANNOTATION_NAME, scope) ?: return annotationNotFound("Entity", psiFile)
    val databaseAnnotation = psiFacade.findClass(DATABASE_ANNOTATION_NAME, scope) ?: return annotationNotFound("Database", psiFile)
    val daoAnnotation = psiFacade.findClass(DAO_ANNOTATION_NAME, scope) ?: return annotationNotFound("Dao", psiFile)
    val pointerManager = SmartPointerManager.getInstance(project)

    val entities = AnnotatedElementsSearch.searchPsiClasses(entityAnnotation, scope)
        .mapNotNullTo(HashSet()) { this.createEntity(it, pointerManager) }
    val databases = AnnotatedElementsSearch.searchPsiClasses(databaseAnnotation, scope)
        .mapNotNullTo(HashSet()) { this.createDatabase(it, pointerManager) }
    val daos = AnnotatedElementsSearch.searchPsiClasses(daoAnnotation, scope)
        .mapTo(HashSet()) { Dao(pointerManager.createSmartPsiElementPointer(it)) }

    return RoomSchema(databases, entities, daos)
  }

  private fun createEntity(psiClass: PsiClass, pointerManager: SmartPointerManager): Entity? {
    val (tableName, tableNameElement) = getNameAndNameElement(
        psiClass, annotationName = ENTITY_ANNOTATION_NAME, annotationAttributeName = "tableName") ?: return null

    return Entity(
        pointerManager.createSmartPsiElementPointer(psiClass),
        tableName,
        pointerManager.createSmartPsiElementPointer(tableNameElement),
        findColumns(psiClass, pointerManager)
    )
  }

  private fun findColumns(psiClass: PsiClass, pointerManager: SmartPointerManager): Set<EntityColumn> {
    return psiClass.allFields
        .asSequence()
        .filterNot { it.modifierList?.hasModifierProperty(PsiModifier.STATIC) == true }
        .filterNot { it.modifierList?.findAnnotation(IGNORE_ANNOTATION_NAME) != null }
        .mapNotNullTo(HashSet()) { psiField ->
          getNameAndNameElement(
              psiField,
              annotationName = COLUMN_INFO_ANNOTATION_NAME,
              annotationAttributeName = "name")
              ?.let { (columnName, columnNameElement) ->
                EntityColumn(
                    pointerManager.createSmartPsiElementPointer(psiField),
                    columnName,
                    pointerManager.createSmartPsiElementPointer(columnNameElement)
                )
              }
        }
  }

  private fun createDatabase(psiClass: PsiClass, pointerManager: SmartPointerManager): RoomDatabase? {
    val entitiesElementValue: HashSet<PsiClassPointer>? =
        psiClass.modifierList
            ?.findAnnotation(DATABASE_ANNOTATION_NAME)
            ?.findDeclaredAttributeValue("entities")
            ?.let { it as? PsiArrayInitializerMemberValue }
            ?.initializers
            ?.mapNotNullTo(HashSet()) {
              val classObjectAccessExpression = it as? PsiClassObjectAccessExpression ?: return@mapNotNullTo null
              PsiUtil.resolveClassInClassTypeOnly(classObjectAccessExpression.operand.type)
                  ?.let(pointerManager::createSmartPsiElementPointer)
            }

    return RoomDatabase(pointerManager.createSmartPsiElementPointer(psiClass), entitiesElementValue ?: emptySet())
  }

  private fun <T> annotationNotFound(name: String, psiFile: PsiFile): T? {
    LOG.debug("Annotation ", name, " not found from ", psiFile.name)
    return null
  }

  private fun <T> getNameAndNameElement(element: T, annotationName: String, annotationAttributeName: String): Pair<String, PsiElement>?
      where T : PsiModifierListOwner,
            T : PsiNamedElement {
    val nameAttribute = element.modifierList
        ?.findAnnotation(annotationName)
        ?.findDeclaredAttributeValue(annotationAttributeName)

    val name = nameAttribute
        ?.let { constantEvaluationHelper.computeConstantExpression(it) }
        ?.toString()
        ?: element.name
        ?: return null

    return Pair(name, nameAttribute ?: element)
  }
}
