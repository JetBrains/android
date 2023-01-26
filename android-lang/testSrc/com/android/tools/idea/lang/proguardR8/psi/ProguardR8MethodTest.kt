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
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.PsiTypes
import com.intellij.psi.util.parentOfType
import com.intellij.util.IncorrectOperationException
import org.junit.Assert

class ProguardR8MethodTest : ProguardR8TestCase() {

  private fun getMethodsAtCaret(): List<PsiMethod> {
    val proguardMethod = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<ProguardR8ClassMemberName>()
    assertThat(proguardMethod).isNotNull()
    return (proguardMethod!!.reference as PsiPolyVariantReference).multiResolve(false).map { it.element as PsiMethod }
  }

  fun testMatchesToPsiType() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      class MyClass {
        int myInt();
        String myString();
      }
    """.trimIndent())

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClass {
        int myIn${caret}t();
        java.lang.String myString();
      }
      """.trimIndent())

    var method = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<ProguardR8ClassMember>()!!
    assertThat(method.type).isNotNull()
    assertThat(method.type!!.matchesPsiType(PsiTypes.intType())).isTrue()

    myFixture.moveCaret("my|String")
    method = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<ProguardR8ClassMember>()!!
    assertThat(method.type).isNotNull()

    val realType = myFixture.findClass("test.MyClass").findMethodsByName("myString", false).first().returnType!!
    assertThat(method.type!!.matchesPsiType(realType)).isTrue()
  }

  fun testMatchesToPsiParametersList() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      class MyClass {
        void method1(int p1, String p2);
        void method2(long p1, long p2);
      }
    """.trimIndent())

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClass {
        void method${caret}1(int, java.lang.String);
        void method2(long, long);
      }
      """.trimIndent())

    val psiClass = myFixture.findClass("test.MyClass")
    val parameterList1 = psiClass.findMethodsByName("method1", false).first().parameterList
    val parameterList2 = psiClass.findMethodsByName("method2", false).first().parameterList


    val method1 = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<ProguardR8ClassMember>()!!
    assertThat(method1.parameters!!.matchesPsiParameterList(parameterList1)).isTrue()
    assertThat(method1.parameters!!.matchesPsiParameterList(parameterList2)).isFalse()

    myFixture.moveCaret("m|ethod2")
    val method2 = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<ProguardR8ClassMember>()!!
    assertThat(method2.parameters!!.matchesPsiParameterList(parameterList2)).isTrue()
    assertThat(method2.parameters!!.matchesPsiParameterList(parameterList1)).isFalse()
  }

  fun testMethodReferenceCorrectPrimitiveType() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      class MyClass {
        int myMethod();
        int myMethod(int p1);
      }
    """.trimIndent())

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClass {
        int myMeth${caret}od(int);
      }
      """.trimIndent())

    val method = myFixture.elementAtCaret
    assertThat(method).isNotNull()

    val psiMethod = myFixture.findClass("test.MyClass")
      .findMethodsByName("myMethod", false)
      .firstOrNull { it.parameterList.parametersCount == 1 }

    assertThat(method).isEqualTo(psiMethod)
  }

  fun testMethodReferenceAnyPrimitiveType() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      class MyClass {
        int myPrimitive(long p1);
        int myPrimitive(int p1);
      }
    """.trimIndent())

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClass {
        % myPrimiti${caret}ve(%);
      }
      """.trimIndent())

    val methods = getMethodsAtCaret()
    assertThat(methods.size).isEqualTo(2)

    val psiMethods = myFixture.findClass("test.MyClass").findMethodsByName("myPrimitive", false)
    assertThat(methods).containsExactlyElementsIn(psiMethods)
  }

  fun testMethodReferenceAnyType() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      class MyClass {
        String myMethod();
        StringBuilder myMethod(int p1);
        String myMethod(int p1, String p2);
      }
    """.trimIndent())

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClass {
        *** myMeth${caret}od(***);
      }
      """.trimIndent())

    val methods = getMethodsAtCaret()
    assertThat(methods.size).isEqualTo(1)

    val psiMethods = myFixture.findClass("test.MyClass")
      .findMethodsByName("myMethod", false)
      .filter { it.parameters.size == 1 }

    assertThat(methods).containsExactlyElementsIn(psiMethods)
  }

  fun testMethodReferenceAnyNumAndTypeArgs() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      class MyClass {
        String myMethod();
        String myMethod(int p1);
        String myMethod(int p1, String p2);
      }
    """.trimIndent())

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClass {
        *** myMeth${caret}od(...);
      }
      """.trimIndent())

    val methods = getMethodsAtCaret()
    assertThat(methods.size).isEqualTo(3)

    val psiMethods = myFixture.findClass("test.MyClass").findMethodsByName("myMethod", false)
    assertThat(methods).containsExactlyElementsIn(psiMethods)
  }

  fun testMethodReferenceCorrectType() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      class MyClass {
        String myMethod();
      }
    """.trimIndent())

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClass {
        java.lang.String myMeth${caret}od();
      }
      """.trimIndent())

    val method = myFixture.elementAtCaret

    assertThat(method).isNotNull()
    assertThat(method).isEqualTo(myFixture.findClass("test.MyClass").findMethodsByName("myMethod", false).first())
  }

  fun testMethodReferenceIncorrectType() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      class MyClass {
        StringBuilder myMethod();
      }
    """.trimIndent())

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClass {
        java.lang.String myMeth${caret}od();
      }
      """.trimIndent())

    assertThat(getMethodsAtCaret()).isEmpty()
  }

  fun testMethodReferenceIncorrectPrimitiveType() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      class MyClass {
        long myMethod();
      }
    """.trimIndent())

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClass {
        int myMeth${caret}od();
      }
      """.trimIndent())

    assertThat(getMethodsAtCaret()).isEmpty()
  }

  fun testSuggestMethodsPrimitiveType() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      class MyClass {
        boolean myBooleanMethod();
        boolean myBooleanMethod2();
        int myNotBooleanMethod();
      }
    """.trimIndent())

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClass {
        boolean ${caret}();
      }
      """.trimIndent())

    val method = myFixture.completeBasic()

    assertThat(method).isNotEmpty()
    assertThat(method.map { it.lookupString }).containsExactly("myBooleanMethod", "myBooleanMethod2")
  }

  fun testSuggestMethodsAnyPrimitiveType() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      class MyClass {
        boolean myPrimitive();
        byte myPrimitive2();
        int myPrimitive3();
        String myNotPrimitive();
      }
    """.trimIndent())

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClass {
        % ${caret}();
      }
      """.trimIndent())

    val method = myFixture.completeBasic()

    assertThat(method).isNotEmpty()
    assertThat(method.map { it.lookupString }).containsExactly("myPrimitive", "myPrimitive2", "myPrimitive3")
  }

  fun testSuggestMethodsAnyType() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      class MyClass {
        boolean myPrimitive();
        byte myPrimitive2();
        int myPrimitive3();
        String myNotPrimitive();
      }
    """.trimIndent())

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClass {
        *** ${caret}();
      }
      """.trimIndent())

    val method = myFixture.completeBasic()

    assertThat(method).isNotEmpty()
    assertThat(method.map { it.lookupString }).containsExactly("myPrimitive", "myPrimitive2", "myPrimitive3", "myNotPrimitive")
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
        String myNotPrimitive();
        long myPrimitive();
      }
    """.trimIndent())

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClass {
        % ${caret}();
      }
      """.trimIndent())

    val method = myFixture.completeBasic()

    assertThat(method.size).isEqualTo(1)
  }

  fun testRenameMethod() {
    myFixture.addClass(
      //language=JAVA
      """
      //package test;

      class MyClass {
        int myMethod();
      }
    """.trimIndent())

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class MyClass {
        int myMeth${caret}od();
      }
      """.trimIndent())

    myFixture.renameElementAtCaret("myMethodNew")

    val newMethod = myFixture.findClass("MyClass").findMethodsByName("myMethodNew", false).first()
    assertThat(newMethod).isNotNull()

    myFixture.checkResult("""
      -keep class MyClass {
        int myMethodNew();
      }
    """.trimIndent())
  }

  fun testResolveToAllOverloads() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      class MyClass {
        boolean[] myMethod();
        byte myMethod();
        String myMethod();
        MyClass myMethod();
        boolean[] myMethod(int p1);
        byte myMethod(String[] p1);
        String myMethod(StringBuilder p1);
        MyClass myMethod();
      }
    """.trimIndent())

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClass {
        *** my${caret}Method(...);
      }
      """.trimIndent())

    val methods = getMethodsAtCaret()

    assertThat(methods).isNotEmpty()
    assertThat(methods).containsExactlyElementsIn(myFixture.findClass("test.MyClass").methods)
  }

  fun testRenameMethodNotValid() {
    myFixture.addFileToProject("MyClass.kt",
      //language=Kotlin
                               """
      class MyClass {
        fun myMethod():Int {};
      }
    """.trimIndent())

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class MyClass {
        int myMeth${caret}od();
      }
      """.trimIndent())

    try {
      myFixture.renameElementAtCaret("myMethod-New")
      Assert.fail("Expected to throw an IncorrectOperationException for invalid name")
    }
    catch (e: RuntimeException) {
      assertThat(e.cause).isInstanceOf(IncorrectOperationException::class.java)
      assertEquals("\"myMethod-New\" is not an identifier for Shrinker Config.", e.cause?.message)
    }

    val method = myFixture.findClass("MyClass").findMethodsByName("myMethod", false).first()
    assertThat(method).isNotNull()

    myFixture.checkResult("""
      -keep class MyClass {
        int myMethod();
      }
    """.trimIndent())
  }

  fun testInspectUnresolvedMethod() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      class MyClass {
        boolean myBoolean();
      }
    """.trimIndent())

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClass {
        long ${"myBoolean" highlightedAs ERROR};
      }
      """.trimIndent())

    myFixture.checkHighlighting()

    // don't highlight if class is unknown
    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.My* {
        long myBoolean();
      }
      """.trimIndent())

    myFixture.checkHighlighting()

    // don't highlight if method is with wildcards
    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClass {
        long m*();
      }
      """.trimIndent())

    myFixture.checkHighlighting()
  }

  fun testCodeCompletionForIncompleteMethod() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      class MyClass {
        public boolean[] myMethod();
      }
    """.trimIndent())

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClass {
        public boolean[] $caret
      }
      """.trimIndent()
    )
    val methods = myFixture.completeBasic()

    // suggest even without parenthesises
    assertThat(methods.map { it.lookupString }).contains("myMethod")
  }

  fun testCodeCompletionForMethodWithoutType() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      class MyClass {
        boolean[] myMethod1();
        int myMethod2();
      }
    """.trimIndent())

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClass {
         $caret
      }
      """.trimIndent()
    )
    val methods = myFixture.completeBasic()

    // suggest methods with different return types
    assertThat(methods.map { it.lookupString }).containsAllOf("myMethod1", "myMethod2")
  }

  fun testResolveMethodWithoutType() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      class MyClass {
        boolean[] myMethod();
        int myMethod();
      }
    """.trimIndent())

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClass {
         my${caret}Method();
      }
      """.trimIndent()
    )
    val methods = (myFixture.referenceAtCaret as PsiPolyVariantReference).multiResolve(false).toList()

    // resolve methods with different return types
    assertThat(methods).hasSize(2)
    assertThat(methods.map { it.element!!.text }).containsExactly("boolean[] myMethod();", "int myMethod();")
  }


  fun testInsertMethodWithParentheses() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      class MyClass {
        int myMethod();
      }
    """.trimIndent())

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClass {
         my${caret};
      }
      """.trimIndent()
    )
    myFixture.completeBasic()

    myFixture.checkResult(
      """
      -keep class test.MyClass {
         myMethod();
      }
      """.trimIndent()
    )
  }

  // Bug:153616200 , case 3.
  fun testResolveMethodKotlinIntrinsics() {
    myFixture.addClass(
      //language=JAVA
      """
      package kotlin.jvm.internal;

      class Intrinsics {
        private static void throwParameterIsNullException(String paramName) { }
      }
    """.trimIndent())

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      //language=SHRINKER_CONFIG
      """
      -assumenosideeffects class kotlin.jvm.internal.Intrinsics {
          private static void throw<caret>ParameterIsNullException(...);
      }
      """.trimIndent()
    )

    val method = myFixture
      .findClass("kotlin.jvm.internal.Intrinsics")
      .findMethodsByName("throwParameterIsNullException").first()
    val methodFromReference = myFixture.elementAtCaret

    assertThat(methodFromReference).isEqualTo(method)
  }
}
