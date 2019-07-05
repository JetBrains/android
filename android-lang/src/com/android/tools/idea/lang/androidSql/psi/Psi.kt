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
package com.android.tools.idea.lang.androidSql.psi

import com.android.tools.idea.lang.androidSql.ANDROID_SQL_FILE_TYPE
import com.android.tools.idea.lang.androidSql.ANDROID_SQL_ICON
import com.android.tools.idea.lang.androidSql.AndroidSqlLanguage
import com.android.tools.idea.lang.androidSql.resolution.AndroidSqlTable
import com.android.tools.idea.lang.androidSql.resolution.IgnoreClassProcessor
import com.android.tools.idea.lang.androidSql.room.RoomAnnotations
import com.android.tools.idea.lang.androidSql.room.RoomSchemaManager
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.module.ModuleUtil
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

class AndroidSqlTokenType(debugName: String) : IElementType(debugName, AndroidSqlLanguage.INSTANCE) {
  override fun toString(): String = when (val token = super.toString()) {
    "," -> "comma"
    ";" -> "semicolon"
    "'" -> "single quote"
    "\"" -> "double quote"
    else -> token
  }
}

class AndroidSqlAstNodeType(debugName: String) : IElementType(debugName, AndroidSqlLanguage.INSTANCE)

class AndroidSqlFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, AndroidSqlLanguage.INSTANCE) {
  override fun getFileType(): FileType = ANDROID_SQL_FILE_TYPE
  override fun getIcon(flags: Int): Icon? = ANDROID_SQL_ICON

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

  fun processTables(processor: Processor<AndroidSqlTable>): Boolean {
    val hostRoomAnnotation = findHostRoomAnnotation()
    if (hostRoomAnnotation != null) {
      // We are inside a Room annotation, let's use the Room schema.
      val module = ModuleUtil.findModuleForPsiElement(this) ?: return true
      return ContainerUtil.process(
        RoomSchemaManager.getInstance(module).getSchema(this)?.tables ?: emptySet<AndroidSqlTable>(),
        amendProcessor(hostRoomAnnotation, processor)
      )
    }

    return true
  }

  /**
   * Picks the right [Processor] for tables in the schema. If this [AndroidSqlFile] belongs to a `@DatabaseView` definition, skips the view
   * being defined from completion, to avoid recursive definitions.
   */
  private fun amendProcessor(
    hostRoomAnnotation: UAnnotation,
    processor: Processor<AndroidSqlTable>
  ): Processor<in AndroidSqlTable> {
    return hostRoomAnnotation.takeIf { RoomAnnotations.DATABASE_VIEW.isEquals(it.qualifiedName) }
             ?.getParentOfType<UClass>()
             ?.let { IgnoreClassProcessor(it.javaPsi, processor) }
           ?: processor
  }
}

val ANDROID_SQL_FILE_NODE_TYPE = IFileElementType(AndroidSqlLanguage.INSTANCE)

internal interface AndroidSqlTableElement : PsiElement {
  val sqlTable: AndroidSqlTable?
}

internal interface HasWithClause : PsiElement {
  val withClause: AndroidSqlWithClause?
}
