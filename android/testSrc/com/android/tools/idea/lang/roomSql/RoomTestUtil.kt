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

import com.intellij.psi.PsiClass
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

abstract class LightRoomTestCase : LightCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()

    myFixture.addClass(
        """
          package android.arch.persistence.room;

          public @interface Dao {}
          """.trimIndent())

    myFixture.addClass(
        """
          package android.arch.persistence.room;

          public @interface Database { Class[] entities(); int version(); }
          """.trimIndent())

    myFixture.addClass(
        """
          package android.arch.persistence.room;

          public @interface Entity { String tableName() default ""; }
          """.trimIndent())

    myFixture.addClass(
        """
          package android.arch.persistence.room;

          public @interface Query { String value(); }
          """.trimIndent())

    myFixture.addClass(
        """
          package android.arch.persistence.room;

          public @interface Ignore {}
          """.trimIndent())
  }

  protected fun JavaCodeInsightTestFixture.addRoomEntity(
      qualifiedClassName: String,
      tableNameOverride: String? = null,
      fields: Map<String, String>? = null
  ) : PsiClass {
    val packageName = qualifiedClassName.substringBeforeLast('.', "")
    val className = qualifiedClassName.substringAfterLast('.')
    val packageLine = if (packageName.isEmpty()) "" else "package $packageName;"
    val annotationArguments = if (tableNameOverride == null) "" else "(tableName = \"$tableNameOverride\")"

    val fieldsSnippet = fields
        ?.entries
        ?.joinToString(prefix = "\n", postfix = "\n", separator = "\n") { (name, type) -> "private $type $name;" }
        .orEmpty()

    return addClass(
        """
        $packageLine

        import android.arch.persistence.room.Entity;

        @Entity$annotationArguments
        public class $className { $fieldsSnippet }
        """.trimIndent())
  }
}