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

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.psi.PsiClass
import com.intellij.psi.SmartPointerManager
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

abstract class RoomLightTestCase : LightCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()

    // A misconfigured SDK manifests itself in strange ways, let's fail early if there's something wrong.
    assertThat(ModuleRootManager.getInstance(myModule).sdk).named("module SDK").isNotNull()

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

    myFixture.addClass(
        """
          package android.arch.persistence.room;

          public @interface ColumnInfo { String name() default ""; }
          """.trimIndent())

    myFixture.addClass(
        """
          package android.arch.persistence.room;

          public @interface Embedded { String prefix() default ""; }
          """.trimIndent())
  }

  protected data class FieldDefinition(val name: String, val type: String, val columnName: String? = null)

  protected infix fun String.ofType(type: String): FieldDefinition = FieldDefinition(this, type)

  protected fun JavaCodeInsightTestFixture.classPointer(qualifiedClassName: String) =
      SmartPointerManager.getInstance(project).createSmartPsiElementPointer(findClass(qualifiedClassName))

  protected fun JavaCodeInsightTestFixture.findField(qualifiedClassName: String, fieldName: String, checkBases: Boolean = false) =
      findClass(qualifiedClassName).findFieldByName(fieldName, checkBases)!!

  protected fun JavaCodeInsightTestFixture.fieldPointer(qualifiedClassName: String, fieldName: String, checkBases: Boolean = false) =
      SmartPointerManager.getInstance(project).createSmartPsiElementPointer(findField(qualifiedClassName, fieldName, checkBases))

  protected fun JavaCodeInsightTestFixture.addRoomEntity(qualifiedClassName: String, vararg fields: FieldDefinition) =
      addRoomEntity(qualifiedClassName, tableNameOverride = null, fields = *fields)

  protected val JavaCodeInsightTestFixture.referenceAtCaret get() = file.findReferenceAt(caretOffset)!!

  protected fun JavaCodeInsightTestFixture.addRoomEntity(
      qualifiedClassName: String,
      tableNameOverride: String?,
      vararg fields: FieldDefinition
  ) : PsiClass {
    val packageName = qualifiedClassName.substringBeforeLast('.', "")
    val className = qualifiedClassName.substringAfterLast('.')
    val packageLine = if (packageName.isEmpty()) "" else "package $packageName;"
    val annotationArguments = if (tableNameOverride == null) "" else "(tableName = \"$tableNameOverride\")"

    val fieldsSnippet = fields.joinToString(prefix = "\n", postfix = "\n", separator = "\n") { (name, type, columnName) ->
      val annotation = if (columnName == null) "" else "@ColumnInfo(name = \"$columnName\")"
      "$annotation $type $name;"
    }

    return addClass(
        """
        $packageLine

        import android.arch.persistence.room.Entity;
        import android.arch.persistence.room.ColumnInfo;

        @Entity$annotationArguments
        public class $className { $fieldsSnippet }
        """.trimIndent())
  }
}
