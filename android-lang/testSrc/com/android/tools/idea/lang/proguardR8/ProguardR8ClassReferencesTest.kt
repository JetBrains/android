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
package com.android.tools.idea.lang.proguardR8

import com.android.tools.idea.lang.androidSql.referenceAtCaret
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat

class ProguardR8ClassReferencesTest : ProguardR8TestCase() {

  fun testResolveToClassName() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      public class MyClass {}
    """.trimIndent()
    )

    myFixture.configureByText(ProguardR8FileType.INSTANCE, """
        -keep class test.MyC${caret}lass {
        }
    """.trimIndent()
    )

    myFixture.moveCaret("My|Class")

    assertThat(myFixture.elementAtCaret).isEqualTo(myFixture.findClass("test.MyClass"))
  }

  fun testResolveToPackage() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      public class MyClass {}
    """.trimIndent()
    ).qualifiedName

    myFixture.configureByText(ProguardR8FileType.INSTANCE, """
        -keep class te${caret}st.MyClass {
        }
    """.trimIndent()
    )


    assertThat(myFixture.elementAtCaret).isEqualTo(myFixture.findPackage("test"))
  }


  fun testResolveToInnerClass() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      public class MyClass {
        class InnerClass {}
      }
    """.trimIndent()
    ).qualifiedName

    myFixture.configureByText(ProguardR8FileType.INSTANCE, """
        -keep class test.MyClass${"$"}Inner${caret}Class {
        }
    """.trimIndent()
    )


    assertThat(myFixture.elementAtCaret).isEqualTo(myFixture.findClass("test.MyClass.InnerClass"))
  }


  fun testCompletionForInnerClass() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      public class MyClass {
        class InnerClass {}
      }
    """.trimIndent()
    ).qualifiedName

    myFixture.configureByText(ProguardR8FileType.INSTANCE, """
        -keep class test.MyClass${"$"}${caret} {
        }
    """.trimIndent()
    )


    val classes = myFixture.completeBasic()
    assertThat(classes).isNotEmpty()
    assertThat(classes.map { it.lookupString }).containsExactly("InnerClass")
  }

  fun testCompletionForClass() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      public class MyClass {}
    """.trimIndent()
    ).qualifiedName

    myFixture.configureByText(ProguardR8FileType.INSTANCE, """
        -keep class test.${caret} {
        }
    """.trimIndent()
    )


    val classes = myFixture.completeBasic()
    assertThat(classes).isNotEmpty()
    assertThat(classes.map { it.lookupString }).containsExactly("MyClass")
  }


  fun testCompletionForPackages() {
    myFixture.addClass(
      //language=JAVA
      """
      package p1.p2.test;

      public class MyClass {}
    """.trimIndent()
    ).qualifiedName

    myFixture.configureByText(ProguardR8FileType.INSTANCE, """
        -keep class p1.p2.${caret} {
        }
    """.trimIndent()
    )


    val classes = myFixture.completeBasic()
    assertThat(classes).isNotEmpty()
    assertThat(classes.map { it.lookupString }).containsExactly("test")
  }

  fun testJavaClassNameRenaming() {
    val className = myFixture.addClass(
      //language=JAVA
      """
      package test;

      public class MyClass {}
    """.trimIndent()
    ).qualifiedName

    myFixture.configureByText(ProguardR8FileType.INSTANCE, """
        -keep class ${className} {
        }
    """.trimIndent()
    )

    myFixture.moveCaret("My|Class")

    assertThat(myFixture.elementAtCaret).isEqualTo(myFixture.findClass(className!!))

    myFixture.renameElementAtCaret("MyClassNewName")

    assertThat(myFixture.referenceAtCaret.element.text).isEqualTo("test.MyClassNewName")
  }

  fun testKotlinClassNameRenaming() {
    myFixture.configureByText("MyClass.kt",
      //language=JAVA
                              """
      package test;

      class MyClass {}
    """.trimIndent()
    )

    myFixture.configureByText(ProguardR8FileType.INSTANCE, """
        -keep class test.My${caret}Class {
        }
    """.trimIndent()
    )

    assertThat(myFixture.elementAtCaret).isEqualTo(myFixture.findClass("test.MyClass"))

    myFixture.renameElementAtCaret("MyClassNewName")

    myFixture.checkResult("""
      -keep class test.MyClassNewName {
      }
    """.trimIndent()
    )
  }

  fun testPackageRenaming() {
    myFixture.addClass(
      //language=JAVA
      """
      package p1.p2.myPackage;

      public class MyClass {}
    """.trimIndent()
    ).qualifiedName

    myFixture.configureByText(ProguardR8FileType.INSTANCE, """
        -keep class p1.p2.myPackage.MyClass {
        }
    """.trimIndent()
    )

    myFixture.moveCaret("myPack|age")

    assertThat(myFixture.elementAtCaret).isEqualTo(myFixture.findPackage("p1.p2.myPackage"))

    myFixture.renameElementAtCaret("myPackageNewName")

    myFixture.checkResult("""
      -keep class p1.p2.myPackageNewName.MyClass {
      }
    """.trimIndent()
    )

    myFixture.moveCaret("myPackageNew|Name")

    assertThat(myFixture.elementAtCaret).isEqualTo(myFixture.findPackage("p1.p2.myPackageNewName"))

  }
}