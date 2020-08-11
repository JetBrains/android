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
package com.android.tools.idea.lang.com.android.tools.idea.lang.contentAccess

import com.android.tools.idea.lang.contentAccess.ContentAccessSchemaManager
import com.android.tools.idea.lang.contentAccess.ContentAccessTestCase
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.highlighter.JavaFileType
import org.jetbrains.kotlin.idea.KotlinFileType

class ContentAccessSchemaManagerTest : ContentAccessTestCase() {
  fun test_javaEntity() {
    val file =
      myFixture.configureByText(
        JavaFileType.INSTANCE,
        //language=JAVA
        """
        package test;

        import androidx.contentaccess.ContentColumn;
        import androidx.contentaccess.ContentPrimaryKey;
        import androidx.contentaccess.ContentEntity;

        @ContentEntity(uri = "uri")
        public class Entity {

            static final String DTSTART_CONST = "dtstart";

            @ContentPrimaryKey(columnName = "_id")
            public long eventId;

            @ContentColumn(columnName = DTSTART_CONST)
            public long startTime;
        }
      """.trimIndent()
      )

    val schema = ContentAccessSchemaManager.getInstance(module).getSchema(file)

    assertThat(schema).isNotNull()
    assertThat(schema!!.contentAccessEntities).hasSize(1)
    assertThat(schema.contentAccessEntities[0].name).isEqualTo("Entity")
    val columns = schema.contentAccessEntities[0].columns
    assertThat(columns).hasSize(2)
    val names = columns.map { it.name }
    assertThat(names).containsExactly("_id", "dtstart")
    assertThat(columns.find { it.name == "_id" }!!.isPrimaryKey).isTrue()
  }

  fun test_kotlinEntity() {
    val file =
      myFixture.configureByText(
        KotlinFileType.INSTANCE,
        //language=kotlin
        """
        package test;

        import androidx.contentaccess.ContentColumn
        import androidx.contentaccess.ContentPrimaryKey
        import androidx.contentaccess.ContentEntity

        const val TITLE_CONST = "title"

        @ContentEntity(uri = "uri")
        data class Image(
        @ContentPrimaryKey("id")
        var iD: Long,
        @ContentColumn(TITLE_CONST)
        var title: String?
        )
      """.trimIndent()
      )

    val schema = ContentAccessSchemaManager.getInstance(module).getSchema(file)

    assertThat(schema).isNotNull()
    assertThat(schema!!.contentAccessEntities).hasSize(1)
    assertThat(schema.contentAccessEntities[0].name).isEqualTo("Image")
    val columns = schema.contentAccessEntities[0].columns
    assertThat(columns).hasSize(2)
    val names = columns.map { it.name }
    assertThat(names).containsExactly("id", "title")
    assertThat(columns.find { it.name == "id" }!!.isPrimaryKey).isTrue()
  }
}