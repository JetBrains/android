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

import com.android.tools.idea.lang.roomSql.ROOM_ICON
import com.android.tools.idea.lang.roomSql.ROOM_SQL_FILE_TYPE
import com.android.tools.idea.lang.roomSql.RoomAnnotations
import com.android.tools.idea.lang.roomSql.RoomSqlLanguage
import com.android.tools.idea.lang.roomSql.resolution.IgnoreClassProcessor
import com.android.tools.idea.lang.roomSql.resolution.RoomSchemaManager
import com.android.tools.idea.lang.roomSql.resolution.SqlTable
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.util.Processor
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UClass
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.getUastParentOfType
import javax.swing.Icon

class RoomTokenType(debugName: String) : IElementType(debugName, RoomSqlLanguage.INSTANCE) {
  override fun toString(): String = when (val token = super.toString()) {
    "," -> "comma"
    ";" -> "semicolon"
    "'" -> "single quote"
    "\"" -> "double quote"
    else -> token
  }
}

class RoomAstNodeType(debugName: String) : IElementType(debugName, RoomSqlLanguage.INSTANCE)

class RoomSqlFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, RoomSqlLanguage.INSTANCE) {
  override fun getFileType(): FileType = ROOM_SQL_FILE_TYPE
  override fun getIcon(flags: Int): Icon? = ROOM_ICON

  fun findHostRoomAnnotation(): UAnnotation? {
    return findHost()
      ?.getUastParentOfType<UAnnotation>()
      ?.takeIf {
        val qualifiedName = it.qualifiedName
        when {
          RoomAnnotations.QUERY.isEquals(qualifiedName) -> true
          RoomAnnotations.DATABASE_VIEW.isEquals(qualifiedName) -> true
          else -> false
        }
      }
  }

  private fun findHost(): PsiLanguageInjectionHost? {
    // InjectedLanguageUtil is deprecated, but works in more cases than InjectedLanguageManager, e.g. when using [QuickEditAction] ("Edit
    // RoomSql fragment" intention) it navigates from the created light VirtualFile back to the original host string. We start with the
    // recommended method, fall back to a known solution to the situation described above and eventually fall back to the deprecated method
    // which seems to handle even more cases.
    return InjectedLanguageManager.getInstance(project).getInjectionHost(this)
           ?: context as? PsiLanguageInjectionHost
           ?: InjectedLanguageUtil.findInjectionHost(this)
  }

  fun processTables(processor: Processor<SqlTable>): Boolean {
    val hostRoomAnnotation = findHostRoomAnnotation()
    if (hostRoomAnnotation != null) {
      // We are inside a Room annotation, let's use the Room schema.
      return ContainerUtil.process(
        RoomSchemaManager.getInstance(project)?.getSchema(this)?.tables ?: emptySet<SqlTable>(),
        amendProcessor(hostRoomAnnotation, processor)
      )
    }

    return true
  }

  /**
   * Picks the right [Processor] for tables in the schema. If this [RoomSqlFile] belongs to a `@DatabaseView` definition, skips the view
   * being defined from completion, to avoid recursive definitions.
   */
  private fun amendProcessor(
    hostRoomAnnotation: UAnnotation,
    processor: Processor<SqlTable>
  ): Processor<in SqlTable> {
    return hostRoomAnnotation.takeIf { RoomAnnotations.DATABASE_VIEW.isEquals(it.qualifiedName) }
             ?.getParentOfType<UClass>()
             ?.let { IgnoreClassProcessor(it.javaPsi, processor) }
           ?: processor
  }
}

val ROOM_SQL_FILE_NODE_TYPE = IFileElementType(RoomSqlLanguage.INSTANCE)

interface SqlTableElement : PsiElement {
  val sqlTable: SqlTable?
}

interface HasWithClause : PsiElement {
  val withClause: RoomWithClause?
}
