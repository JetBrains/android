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
package com.android.tools.idea.lang.roomSql

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleServiceManager
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiUtil

private const val ROOM_PACKAGE_NAME = "android.arch.persistence.room"
private const val ENTITY_ANNOTATION_NAME = "$ROOM_PACKAGE_NAME.Entity"
private const val DATABASE_ANNOTATION_NAME = "$ROOM_PACKAGE_NAME.Database"
private const val DAO_ANNOTATION_NAME = "$ROOM_PACKAGE_NAME.Dao"
private const val COLUMN_INFO_ANNOTATION_NAME = "$ROOM_PACKAGE_NAME.ColumnInfo"
private const val IGNORE_ANNOTATION_NAME = "$ROOM_PACKAGE_NAME.Ignore"

private val LOG = Logger.getInstance(RoomSchemaManager::class.java)

interface SqlNameDefinition {
  val name: String
  val nameElement: PsiElement
}

data class Database(

    /** Annotated class. */
    val psiClass: PsiClass,

    /** Classes mentioned in the `entities` annotation parameter. These may not actually be `@Entities` if the code is wrong.  */
    val entities: Set<PsiClass>)


data class Entity(

    /** Annotated class. */
    val psiClass: PsiClass,

    /** Name of the table: take from the class name or the annotation parameter. */
    override val name: String,

    /**
     * [PsiElement] that determines the table name and should be the destination of references from SQL.
     *
     * This can be either the class itself or the annotation element.
     */
    override val nameElement: PsiElement = psiClass,

    /** Columns present in the table representing this entity. */
    val columns: Set<Column> = emptySet()
) : SqlNameDefinition


data class Column(
    /** Field that defines this column. */
    val psiField: PsiField,

    /** Effective name of the column, either taken from the field or from `@ColumnInfo`. */
    override val name: String,

    /** The [PsiElement] that defines the column name. */
    override val nameElement: PsiElement = psiField
) : SqlNameDefinition


data class Dao(val psiClass: PsiClass)


data class RoomSchema(
    val databases: Set<Database>,
    val entities: Set<Entity>,
    val daos: Set<Dao>) {
  fun findEntity(psiClass: PsiClass) = entities.find { it.psiClass == psiClass }
}


/** Utility for constructing a [RoomSchema] using IDE indices. */
class RoomSchemaManager(val module: Module) {
  companion object {
    fun getInstance(module: Module): RoomSchemaManager? = ModuleServiceManager.getService(module, RoomSchemaManager::class.java)
  }

  /**
   * Returns all entities in the project, keyed by table name.
   *
   * Will return null if Room is not used in the project.
   */
  fun getSchema(): RoomSchema? =
      CachedValuesManager.getManager(module.project).getCachedValue(
          module, { CachedValueProvider.Result(buildSchema(), PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT) })

  private val constantEvaluationHelper = JavaPsiFacade.getInstance(module.project).constantEvaluationHelper


  /** Builds the schema using IJ indexes. */
  private fun buildSchema(): RoomSchema? {
    LOG.debug("Recalculating Room schema for module ", module.name)

    val searchScope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, false)
    val psiFacade = JavaPsiFacade.getInstance(module.project)
    val entityAnnotation = psiFacade.findClass(ENTITY_ANNOTATION_NAME, searchScope) ?: return annotationNotFound("Entity")
    val databaseAnnotation = psiFacade.findClass(DATABASE_ANNOTATION_NAME, searchScope) ?: return annotationNotFound("Database")
    val daoAnnotation = psiFacade.findClass(DAO_ANNOTATION_NAME, searchScope) ?: return annotationNotFound("Dao")

    val entities = AnnotatedElementsSearch.searchPsiClasses(entityAnnotation, searchScope).mapNotNullTo(HashSet(), this::createEntity)
    val databases = AnnotatedElementsSearch.searchPsiClasses(databaseAnnotation, searchScope).mapNotNullTo(HashSet(), this::createDatabase)
    val daos = AnnotatedElementsSearch.searchPsiClasses(daoAnnotation, searchScope).mapTo(HashSet(), ::Dao)

    return RoomSchema(databases, entities, daos)
  }

  private fun createEntity(psiClass: PsiClass): Entity? {
    val (tableName, tableNameElement) = getNameAndNameElement(
        psiClass, annotationName = ENTITY_ANNOTATION_NAME, annotationAttributeName = "tableName") ?: return null

    return Entity(psiClass, tableName, tableNameElement, findColumns(psiClass))
  }

  private fun findColumns(psiClass: PsiClass): Set<Column> {
    return psiClass.allFields
        .mapNotNullTo(HashSet()) { psiField ->
          if (psiField.modifierList?.findAnnotation(IGNORE_ANNOTATION_NAME) != null) {
            return@mapNotNullTo null
          }

          val (columnName, columnNameElement) = getNameAndNameElement(
              psiField, annotationName = COLUMN_INFO_ANNOTATION_NAME, annotationAttributeName = "name") ?: return@mapNotNullTo null

          Column(psiField, columnName, columnNameElement)
        }
  }

  private fun createDatabase(psiClass: PsiClass): Database? {
    val entitiesElementValue =
        psiClass.modifierList
            ?.findAnnotation(DATABASE_ANNOTATION_NAME)
            ?.findDeclaredAttributeValue("entities")
            ?.let { it as? PsiArrayInitializerMemberValue }
            ?.initializers
            ?.mapNotNullTo(HashSet()) {
              val classObjectAccessExpression = it as? PsiClassObjectAccessExpression ?: return@mapNotNullTo null
              PsiUtil.resolveClassInClassTypeOnly(classObjectAccessExpression.operand.type)
            }

    return Database(psiClass, entitiesElementValue ?: emptySet())
  }

  private fun <T> annotationNotFound(name: String): T? {
    LOG.debug("Annotation ", name, " not found in module ", module.name)
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
