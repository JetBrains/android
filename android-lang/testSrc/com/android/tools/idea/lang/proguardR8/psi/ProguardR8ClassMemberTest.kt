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
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference
import com.intellij.psi.util.parentOfType

class ProguardR8ClassMemberTest : ProguardR8TestCase() {

  fun testIsConstructor() {
    myFixture.addClass(
      //language=JAVA
      """
        package p1.p2;
        class MyClass {}
      """.trimIndent()
    )

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
        -keep class p1.p2.MyClass {
          MyClass();
          p1.p2.MyClass();
          NotMyClass();
          p1.p2.NotMyClass();
          p3.MyClass();
        }
      """.trimIndent()
    )

    myFixture.moveCaret("My|Class()")
    var member = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<ProguardR8ClassMember>()!!
    assertThat(member.isConstructor()).isTrue()

    myFixture.moveCaret("p1.p2.My|Class()")
    member = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<ProguardR8ClassMember>()!!
    assertThat(member.isConstructor()).isTrue()

    myFixture.moveCaret("NotMy|Class")
    member = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<ProguardR8ClassMember>()!!
    assertThat(member.isConstructor()).isFalse()

    myFixture.moveCaret("p1.p2.NotMy|Class")
    member = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<ProguardR8ClassMember>()!!
    assertThat(member.isConstructor()).isFalse()

    myFixture.moveCaret("p3.My|Class()")
    member = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<ProguardR8ClassMember>()!!
    assertThat(member.isConstructor()).isFalse()
  }

  fun testResolveConstructors() {
    fun getConstructorsDescriptionsAt(str: String): List<String> {
      myFixture.moveCaret(str)
      val classMemberReference: ProguardR8ClassMemberNameReference
      val referenceAtCaret = myFixture.referenceAtCaret
      if (referenceAtCaret is PsiMultiReference) {
        classMemberReference = referenceAtCaret.references.filterIsInstance(ProguardR8ClassMemberNameReference::class.java).first()
      } else {
        classMemberReference = myFixture.referenceAtCaret as ProguardR8ClassMemberNameReference
      }
      return classMemberReference.multiResolve(false).map { it.element.text }
    }

    myFixture.addClass(
      //language=JAVA
      """
        package p1.p2;
        class MyClass {
          MyClass() {}
          MyClass(int i) {}
          protected MyClass(long l) {}
          private MyClass(String s) {}
        }
      """.trimIndent()
    )

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
        -keep class p1.p2.MyClass {
          MyClass();
          MyClass(***);
          public MyClass(...);
          !public MyClass(...);
          private protected MyClass(...);
          MyClass(java.lang.String);

          p1.p2.MyClass();
          p1.p2.MyClass(int, int);
          protected p1.p2.MyClass(...);
        }
      """.trimIndent()
    )

    var constructors = getConstructorsDescriptionsAt("My|Class()")
    assertThat(constructors).containsExactly("MyClass() {}")

    constructors = getConstructorsDescriptionsAt("My|Class(***)")
    assertThat(constructors).containsExactly("MyClass(int i) {}", "protected MyClass(long l) {}", "private MyClass(String s) {}")

    constructors = getConstructorsDescriptionsAt("public My|Class(...)")
    assertThat(constructors).isEmpty()

    constructors = getConstructorsDescriptionsAt("!public My|Class(...)")
    assertThat(constructors).containsExactly(
      "MyClass() {}", "MyClass(int i) {}", "protected MyClass(long l) {}",
      "private MyClass(String s) {}"
    )

    constructors = getConstructorsDescriptionsAt("private protected My|Class(...)")
    assertThat(constructors).containsExactly("protected MyClass(long l) {}", "private MyClass(String s) {}")

    constructors = getConstructorsDescriptionsAt("My|Class(java.lang.String)")
    assertThat(constructors).containsExactly("private MyClass(String s) {}")

    constructors = getConstructorsDescriptionsAt("p1.p2.My|Class()")
    assertThat(constructors).containsExactly("MyClass() {}")

    constructors = getConstructorsDescriptionsAt("p1.p2.My|Class(int, int)")
    assertThat(constructors).isEmpty()

    constructors = getConstructorsDescriptionsAt("protected p1.p2.My|Class(...)")
    assertThat(constructors).containsExactly("protected MyClass(long l) {}")
  }

  fun testModifiers() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      class MyClass {
        int myPackagePrivate;
        public static int myPublicStatic = 1;
        private final int myPrivateFinal = 1;
        public volatile int myPublicVolatile;
        private strictfp int mySrictfpMethod();
      }
    """.trimIndent()
    )

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClass {
         my;
         public my;
         !public my;
         private final my;
         private !static my;
         strictfp my;
         !static !final !strictfp my;
         volatile !volatile my;
      }
      """.trimIndent()
    )
    myFixture.moveCaret("m|y;")
    var fields = (myFixture.referenceAtCaret as ProguardR8ClassMemberNameReference).variants.map { it.lookupString }
    assertThat(fields).containsExactly("myPackagePrivate", "myPublicStatic", "myPrivateFinal", "myPublicVolatile", "mySrictfpMethod")

    myFixture.moveCaret("public m|y;")
    fields = (myFixture.referenceAtCaret as ProguardR8ClassMemberNameReference).variants.map { it.lookupString }
    assertThat(fields).containsExactly("myPublicStatic", "myPublicVolatile")

    myFixture.moveCaret("!public m|y;")
    fields = (myFixture.referenceAtCaret as ProguardR8ClassMemberNameReference).variants.map { it.lookupString }
    assertThat(fields).containsExactly("myPackagePrivate", "myPrivateFinal", "mySrictfpMethod")

    myFixture.moveCaret("private final m|y;")
    fields = (myFixture.referenceAtCaret as ProguardR8ClassMemberNameReference).variants.map { it.lookupString }
    assertThat(fields).containsExactly("myPrivateFinal")

    myFixture.moveCaret("private !static m|y;")
    fields = (myFixture.referenceAtCaret as ProguardR8ClassMemberNameReference).variants.map { it.lookupString }
    assertThat(fields).containsExactly("myPrivateFinal", "mySrictfpMethod")

    myFixture.moveCaret("strictfp m|y;")
    fields = (myFixture.referenceAtCaret as PsiMultiReference).references
      .find { it is ProguardR8ClassMemberNameReference }!!.variants.map { (it as LookupElement).lookupString }
    assertThat(fields).containsExactly("mySrictfpMethod")


    myFixture.moveCaret("!static !final !strictfp m|y;")
    fields = (myFixture.referenceAtCaret as PsiMultiReference).references
      .find { it is ProguardR8ClassMemberNameReference }!!.variants.map { (it as LookupElement).lookupString }
    assertThat(fields).containsExactly("myPackagePrivate", "myPublicVolatile")

    myFixture.moveCaret("volatile !volatile m|y;")
    fields = (myFixture.referenceAtCaret as ProguardR8ClassMemberNameReference).variants.map { it.lookupString }
    assertThat(fields).isEmpty()
  }
}