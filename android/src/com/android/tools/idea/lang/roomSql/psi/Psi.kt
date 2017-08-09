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

import com.android.tools.idea.lang.roomSql.ROOM_SQL_FILE_TYPE
import com.android.tools.idea.lang.roomSql.ROOM_SQL_ICON
import com.android.tools.idea.lang.roomSql.ROOM_SQL_LANGUAGE
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import javax.swing.Icon

class RoomTokenType(debugName: String) : IElementType(debugName, ROOM_SQL_LANGUAGE) {
  override fun toString(): String = when (super.toString()) {
    "," -> "comma"
    ";" -> "semicolon"
    "'" -> "single quote"
    "\"" -> "double quote"
    else -> super.toString()
  }
}

class RoomAstNodeType(debugName: String) : IElementType(debugName, ROOM_SQL_LANGUAGE)

class RoomSqlFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, ROOM_SQL_LANGUAGE) {
  override fun getFileType(): FileType = ROOM_SQL_FILE_TYPE
  override fun getIcon(flags: Int): Icon? = ROOM_SQL_ICON
}

val ROOM_SQL_FILE_NODE_TYPE = IFileElementType(ROOM_SQL_LANGUAGE)

