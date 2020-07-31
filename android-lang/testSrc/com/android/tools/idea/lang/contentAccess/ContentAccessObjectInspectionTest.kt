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
import org.jetbrains.kotlin.idea.KotlinFileType

/**
 * Tests for [ContentAccessObjectInspection] and [ContentAccessObjectInspectionKotlin] inspections.
 */
class ContentAccessObjectInspectionTest : ContentAccessTestCase() {

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(ContentAccessObjectInspectionKotlin())
    myFixture.enableInspections(ContentAccessObjectInspection())
    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package test

      import androidx.contentaccess.ContentColumn
      import androidx.contentaccess.ContentEntity
      import androidx.contentaccess.ContentPrimaryKey

      @ContentEntity("content://com.android.calendar/events")
      data class Event(
         @ContentPrimaryKey("_id")
         var id: Long,
         @ContentColumn("calendar_id")
         var calendarId: Long,
         @ContentColumn("title")
         var title: String,
         @ContentColumn("description")
         var description: String?,
         @ContentColumn("dtstart")
         var startTime: Long,
         @ContentColumn("dtend")
         var endTime: Long,
      )
    """.trimIndent())
  }

  fun testNoEntity() {
    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package test

      import androidx.contentaccess.ContentAccessObject
      import androidx.contentaccess.ContentQuery

      @ContentAccessObject
      interface CalendarAccessor {
      @ContentQuery(selection = "dtstart > :t", projection = arrayOf("_id"), uri = ":uri")
          fun getAllEventsAfter(t: Long, uri: String): List<Long>
      }

    """.trimIndent())

    val errors = myFixture.doHighlighting(HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING).map { it.description }
    assertThat(errors).containsExactly("Unable to resolve content entity for ContentQuery")

    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package test

      import androidx.contentaccess.ContentAccessObject
      import androidx.contentaccess.ContentQuery

      @ContentAccessObject(Event::class)
      interface CalendarAccessor2 {
      @ContentQuery(selection = "dtstart > :t", projection = arrayOf("_id"), uri = ":uri")
          fun getAllEventsAfter(t: Long, uri: String): List<Long>
      }

    """.trimIndent())

    assertThat(myFixture.doHighlighting(HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING)).isEmpty()

    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
      package test

      import androidx.contentaccess.ContentAccessObject
      import androidx.contentaccess.ContentQuery

      @ContentAccessObject()
      interface CalendarAccessor3 {
      @ContentQuery(selection = "dtstart > :t", projection = arrayOf("_id"), uri = ":uri", contentEntity = Event::class)
          fun getAllEventsAfter(t: Long, uri: String): List<Long>
      }

    """.trimIndent())

    assertThat(myFixture.doHighlighting(HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING)).isEmpty()
  }

  fun testNoEntity_java() {
    myFixture.configureByText(
      "test/Accessor.java",
      //language=JAVA
      """
      package test;

      import androidx.contentaccess.ContentUpdate;

      public class Accessor {
        @ContentUpdate()
        public int startTime() {return 1;}
      }
    """.trimIndent())

    val errors = myFixture.doHighlighting(HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING).map { it.description }
    assertThat(errors).containsExactly(
      "Unable to resolve content entity for ContentUpdate"
    )
    myFixture.configureByText(
      "test/Accessor2.java",
      //language=JAVA
      """
      package test;

      import androidx.contentaccess.ContentAccessObject;
      import androidx.contentaccess.ContentUpdate;

      @ContentAccessObject(contentEntity = Event.class)
      public class Accessor2 {
        @ContentUpdate()
        public int startTime() {return 1;}
      }
      """.trimIndent()
    )

    assertThat(myFixture.doHighlighting(HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING)).isEmpty()

    myFixture.configureByText(
      "test/Accessor3.java",
      //language=JAVA
      """
      package test;

      import androidx.contentaccess.ContentAccessObject;
      import androidx.contentaccess.ContentUpdate;

      @ContentAccessObject()
      public class Accessor3 {
        @ContentUpdate(contentEntity = Event.class)
        public int startTime() {return 1;}
      }
      """.trimIndent()
    )

    assertThat(myFixture.doHighlighting(HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING)).isEmpty()
  }
}