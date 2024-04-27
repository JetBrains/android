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
package com.android.tools.idea.lang.androidSql.parser

import com.android.tools.idea.lang.androidSql.AndroidSqlFileType
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlDefinedTableName
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlin.reflect.KClass

class RoomNameElementTest : BasePlatformTestCase() {

  /**
   * Parses the given string and finds the first [PsiElement] of the requested class.
   */
  private fun <T : PsiElement> parseAndFind(input: String, kclass: KClass<T>): T {
    return PsiTreeUtil.findChildOfType(
      PsiFileFactory.getInstance(project).createFileFromText("sample.rsql", AndroidSqlFileType.INSTANCE, input),
      kclass.java
    )!!
  }

  fun testTableNameAsText() {
    assertThat(parseAndFind("select * from table_name", AndroidSqlDefinedTableName::class).nameAsString).isEqualTo("table_name")
    assertThat(parseAndFind("select * from [$123 table ą]", AndroidSqlDefinedTableName::class).nameAsString).isEqualTo("$123 table ą")
    assertThat(parseAndFind("""select * from "my table"""", AndroidSqlDefinedTableName::class).nameAsString).isEqualTo("my table")
    assertThat(parseAndFind("select * from 'Foo''s kingdom''s table'", AndroidSqlDefinedTableName::class).nameAsString)
      .isEqualTo("Foo's kingdom's table")
    assertThat(parseAndFind("select * from \"some\"\"table\"", AndroidSqlDefinedTableName::class).nameAsString).isEqualTo("some\"table")
    assertThat(parseAndFind("select * from `some table`", AndroidSqlDefinedTableName::class).nameAsString).isEqualTo("some table")
    assertThat(parseAndFind("select * from `some``table`", AndroidSqlDefinedTableName::class).nameAsString).isEqualTo("some`table")
  }
}

