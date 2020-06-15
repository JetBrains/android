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

import com.android.tools.idea.lang.androidSql.ANDROID_SQL_ICON
import com.android.tools.idea.lang.androidSql.AndroidSqlFileType
import com.android.tools.idea.lang.androidSql.AndroidSqlLanguage
import com.android.tools.idea.lang.androidSql.resolution.AndroidSqlTable
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
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
  override fun getFileType(): FileType = AndroidSqlFileType.INSTANCE
  override fun getIcon(flags: Int): Icon? = ANDROID_SQL_ICON
}

val ANDROID_SQL_FILE_NODE_TYPE = IFileElementType(AndroidSqlLanguage.INSTANCE)

internal interface AndroidSqlTableElement : PsiElement {
  val sqlTable: AndroidSqlTable?
}

internal interface HasWithClause : PsiElement {
  val withClause: AndroidSqlWithClause?
}
