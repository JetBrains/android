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
package com.android.tools.idea.lang.androidSql

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture

private val roomAnnotationToClassBodyJava = mapOf(
  "Dao" to """
  package androidx.room;

  public @interface Dao {}
  """.trimIndent(),

  "Database" to """
  package androidx.room;

  public @interface Database { Class<?>[] entities(); Class<?>[] views() default {}; int version(); }
  """.trimIndent(),

  "Entity" to """
  package androidx.room;

  public @interface Entity { String tableName() default ""; }
  """.trimIndent(),

  "Query" to """
  package androidx.room;

  public @interface Query { String value(); }
  """.trimIndent(),

  "DatabaseView" to """
  package androidx.room;

  public @interface DatabaseView { String value() default ""; String viewName() default ""  }
  """.trimIndent(),

  "Ignore" to """
  package androidx.room;

  public @interface Ignore {}
  """.trimIndent(),

  "ColumnInfo" to """
  package androidx.room;

  public @interface ColumnInfo { String name() default ""; }
  """.trimIndent(),

  "Embedded" to """
  package androidx.room;

  public @interface Embedded { String prefix() default ""; }
  """.trimIndent(),

  "Fts3" to """
  package androidx.room;

  public @interface Fts3 {}
  """.trimIndent(),

  "Fts4" to """
  package androidx.room;

  public @interface Fts4 {}
  """.trimIndent()
)

private val roomAnnotationToClassBodyKotlin = mapOf(
  "androidx/room/Dao.kt" to """
    package androidx.room
  
    @Target(AnnotationTarget.CLASS)
    @Retention(AnnotationRetention.BINARY)
    public annotation class Dao
    """.trimIndent(),

  "androidx/room/Database.kt" to """
    package androidx.room
  
    @Target(AnnotationTarget.CLASS)
    @Retention(AnnotationRetention.BINARY)
    public annotation class Database(
      val entities: Array<KClass<*>> = [],
      val views: Array<KClass<*>> = [],
      val version: Int,
      val exportSchema: Boolean = true,
      val autoMigrations: Array<AutoMigration> = []
    )
    """.trimIndent(),

  "androidx/room/Entity.kt" to """
    package androidx.room
  
    @Target(AnnotationTarget.CLASS)
    @Retention(AnnotationRetention.BINARY)
    public annotation class Entity(
      val tableName: String = "",
      val indices: Array<Index> = [],
      val inheritSuperIndices: Boolean = false,
      val primaryKeys: Array<String> = [],
      val foreignKeys: Array<ForeignKey> = [],
      val ignoredColumns: Array<String> = []
    )
    """.trimIndent(),

  "androidx/room/Query.kt" to """
    package androidx.room
  
    @Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
    @Retention(AnnotationRetention.BINARY)
    public annotation class Query(
      val value: String
    )
    """.trimIndent(),

  "androidx/room/DatabaseView.kt" to """
    package androidx.room
  
    @Target(AnnotationTarget.CLASS)
    @Retention(AnnotationRetention.BINARY)
    public annotation class DatabaseView(
      val value: String = "",
      val viewName: String = ""
    )
    """.trimIndent(),

  "androidx/room/Ignore.kt" to """
    package androidx.room
  
    @Target(
      AnnotationTarget.FUNCTION,
      AnnotationTarget.FIELD,
      AnnotationTarget.CONSTRUCTOR,
      AnnotationTarget.PROPERTY_GETTER
    )
    @Retention(AnnotationRetention.BINARY)
    public annotation class Ignore
    """.trimIndent(),

  "androidx/room/ColumnInfo.kt" to """
    package androidx.room
  
    @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
    @Retention(AnnotationRetention.BINARY)
    public annotation class ColumnInfo(
        val name: String = INHERIT_FIELD_NAME,
        @get:SQLiteTypeAffinity
        val typeAffinity: Int = UNDEFINED,
        val index: Boolean = false,
        @get:Collate
        val collate: Int = UNSPECIFIED,
        val defaultValue: String = VALUE_UNSPECIFIED,
    )
    """.trimIndent(),

  "androidx/room/Embedded.kt" to """
    package androidx.room
  
    @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
    @Retention(AnnotationRetention.BINARY)
    public annotation class Embedded(val prefix: String = "")
    """.trimIndent(),

  "androidx/room/Fts3.kt" to """
    package androidx.room
  
    @Target(AnnotationTarget.CLASS)
    @Retention(AnnotationRetention.BINARY)
    @RequiresApi(16)
    public annotation class Fts3(val tokenizer: String = TOKENIZER_SIMPLE, val tokenizerArgs: Array<String> = [])
    """.trimIndent(),

  "androidx/room/Fts4.kt" to """
    package androidx.room
  
    @Target(AnnotationTarget.CLASS)
    @Retention(AnnotationRetention.BINARY)
    @RequiresApi(16)
    public annotation class Fts4(
      val tokenizer: String = TOKENIZER_SIMPLE,
      val tokenizerArgs: Array<String> = [],
      val contentEntity: KClass<*> = Any::class,
      val languageId: String = "",
      val matchInfo: MatchInfo = MatchInfo.FTS4,
      val notIndexed: Array<String> = [],
      val prefix: IntArray = [],
      val order: Order = Order.ASC
    )
    """.trimIndent()
)

