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
import com.android.tools.idea.lang.roomSql.psi.RoomColumnName
import com.android.tools.idea.lang.roomSql.psi.RoomTableName
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil

private const val DUMMY_FILE_NAME = "dummy.rsql"

class RoomSqlPsiFacade(val project: Project) {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): RoomSqlPsiFacade? = ServiceManager.getService(project, RoomSqlPsiFacade::class.java)
  }

  fun createFileFromText(text: String): PsiFile =
      PsiFileFactory.getInstance(project).createFileFromText(DUMMY_FILE_NAME, ROOM_SQL_FILE_TYPE, text)

  fun createTableName(name: String): RoomTableName? =
      PsiTreeUtil.findChildOfAnyType(
          createFileFromText("select * from ${RoomSqlLexer.getValidName(name)}"),
          RoomTableName::class.java)

  fun createColumnName(name: String): RoomColumnName? =
      PsiTreeUtil.findChildOfAnyType(
          createFileFromText("select ${RoomSqlLexer.getValidName(name)}"),
          RoomColumnName::class.java)
}
