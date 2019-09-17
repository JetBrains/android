/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.lang.com.android.tools.idea.lang.proguardR8.psi

import com.android.tools.idea.lang.androidSql.referenceAtCaret
import com.android.tools.idea.lang.com.android.tools.idea.lang.proguardR8.ProguardR8TestCase
import com.android.tools.idea.lang.proguardR8.ProguardR8FileType
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8FieldName
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.PsiPrimitiveType

class ProguardR8FieldsTest : ProguardR8TestCase() {

  fun testReturnsCorrectType() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      class MyClass {
        int myInt;
        String myString;
      }
    """.trimIndent())

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClass {
        int myIn${caret}t;
        java.lang.String myString;
      }
      """.trimIndent())

    var fieldName = myFixture.referenceAtCaret.element as ProguardR8FieldName
    assertThat(fieldName.type).isNotNull()
    assertThat(fieldName.type!!.matchesPsiType(PsiPrimitiveType.INT)).isTrue()

    myFixture.moveCaret("my|String")
    fieldName = myFixture.referenceAtCaret.element as ProguardR8FieldName
    assertThat(fieldName.type).isNotNull()

    val realType = myFixture.findClass("test.MyClass").findFieldByName("myString", false)!!.type
    assertThat(fieldName.type!!.matchesPsiType(realType)).isTrue()
  }

  fun testFieldReferenceCorrectPrimitiveType() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      class MyClass {
        int myField;
      }
    """.trimIndent())

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClass {
        int myFie${caret}ld;
      }
      """.trimIndent())

    val field = myFixture.elementAtCaret

    assertThat(field).isNotNull()
    assertThat(field).isEqualTo(myFixture.findClass("test.MyClass").findFieldByName("myField", false))
  }

  fun testFieldReferenceAnyPrimitiveType() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      class MyClass {
        int myPrimitive;
        String myNotPrimitive;
      }
    """.trimIndent())

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClass {
        % myPrimitive;
        % myNotPrimitive
      }
      """.trimIndent())

    myFixture.moveCaret("myPrimiti|ve")
    val field = myFixture.elementAtCaret

    assertThat(field).isNotNull()
    assertThat(field).isEqualTo(myFixture.findClass("test.MyClass").findFieldByName("myPrimitive", false))

    myFixture.moveCaret("myNotPrimiti|ve")
    val field2 = myFixture.referenceAtCaret.resolve()

    assertThat(field2).isNull()
  }

  fun testFieldReferenceAnyType() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      class MyClass {
        StringBuilder myField;
      }
    """.trimIndent())

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClass {
        *** myFie${caret}ld;
      }
      """.trimIndent())

    val field = myFixture.elementAtCaret

    assertThat(field).isNotNull()
    assertThat(field).isEqualTo(myFixture.findClass("test.MyClass").findFieldByName("myField", false))
  }

  fun testFieldReferenceCorrectType() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      class MyClass {
        String myField;
      }
    """.trimIndent())

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClass {
        java.lang.String myFie${caret}ld;
      }
      """.trimIndent())

    val field = myFixture.elementAtCaret

    assertThat(field).isNotNull()
    assertThat(field).isEqualTo(myFixture.findClass("test.MyClass").findFieldByName("myField", false))
  }

  fun testFieldReferenceIncorrectType() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      class MyClass {
        StringBuilder myField;
      }
    """.trimIndent())

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClass {
        java.lang.String myFie${caret}ld;
      }
      """.trimIndent())

    val field = myFixture.referenceAtCaret.resolve()

    assertThat(field).isNull()
  }

  fun testFieldReferenceIncorrectPrimitiveType() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      class MyClass {
        long myField;
      }
    """.trimIndent())

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClass {
        int myFie${caret}ld;
      }
      """.trimIndent())

    val field = myFixture.referenceAtCaret.resolve()

    assertThat(field).isNull()
  }

  fun testSuggestFieldsPrimitiveType() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      class MyClass {
        boolean myBooleanField;
        boolean myBooleanField2;
        int myNotBooleanField;
      }
    """.trimIndent())

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClass {
        boolean ${caret};
      }
      """.trimIndent())

    val field = myFixture.completeBasic()

    assertThat(field).isNotEmpty()
    assertThat(field.map { it.lookupString }).containsExactly("myBooleanField", "myBooleanField2")
  }

  fun testSuggestFieldsAnyPrimitiveType() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      class MyClass {
        boolean myPrimitive;
        byte myPrimitive2;
        int myPrimitive3;
        String myNotPrimitive;
      }
    """.trimIndent())

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClass {
        % ${caret};
      }
      """.trimIndent())

    val field = myFixture.completeBasic()

    assertThat(field).isNotEmpty()
    assertThat(field.map { it.lookupString }).containsExactly("myPrimitive", "myPrimitive2", "myPrimitive3")
  }

  fun testSuggestFieldsAnyType() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      class MyClass {
        boolean myPrimitive;
        byte myPrimitive2;
        int myPrimitive3;
        String myNotPrimitive;
      }
    """.trimIndent())

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClass {
        *** ${caret};
      }
      """.trimIndent())

    val field = myFixture.completeBasic()

    assertThat(field).isNotEmpty()
    assertThat(field.map { it.lookupString }).containsExactly("myPrimitive", "myPrimitive2", "myPrimitive3", "myNotPrimitive")
  }

  fun testNotSuggestFields() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      class MyClass {
        boolean myPrimitive;
        byte myPrimitive2;
        int myPrimitive3;
        String myNotPrimitive;
      }
    """.trimIndent())

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClass {
        long ${caret};
      }
      """.trimIndent())

    val field = myFixture.completeBasic()

    assertThat(field).isEmpty()
  }

  fun testRenameField() {
    myFixture.addClass(
      //language=JAVA
      """
      //package test;

      class MyClass {
        int myField;
      }
    """.trimIndent())

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class MyClass {
        int myFie${caret}ld;
      }
      """.trimIndent())

    myFixture.renameElementAtCaret("myFieldNew")

    val newField = myFixture.findClass("MyClass").findFieldByName("myFieldNew", false)
    assertThat(newField).isNotNull()

    myFixture.checkResult("""
      -keep class MyClass {
        int myFieldNew;
      }
    """.trimIndent())
  }
}