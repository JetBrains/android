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
package com.android.tools.idea.lang.proguardR8.psi

import com.android.tools.idea.lang.androidSql.referenceAtCaret
import com.android.tools.idea.lang.proguardR8.ProguardR8FileType
import com.android.tools.idea.lang.proguardR8.ProguardR8TestCase
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.highlightedAs
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.intellij.lang.annotation.HighlightSeverity.ERROR
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.PsiTypes
import com.intellij.psi.util.parentOfType

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
    var fieldName = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<ProguardR8ClassMember>()!!
    assertThat(fieldName.type).isNotNull()
    assertThat(fieldName.type!!.matchesPsiType(PsiTypes.intType())).isTrue()

    myFixture.moveCaret("my|String")
    fieldName = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<ProguardR8ClassMember>()!!
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

  fun testInspectionOnField() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      class MyClass {
        boolean myBoolean;
      }
    """.trimIndent())

    // wrong type
    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClass {
        long ${"myBoolean".highlightedAs(ERROR, "The rule matches no class members")};
      }
      """.trimIndent())

    myFixture.checkHighlighting()

    // wrong name
    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClass {
        boolean ${"myNotBoolean" highlightedAs ERROR};
      }
      """.trimIndent())

    myFixture.checkHighlighting()

    // don't highlight if class is unknown
    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.My* {
        long myBoolean;
      }
      """.trimIndent())

    // don't highlight if class is unknown, but super class is known
    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class * extends test.MyClass {
        foo;
      }
      """.trimIndent())

    myFixture.checkHighlighting()

    // don't highlight if class is unknown (2)
    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class ${"test.MyNotExistingClass".highlightedAs(ERROR, "Unresolved class name")} {
        long myBoolean;
      }
      """.trimIndent())

    myFixture.checkHighlighting()

    // don't highlight if field is with wildcards
    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClass {
        long m*;
      }
      """.trimIndent())

    myFixture.checkHighlighting()
  }

  fun testCodeCompletionWithoutType() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      class MyClass {
        boolean[] myField1;
        int myField2;
        String myFiled3;
      }
    """.trimIndent())

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClass {
         $caret
      """.trimIndent()
    )
    val fields = myFixture.completeBasic()

    // suggest fields with different types
    assertThat(fields.map { it.lookupString }).containsAllOf("myField1", "myField2", "myFiled3")
  }

  fun testResolveFieldWithoutType() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      class MyClass {
        boolean[] myField;
      }
    """.trimIndent())

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClass {
         my${caret}Field;
      }
      """.trimIndent()
    )
    val fields = (myFixture.referenceAtCaret as PsiPolyVariantReference).multiResolve(false).toList()

    assertThat(fields).hasSize(1)
    assertThat(fields.map { it.element!!.text }).contains("boolean[] myField;")
  }

  fun testResolveFieldWithRightAccessModifier() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      class MyClass {
        int myPackagePrivate;
        public int myPublic;
        private int myPrivate;
        protected int myProtected;
      }
    """.trimIndent())

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClass {
         my;
         public my;
         !public my;
         private my;
         private protected my;
         !private !protected my;
         !private !protected !public my;
         private !private my;
      }
      """.trimIndent()
    )
    myFixture.moveCaret("m|y;")
    var fields = (myFixture.referenceAtCaret as ProguardR8ClassMemberNameReference).variants.map { it.lookupString }
    assertThat(fields).containsExactly("myPackagePrivate", "myPublic", "myPrivate", "myProtected")

    myFixture.moveCaret("public m|y;")
    fields = (myFixture.referenceAtCaret as ProguardR8ClassMemberNameReference).variants.map { it.lookupString }
    assertThat(fields).containsExactly("myPublic")

    myFixture.moveCaret("!public m|y;")
    fields = (myFixture.referenceAtCaret as ProguardR8ClassMemberNameReference).variants.map { it.lookupString }
    assertThat(fields).containsExactly("myPackagePrivate", "myPrivate", "myProtected")

    myFixture.moveCaret("private m|y;")
    fields = (myFixture.referenceAtCaret as ProguardR8ClassMemberNameReference).variants.map { it.lookupString }
    assertThat(fields).containsExactly("myPrivate")

    myFixture.moveCaret("private protected m|y;")
    fields = (myFixture.referenceAtCaret as ProguardR8ClassMemberNameReference).variants.map { it.lookupString }
    assertThat(fields).containsExactly("myPrivate", "myProtected")

    myFixture.moveCaret("!private !protected m|y;")
    fields = (myFixture.referenceAtCaret as ProguardR8ClassMemberNameReference).variants.map { it.lookupString }
    assertThat(fields).containsExactly("myPackagePrivate", "myPublic")

    myFixture.moveCaret("!private !protected !public m|y;")
    fields = (myFixture.referenceAtCaret as ProguardR8ClassMemberNameReference).variants.map { it.lookupString }
    assertThat(fields).containsExactly("myPackagePrivate")

    myFixture.moveCaret("private !private m|y;")
    fields = (myFixture.referenceAtCaret as ProguardR8ClassMemberNameReference).variants.map { it.lookupString }
    assertThat(fields).isEmpty()
  }

  fun testResolveFieldWithRightAccessModifierKotlin() {
    myFixture.addFileToProject(
      "myClass.kt",
      """
      package test;

      class MyClass {
        internal var myInternal:Int;
        var myPublic:Int;
      }
    """.trimIndent())

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClass {
         my;
         public my;
         !public my;
         private my;
         private protected my;
         !private !protected my;
         !private !protected !public my;
         private !private my;
      }
      """.trimIndent()
    )
    myFixture.moveCaret("m|y;")
    var fields = (myFixture.referenceAtCaret as ProguardR8ClassMemberNameReference).variants.map { it.lookupString }
    assertThat(fields).containsAllOf("myInternal", "myPublic")

    myFixture.moveCaret("public m|y;")
    fields = (myFixture.referenceAtCaret as ProguardR8ClassMemberNameReference).variants.map { it.lookupString }
    // There are public getter and setter getMyPublic, setMyPublic, but not a field
    assertThat(fields).doesNotContain("myPublic")
  }
}