fun createStubRoomClasses(codeInsightTestFixture: JavaCodeInsightTestFixture, useJavaSource: Boolean = true) {
  if (useJavaSource){
    roomAnnotationToClassBodyJava.values.forEach { codeInsightTestFixture.addClass(it) }
  }
  else {
    roomAnnotationToClassBodyKotlin.forEach { (file, content) -> codeInsightTestFixture.addFileToProject(file, content) }
  }
}

fun createStubRoomClassesInPath(codeInsightTestFixture: JavaCodeInsightTestFixture, path: String) {
  roomAnnotationToClassBodyJava.forEach {
    codeInsightTestFixture.addFileToProject(path + "/androidx/room/${it.key}.java", it.value)
  }
}

data class FieldDefinition(val name: String, val type: String, val columnName: String? = null)

infix fun String.ofType(type: String): FieldDefinition = FieldDefinition(this, type)

fun JavaCodeInsightTestFixture.classPointer(qualifiedClassName: String): SmartPsiElementPointer<PsiClass> {
  return SmartPointerManager.getInstance(project).createSmartPsiElementPointer(findClass(qualifiedClassName))
}

fun JavaCodeInsightTestFixture.findField(
  qualifiedClassName: String,
  fieldName: String,
  checkBases: Boolean = false
): PsiField {
  return findClass(qualifiedClassName).findFieldByName(fieldName, checkBases)!!
}

fun JavaCodeInsightTestFixture.findMethod(
  qualifiedClassName: String,
  methodName: String,
  checkBases: Boolean = false
): PsiMethod {
  return findClass(qualifiedClassName).findMethodsByName(methodName, checkBases).first()!!
}

fun JavaCodeInsightTestFixture.fieldPointer(
  qualifiedClassName: String,
  fieldName: String,
  checkBases: Boolean = false
): SmartPsiElementPointer<PsiField> {
  return SmartPointerManager.getInstance(project).createSmartPsiElementPointer(findField(qualifiedClassName, fieldName, checkBases))
}

fun JavaCodeInsightTestFixture.methodPointer(
  qualifiedClassName: String,
  methodName: String,
  checkBases: Boolean = false
): SmartPsiElementPointer<PsiMethod> {
  return SmartPointerManager.getInstance(project).createSmartPsiElementPointer(findMethod(qualifiedClassName, methodName, checkBases))
}

fun JavaCodeInsightTestFixture.addRoomEntity(
  qualifiedClassName: String,
  vararg fields: FieldDefinition
): PsiClass {
  return addRoomEntity(qualifiedClassName, tableNameOverride = null, fields = *fields)
}

val JavaCodeInsightTestFixture.referenceAtCaret get() = file.findReferenceAt(caretOffset)!!

fun JavaCodeInsightTestFixture.addRoomEntity(
  qualifiedClassName: String,
  tableNameOverride: String?,
  vararg fields: FieldDefinition
): PsiClass {
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

      import androidx.room.Entity;
      import androidx.room.ColumnInfo;

      @Entity$annotationArguments
      public class $className { $fieldsSnippet }
      """.trimIndent()
  )
}
