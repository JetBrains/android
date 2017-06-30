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
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassObjectAccessExpression
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiUtil

private const val ENTITY_ANNOTATION_NAME = "android.arch.persistence.room.Entity"
private const val DATABASE_ANNOTATION_NAME = "android.arch.persistence.room.Database"
private const val DAO_ANNOTATION_NAME = "android.arch.persistence.room.Dao"

private val LOG = Logger.getInstance(RoomSchemaManager::class.java)

/** A class annotated with `@Database`. */
data class Database(
    /** Annotated class. */
    val psiClass: PsiClass,
    /** Classes mentioned in the `entities` annotation parameter. These may not actually be `@Entities` if the code is wrong.  */
    val entities: Set<PsiClass>)

data class Entity(
    /** Annotated class. */
    val psiClass: PsiClass,
    /** Name of the table: take from the class name or the annotation parameter. */
    val tableName: String)

data class Dao(val psiClass: PsiClass)

data class RoomSchema(
    val databases: Set<Database>,
    val entities: Set<Entity>,
    val daos: Set<Dao>)

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
    val tableNameOverride =
        psiClass.modifierList
            ?.findAnnotation(ENTITY_ANNOTATION_NAME)
            ?.findDeclaredAttributeValue("tableName")
            ?.let { JavaPsiFacade.getInstance(psiClass.project).constantEvaluationHelper.computeConstantExpression(it) }
            ?.toString()

    val tableName = tableNameOverride ?: psiClass.name ?: return null

    return Entity(psiClass, tableName)
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
}