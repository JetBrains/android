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
import com.android.tools.idea.lang.roomSql.psi.RoomDefinedTableName
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import kotlin.reflect.KClass

class RoomNameElementTest : LightCodeInsightFixtureTestCase() {

  /**
   * Parses the given string and finds the first [PsiElement] of the requested class.
   */
  private fun <T : PsiElement> parseAndFind(input: String, kclass: KClass<T>): T {
    return PsiTreeUtil.findChildOfType(
        PsiFileFactory.getInstance(project).createFileFromText("dummy.rsql", ROOM_SQL_FILE_TYPE, input),
        kclass.java
    )!!
  }

  fun testTableNameAsText() {
    assertThat(parseAndFind("select * from table_name", RoomDefinedTableName::class).nameAsString).isEqualTo("table_name")
    assertThat(parseAndFind("select * from [$123 table ą]", RoomDefinedTableName::class).nameAsString).isEqualTo("$123 table ą")
    assertThat(parseAndFind("""select * from "my table"""", RoomDefinedTableName::class).nameAsString).isEqualTo("my table")
    assertThat(parseAndFind("select * from 'Foo''s kingdom''s table'", RoomDefinedTableName::class).nameAsString)
        .isEqualTo("Foo's kingdom's table")
    assertThat(parseAndFind("select * from \"some\"\"table\"", RoomDefinedTableName::class).nameAsString).isEqualTo("some\"table")
    assertThat(parseAndFind("select * from `some table`", RoomDefinedTableName::class).nameAsString).isEqualTo("some table")
    assertThat(parseAndFind("select * from `some``table`", RoomDefinedTableName::class).nameAsString).isEqualTo("some`table")
  }
}

