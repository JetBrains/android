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
package com.android.tools.idea.lang.roomSql.psi

import com.android.tools.idea.lang.roomSql.*
import com.android.tools.idea.lang.roomSql.resolution.RoomSchemaManager
import com.android.tools.idea.lang.roomSql.resolution.SqlTable
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.util.Processor
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.uast.*
import javax.swing.Icon

class RoomTokenType(debugName: String) : IElementType(debugName, RoomSqlLanguage.INSTANCE) {
  override fun toString(): String = when (super.toString()) {
    "," -> "comma"
    ";" -> "semicolon"
    "'" -> "single quote"
    "\"" -> "double quote"
    else -> super.toString()
  }
}

class RoomAstNodeType(debugName: String) : IElementType(debugName, RoomSqlLanguage.INSTANCE)

class RoomSqlFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, RoomSqlLanguage.INSTANCE) {
  override fun getFileType(): FileType = ROOM_SQL_FILE_TYPE
  override fun getIcon(flags: Int): Icon? = ROOM_ICON

  val queryAnnotation: UAnnotation? get() {
    val injectionHost = InjectedLanguageManager.getInstance(project).getInjectionHost(this)
    val annotation = injectionHost?.getUastParentOfType<UAnnotation>() ?: return null

    return if (annotation.qualifiedName == QUERY_ANNOTATION_NAME) annotation else null
  }

  val queryMethod: UMethod? get() = queryAnnotation?.getParentOfType<UAnnotated>() as? UMethod

  fun processTables(processor: Processor<SqlTable>): Boolean {
    if (queryAnnotation != null) {
      // We are inside a Room @Query annotation, let's use the Room schema.
      val tables = RoomSchemaManager.getInstance(this)?.schema?.entities?: emptySet<SqlTable>()
      return ContainerUtil.process(tables, processor)
    }

    return true
  }
}

val ROOM_SQL_FILE_NODE_TYPE = IFileElementType(RoomSqlLanguage.INSTANCE)

interface SqlTableElement : PsiElement {
  val sqlTable: SqlTable?
}

interface HasWithClause : PsiElement {
  val withClause: RoomWithClause?
}

