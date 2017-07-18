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

import com.android.tools.idea.lang.roomSql.parser.RoomSqlLexer
import com.android.tools.idea.lang.roomSql.psi.RoomTableName
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.util.ArrayUtil

class RoomTablePsiReference(
    val tableName: RoomTableName
) : PsiReferenceBase.Poly<RoomTableName>(tableName, TextRange(0, tableName.textLength), false) {

  private val schema: RoomSchema? = ModuleUtil.findModuleForPsiElement(tableName)
      ?.let(RoomSchemaManager.Companion::getInstance)
      ?.getSchema()

  override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> = PsiElementResolveResult.createResults(
      schema
          ?.entities
          ?.asSequence()
          ?.filter { it.tableName.equals(tableName.nameAsString, ignoreCase = !tableName.isQuoted) }
          ?.map { it.psiClass }
          ?.toList()
          .orEmpty())

  override fun getVariants(): Array<Any> =
      schema
          ?.entities
          ?.map {
            LookupElementBuilder.create(it.psiClass, RoomSqlLexer.getValidName(it.tableName)).withTypeText(it.psiClass.qualifiedName, true)
          }
          ?.let { it as? List<Any> }
          ?.toTypedArray()
          ?: ArrayUtil.EMPTY_OBJECT_ARRAY

}

class RoomTableNameManipulator : AbstractElementManipulator<RoomTableName>() {
  override fun handleContentChange(element: RoomTableName, range: TextRange, newContent: String?): RoomTableName {
    if (newContent.isNullOrEmpty()) {
      return element
    }

    val newName = RoomSqlPsiFacade.getInstance(element.project)?.createTableName(newContent!!) ?: return element
    return element.replace(newName) as RoomTableName
  }
}