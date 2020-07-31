/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.lang.contentAccess

import com.google.common.truth.Truth.assertThat
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.KotlinFileType

class ContentEntityInspectionTest : ContentAccessTestCase() {

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(ContentEntityInspection())
    myFixture.enableInspections(ContentEntityInspectionKotlin())
  }

  fun testAnnotationNotInContentEntityClass() {
    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package test

      import androidx.contentaccess.ContentColumn
      import androidx.contentaccess.ContentPrimaryKey

      data class Image(
        @ContentPrimaryKey("id")
        var iD: Long,
        @ContentColumn("title")
        var title: String?
      )
    """.trimIndent())

    val errors = myFixture.doHighlighting(HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING)
    val errorTexts = errors.map { it.description }
    assertThat(errorTexts).containsExactly(
      "ContentPrimaryKey is only useful within @ContentEntity annotated class",
      "ContentColumn is only useful within @ContentEntity annotated class"
    )

    val fix = errors.first().quickFixActionRanges.first().first
    assertThat(fix).isNotNull()
    WriteCommandAction.writeCommandAction(project).run(ThrowableRunnable<Throwable> {
      fix.action.invoke(project, myFixture.editor, myFixture.file)
    })
    myFixture.checkResult(
      //language=kotlin
      """
      package test

      import androidx.contentaccess.ContentColumn
      import androidx.contentaccess.ContentEntity
      import androidx.contentaccess.ContentPrimaryKey

      @ContentEntity
      data class Image(
        @ContentPrimaryKey("id")
        var iD: Long,
        @ContentColumn("title")
        var title: String?
      )
      """.trimIndent()
    )
  }

  fun testAnnotationNotInContentEntityClass_java() {
    myFixture.configureByText(
      "test/Entity.java",
      //language=JAVA
      """
      package test;

      import androidx.contentaccess.ContentColumn;
      import androidx.contentaccess.ContentPrimaryKey;

      public class Entity {
        @ContentPrimaryKey(columnName = "dtstart")
        public Long startTime;

        @ContentColumn(columnName = "dtend")
        public Long endTime;
      }
    """.trimIndent())

    val errors = myFixture.doHighlighting(HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING)
    val errorTexts = errors.map { it.description }
    assertThat(errorTexts).containsExactly(
      "ContentPrimaryKey is only useful within @ContentEntity annotated class",
      "ContentColumn is only useful within @ContentEntity annotated class"
    )

    val fix = errors.first().quickFixActionRanges.first().first
    assertThat(fix).isNotNull()
    WriteCommandAction.writeCommandAction(project).run(ThrowableRunnable<Throwable> {
      fix.action.invoke(project, myFixture.editor, myFixture.file)
    })
    myFixture.checkResult(
      //language=JAVA
      """
      package test;

      import androidx.contentaccess.ContentColumn;
      import androidx.contentaccess.ContentEntity;
      import androidx.contentaccess.ContentPrimaryKey;

      @ContentEntity
      public class Entity {
        @ContentPrimaryKey(columnName = "dtstart")
        public Long startTime;

        @ContentColumn(columnName = "dtend")
        public Long endTime;
      }
      """.trimIndent())
  }

  fun testContentEntityWithoutContentPrimaryKey() {
    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package test

      import androidx.contentaccess.ContentColumn
      import androidx.contentaccess.ContentEntity

      @ContentEntity
      data class Image(
        @ContentColumn("title")
        var title: String?
      )
    """.trimIndent())

    val errors = myFixture.doHighlighting(HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING).map { it.description }
    assertThat(errors).containsExactly("Classes annotated with @ContentEntity must include a field annotated with @ContentPrimaryKey")
  }

  fun testContentEntityWithoutContentPrimaryKey_java() {
    myFixture.configureByText(
      "test/Entity.java",
      //language=JAVA
      """
      package test;

      import androidx.contentaccess.ContentColumn;
      import androidx.contentaccess.ContentEntity;

      @ContentEntity
      public class Entity {
        @ContentColumn(columnName = "dtend")
        public Long endTime;
      }
    """.trimIndent())

    val errors = myFixture.doHighlighting(HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING).map { it.description }
    assertThat(errors).containsExactly("Classes annotated with @ContentEntity must include a field annotated with @ContentPrimaryKey")
  }

  fun testContentEntityNotAllFieldAnnotated() {
    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package test

      import androidx.contentaccess.ContentPrimaryKey
      import androidx.contentaccess.ContentEntity

      @ContentEntity
      data class Image(
        @ContentPrimaryKey("title")
        var title: String?,

        var notAnnotated: String?
      )
    """.trimIndent())

    val errors = myFixture.doHighlighting(HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING).map { it.description }
    assertThat(errors).containsExactly(
      "All fields in a class annotated with @ContentEntity must also be annotated with either @ContentPrimaryKey or @ContentColumn"
    )
  }

  fun testContentEntityNotAllFieldAnnotated_java() {
    myFixture.configureByText(
      "test/Entity.java",
      //language=JAVA
      """
      package test;

      import androidx.contentaccess.ContentPrimaryKey;
      import androidx.contentaccess.ContentEntity;

      @ContentEntity
      public class Entity {
        @ContentPrimaryKey(columnName = "key")
        public Long key;

        public Long notAnnotated;
      }
    """.trimIndent())

    val errors = myFixture.doHighlighting(HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING).map { it.description }
    assertThat(errors).containsExactly(
      "All fields in a class annotated with @ContentEntity must also be annotated with either @ContentPrimaryKey or @ContentColumn"
    )
  }

  fun testContentEntityMoreThanOnePrimaryKey() {
    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package test

      import androidx.contentaccess.ContentPrimaryKey
      import androidx.contentaccess.ContentEntity
      import androidx.contentaccess.ContentColumn

      @ContentEntity
      data class Image(
        @ContentPrimaryKey("key")
        var key: String?,
        @ContentPrimaryKey("extraKey")
        var extraKey: String?,
        @ContentColumn("column")
        var column: String?
      )
    """.trimIndent())

    val errors = myFixture.doHighlighting(HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING).map { it.description }
    assertThat(errors).containsExactly(
      "@ContentEntity must include only one @ContentPrimaryKey",
      "@ContentEntity must include only one @ContentPrimaryKey"
    )
  }

  fun testContentEntityMoreThanOnePrimaryKey_java() {
    myFixture.configureByText(
      "test/Entity.java",
      //language=JAVA
      """
      package test;

      import androidx.contentaccess.ContentPrimaryKey;
      import androidx.contentaccess.ContentEntity;
      import androidx.contentaccess.ContentColumn;

      @ContentEntity
      public class Entity {
        @ContentPrimaryKey(columnName = "key")
        public Long key;
        @ContentPrimaryKey(columnName = "key")
        public Long extraKey;
        @ContentColumn(columnName = "column")
        public Long column;
      }
    """.trimIndent())

    val errors = myFixture.doHighlighting(HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING).map { it.description }
    assertThat(errors).containsExactly(
      "@ContentEntity must include only one @ContentPrimaryKey",
      "@ContentEntity must include only one @ContentPrimaryKey"
    )
  }

  fun testInvalidColumnType() {
    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package test

      import androidx.contentaccess.ContentPrimaryKey
      import androidx.contentaccess.ContentEntity
      import androidx.contentaccess.ContentColumn

      @ContentEntity
      data class EntityKt (
        @ContentPrimaryKey("key")
        var key: String?,

        @ContentColumn("valid1")
        var valid1: Int,
        @ContentColumn("valid2")
        var valid2: ByteArray,
        @ContentColumn("valid3")
        var valid3: String?,
        @ContentColumn("valid4")
        var valid4: Array<Byte>,
        @ContentColumn("valid5")
        var valid5: Long,

        <error descr="Field annotated with @ContentColumn is not of a valid type">@ContentColumn("invalid1")
        var invalid1: EntityKt</error>,
        <error descr="Field annotated with @ContentColumn is not of a valid type">@ContentColumn("invalid2")
        var invalid2: Array<String></error>,
        <error descr="Field annotated with @ContentColumn is not of a valid type">@ContentColumn("invalid3")
        var invalid3: Byte</error>
      )
    """.trimIndent())

    myFixture.checkHighlighting()
  }

  fun testInvalidColumnType_java() {
    myFixture.configureByText(
      "test/Entity.java",
      //language=JAVA
      """
      package test;

      import androidx.contentaccess.ContentPrimaryKey;
      import androidx.contentaccess.ContentEntity;
      import androidx.contentaccess.ContentColumn;

      @ContentEntity
      public class Entity {
        @ContentPrimaryKey(columnName = "key")
        public Long extraKey;

        @ContentColumn(columnName = "valid1")
        public Long valid1;
        @ContentColumn(columnName = "valid2")
        public Byte[] valid2;
        @ContentColumn(columnName = "valid3")
        public int valid3;
        @ContentColumn(columnName = "valid4")
        public String valid4;
        @ContentColumn(columnName = "valid5")
        public byte[] valid5;

        <warning descr="Field annotated with @ContentColumn is not of a valid type">@ContentColumn(columnName = "invalid1")
        public String[] invalid1;</warning>
        <warning descr="Field annotated with @ContentColumn is not of a valid type">@ContentColumn(columnName = "invalid2")
        public Byte invalid2;</warning>
        <warning descr="Field annotated with @ContentColumn is not of a valid type">@ContentColumn(columnName = "invalid3")
        public Entity invalid3;</warning>
      }
    """.trimIndent())

   myFixture.checkHighlighting()
  }
}