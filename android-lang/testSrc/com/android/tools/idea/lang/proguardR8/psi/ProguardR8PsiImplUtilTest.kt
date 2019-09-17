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

import com.android.tools.idea.lang.com.android.tools.idea.lang.proguardR8.ProguardR8TestCase
import com.android.tools.idea.lang.proguardR8.ProguardR8FileType
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8JavaPrimitive
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8QualifiedName
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8Type
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.util.parentOfType

class ProguardR8PsiImplUtilTest : ProguardR8TestCase() {

  fun testResolvePsiClassFromQualifiedName() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      class MyClass {}
    """.trimIndent())

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClas${caret}s {}
      """.trimIndent())

    val qName = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType(ProguardR8QualifiedName::class)

    assertThat(qName).isNotNull()
    assertThat(qName!!.resolveToPsiClass()).isEqualTo(myFixture.findClass("test.MyClass"))
  }

  fun testMatchesArrayType() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      class MyClass {
        String[] field;
        List<String> field2;
      }
    """.trimIndent())

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClass {
        java.lang.S${caret}tring[] field;
        java.util.List field2;
      }
      """.trimIndent())

    var type = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType(ProguardR8Type::class)
    assertThat(type).isNotNull()
    val arrayStringType = JavaPsiFacade.getElementFactory(project).createTypeFromText("java.lang.String[]", null)
    assertThat(type!!.matchesPsiType(arrayStringType)).isTrue()
    val stringType = JavaPsiFacade.getElementFactory(project).createTypeFromText("java.lang.String", null)
    assertThat(type.matchesPsiType(stringType)).isFalse()
    val listType = JavaPsiFacade.getElementFactory(project).createTypeFromText("java.util.List", null)
    assertThat(type.matchesPsiType(listType)).isFalse()

    myFixture.moveCaret("Li|st")
    type = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType(ProguardR8Type::class)!!
    assertThat(type.matchesPsiType(listType)).isTrue()
  }

  fun testGetPsiPrimitive() {

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClass {
        in${caret}t myPrimitive;
        byte myPrimitive2;
      }
      """.trimIndent())

    var primitiveType = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType(ProguardR8JavaPrimitive::class)

    assertThat(primitiveType).isNotNull()
    assertThat(primitiveType!!.psiPrimitive).isEqualTo(PsiPrimitiveType.INT)

    myFixture.moveCaret("by|te")

    primitiveType = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType(ProguardR8JavaPrimitive::class)

    assertThat(primitiveType).isNotNull()
    assertThat(primitiveType!!.psiPrimitive).isEqualTo(PsiPrimitiveType.BYTE)
  }

  fun testMatchesPsiType() {
    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClass {
        boo${caret}lean myPrimitive;
        java.lang.String myString;
        % myAnyPrimitive;
        *** myAnyType;
      }
      """.trimIndent())

    var type = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType(ProguardR8Type::class)!!
    assertThat(type.matchesPsiType(PsiPrimitiveType.BOOLEAN)).isTrue()

    myFixture.moveCaret("Str|ing")
    type = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType(ProguardR8Type::class)!!
    val stringType = JavaPsiFacade.getElementFactory(project).createTypeFromText("java.lang.String", null)
    assertThat(type.matchesPsiType(stringType)).isTrue()

    myFixture.moveCaret("%|")
    type = myFixture.file.findElementAt(myFixture.caretOffset - 1)!!.parentOfType(ProguardR8Type::class)!!
    // String is NOT primitive
    assertThat(type.matchesPsiType(stringType)).isFalse()
    assertThat(type.matchesPsiType(PsiPrimitiveType.LONG)).isTrue()
    assertThat(type.matchesPsiType(PsiPrimitiveType.VOID)).isFalse()

    myFixture.moveCaret("*|**")
    type = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType(ProguardR8Type::class)!!
    assertThat(type.matchesPsiType(stringType)).isTrue()
    assertThat(type.matchesPsiType(PsiPrimitiveType.LONG)).isTrue()
  }
}
