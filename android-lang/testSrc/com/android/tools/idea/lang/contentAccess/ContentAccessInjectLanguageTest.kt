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

import com.android.tools.idea.lang.contentAccess.parser.ContentAccessFile
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.highlighter.JavaFileType
import org.jetbrains.kotlin.idea.KotlinFileType

class ContentAccessInjectLanguageTest : ContentAccessTestCase() {

  fun test() {
    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
        import androidx.contentaccess.*
        
        @ContentAccessObject(Event::class)
        interface CalendarAccessor {
          @ContentQuery(uri = "dtst<caret>art > :t")
          fun getAllEventsAfter(t: Long, uri: String): List<Long>
        }
      """.trimIndent()
    )

    var psiFile = myFixture.file.findElementAt(myFixture.caretOffset)!!.containingFile
    assertThat(psiFile is ContentAccessFile).isFalse()

    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
        import androidx.contentaccess.*
        
        @ContentAccessObject(Event::class)
        interface CalendarAccessor0 {
          @ContentQuery(uri = "uri", selection = "dtst<caret>art > :t")
          fun getAllEventsAfter(t: Long, uri: String): List<Long>
        }
      """.trimIndent()
    )

    psiFile = myFixture.file.findElementAt(myFixture.caretOffset)!!.containingFile
    assertThat(psiFile is ContentAccessFile).isTrue()

    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
        import androidx.contentaccess.*
        
        @ContentAccessObject(Event::class)
        interface CalendarAccessor1 {
          @ContentQuery(selection = "dtst<caret>art > :t")
          fun getAllEventsAfter(t: Long, uri: String): List<Long>
        }
      """.trimIndent()
    )

    psiFile = myFixture.file.findElementAt(myFixture.caretOffset)!!.containingFile
    assertThat(psiFile is ContentAccessFile).isTrue()

    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
        import androidx.contentaccess.*
        
        @ContentAccessObject(Event::class)
        interface CalendarAccessor2 {
          @ContentUpdate(uri = "dtsta<caret>rt > :t")
          fun updateEventsAfter(t: Long): Int
        }
      """.trimIndent()
    )

    psiFile = myFixture.file.findElementAt(myFixture.caretOffset)!!.containingFile
    assertThat(psiFile is ContentAccessFile).isFalse()

    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
        import androidx.contentaccess.*
        
        @ContentAccessObject(Event::class)
        interface CalendarAccessor3 {
          @ContentUpdate(where = "dtsta<caret>rt > :t")
          fun updateEventsAfter(t: Long): Int
        }
      """.trimIndent()
    )

    psiFile = myFixture.file.findElementAt(myFixture.caretOffset)!!.containingFile
    assertThat(psiFile is ContentAccessFile).isTrue()

    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
        import androidx.contentaccess.*
        
        @ContentAccessObject(Event::class)
        interface CalendarAccessor4 {
          @ContentDelete(selection = "dts<caret>tart > :t")
          fun deleteEventsAfter(t: Long): Int
        }
      """.trimIndent()
    )

    psiFile = myFixture.file.findElementAt(myFixture.caretOffset)!!.containingFile
    assertThat(psiFile is ContentAccessFile).isFalse()

    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
        import androidx.contentaccess.*
        
        @ContentAccessObject(Event::class)
        interface CalendarAccessor4 {
          @ContentDelete(where = "dts<caret>tart > :t")
          fun deleteEventsAfter(t: Long): Int
        }
      """.trimIndent()
    )

    psiFile = myFixture.file.findElementAt(myFixture.caretOffset)!!.containingFile
    assertThat(psiFile is ContentAccessFile).isTrue()


    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
        import androidx.contentaccess.*
        
        @ContentAccessObject(Event::class)
        interface CalendarAccessor4 {
          @ContentDelete("dts<caret>tart > :t")
          fun deleteEventsAfter(t: Long): Int
        }
      """.trimIndent()
    )

    psiFile = myFixture.file.findElementAt(myFixture.caretOffset)!!.containingFile
    assertThat(psiFile is ContentAccessFile).isTrue()


    myFixture.configureByText(
      KotlinFileType.INSTANCE,
      //language=kotlin
      """
        import androidx.contentaccess.*
        
        @ContentAccessObject(Event::class)
        interface CalendarAccessor4 {
          @ContentDelete(uri = "<caret>", where = "dtstart > :t")
          fun deleteEventsAfter(t: Long): Int
        }
      """.trimIndent()
    )

    psiFile = myFixture.file.findElementAt(myFixture.caretOffset)!!.containingFile
    assertThat(psiFile is ContentAccessFile).isFalse()
  }

  fun testJava() {

    myFixture.configureByText(
      JavaFileType.INSTANCE,
      //language=JAVA
      """
        import androidx.contentaccess.*;
        
        @ContentAccessObject(contentEntity = Event.class)
        class CalendarAccessor1 {
          @ContentDelete(where = "dts<caret>tart > :t")
          public int deleteEventsAfter(long t) {}
        }
      """.trimIndent()
    )

    var psiFile = myFixture.file.findElementAt(myFixture.caretOffset)!!.containingFile
    assertThat(psiFile is ContentAccessFile).isTrue()

    myFixture.configureByText(
      JavaFileType.INSTANCE,
      //language=JAVA
      """
        import androidx.contentaccess.*;
        
        @ContentAccessObject(contentEntity = Event.class)
        class CalendarAccessor2 {
          @ContentQuery(selection = "dtsta<caret>rt > :t")
          public boolean getAllEventsAfter(long t) {}
        }
      """.trimIndent()
    )

    psiFile = myFixture.file.findElementAt(myFixture.caretOffset)!!.containingFile
    assertThat(psiFile is ContentAccessFile).isTrue()


    myFixture.configureByText(
      JavaFileType.INSTANCE,
      //language=JAVA
      """
        import androidx.contentaccess.*;
        
        @ContentAccessObject(contentEntity = Event.class)
        class CalendarAccessor3 {
          
          @ContentUpdate(where = "dtsta<caret>rt > :t")
          public int updateEventsAfter(lomg t) {}
        }
      """.trimIndent()
    )

    psiFile = myFixture.file.findElementAt(myFixture.caretOffset)!!.containingFile
    assertThat(psiFile is ContentAccessFile).isTrue()
  }
}