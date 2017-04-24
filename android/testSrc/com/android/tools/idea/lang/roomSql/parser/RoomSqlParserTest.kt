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
package com.android.tools.idea.lang.roomSql.parser

import com.android.tools.idea.lang.roomSql.ROOM_SQL_FILE_TYPE
import com.android.tools.idea.lang.roomSql.psi.RoomSqlParserDefinition
import com.intellij.testFramework.ParsingTestCase

class RoomSqlParserTest : ParsingTestCase("no_data_path_needed", ROOM_SQL_FILE_TYPE.defaultExtension, RoomSqlParserDefinition()) {

  /**
   * Checks that the given text parses correctly.
   *
   * For now the PSI hierarchy is not finalized, so there's no point checking the tree shape.
   */
  private fun check(text: String) = ensureParsed(createPsiFile("in-memory", text))

  /**
   * Makes sure the lexer is not case sensitive.
   *
   * This needs to be manually fixed with "%caseless" after regenerating the flex file.
   */
  fun testCaseInsensitiveKeywords() {
    check("select foo from bar")
    check("SELECT foo FROM bar")
  }

  fun testSelect() {
    check("select * from table")
    check("""select *, table.*, foo, "bar", 12 from table""")
    check("select foo from bar, baz")
    check("select foo from bar join baz")
    check("select foo from bar left outer join baz")
    check("select foo from table where bar")
    check("select foo from table group by bar")
    check("select foo from table group by bar having baz")
    check("select foo from bar union all select baz from goo")
    check("select foo from bar order by foo")
    check("select foo from bar limit 1")
    check("select foo from bar limit 1 offset :page")

    check("""
      select foo, 3.4e+2 from bar inner join OtherTable group by name having expr
      union all select *, User.* from Table, User limit 1 offset :page""")
  }

  fun testInsert() {
    check("""insert into foo values (1, "foo")""")
    check("""insert into foo(a,b) values (1, "foo")""")
    check("insert into foo default values")
    check("""insert or replace into foo values (1, "foo")""")
  }

  fun testDelete() {
    check("delete from foo")
    check("delete from foo where bar")
  }

  fun testUpdate() {
    check("update foo set bar=42")
    check("update foo set bar = 42, baz = :value, quux=:anotherValue")
    check("update or fail foo set bar=42")
    check("update foo set bar=42 where someExpr")
  }
}