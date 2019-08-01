/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.lang.androidSql.parser

import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes
import com.intellij.lexer.FlexAdapter

class AndroidSqlLexer : FlexAdapter(_AndroidSqlLexer()) {
  companion object {
    fun needsQuoting(name: String): Boolean {
      val lexer = AndroidSqlLexer()
      lexer.start(name)
      return lexer.tokenType != AndroidSqlPsiTypes.IDENTIFIER || lexer.tokenEnd != lexer.bufferEnd
    }

    /** Checks if the given name (table name, column name) needs escaping and returns a string that's safe to put in SQL. */
    @JvmStatic
    fun getValidName(name: String): String {
      return if (!needsQuoting(name)) name else "`${name.replace("`", "``")}`"
    }
  }
}