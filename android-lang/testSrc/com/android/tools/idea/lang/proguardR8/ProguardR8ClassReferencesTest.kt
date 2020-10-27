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

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE, """
        -keep class test.MyC${caret}lass {
        }
    """.trimIndent()
    )

    myFixture.moveCaret("My|Class")

    assertThat(myFixture.elementAtCaret).isEqualTo(myFixture.findClass("test.MyClass"))
  }

  fun testFindUsagesOfNonPublicClass() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      class MyClass {}
    """.trimIndent()
    )

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE, """
        -keep class test.MyC${caret}lass {
        }
    """.trimIndent()
    )

    val presentation = myFixture.getUsageViewTreeTextRepresentation(myFixture.elementAtCaret)
    assertThat(presentation).contains(
      """
        Found usages (1)
          Referenced in Shrinker Config files (1)
      """.trimIndent()
    )
  }

  fun testResolveToPackage() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      public class MyClass {}
    """.trimIndent()
    )

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE, """
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
    )

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE, """
        -keep class test.MyClass${"$"}Inner${caret}Class {
        }
    """.trimIndent()
    )

    assertThat(myFixture.elementAtCaret).isEqualTo(myFixture.findClass("test.MyClass.InnerClass"))
  }

  fun testResolveToClassWithDollarSymbol() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      public class MyClass${'$'}Name { }
    """.trimIndent()
    )
    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
        -keep class test.MyClass${"$"}Nam${caret}e {
        }
    """.trimIndent()
    )

    assertThat(myFixture.elementAtCaret).isEqualTo(myFixture.findClass("test.MyClass${'$'}Name"))
  }

  fun testRenameClassWithCurrencySymbol() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      public class My${'$'}Class {}
    """.trimIndent()
    )

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
        -keep class test.My${'$'}Cla<caret>ss {
        }
      """.trimIndent()
    )

    val element = myFixture.elementAtCaret
    assertThat(element).isEqualTo(myFixture.findClass("test.My${'$'}Class"))

    myFixture.renameElement(element, "MyClassNewName", true, true)

    assertThat(myFixture.findClass("test.MyClassNewName")).isNotNull()

    myFixture.checkResult(
      """
        -keep class test.MyClassNewName {
        }
      """.trimIndent()
    )
    assertThat(myFixture.elementAtCaret).isEqualTo(myFixture.findClass("test.MyClassNewName"))
  }

  fun testCompletionForInnerClass() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      public class MyClass {
        // References provide completion only for static inner classes.
        static class StaticInnerClass {}
      }
    """.trimIndent()
    )

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE, """
        -keep class test.MyClass${"$"}${caret} {
        }
    """.trimIndent()
    )

    val classes = myFixture.completeBasic()
    assertThat(classes).isNotEmpty()
    assertThat(classes.map { it.lookupString }).containsExactly("StaticInnerClass")

    // Don't provide code completion for inner class after '.'.
    myFixture.configureByText(
      ProguardR8FileType.INSTANCE, """
        -keep class test.MyClass.${caret} {
        }
    """.trimIndent()
    )

    assertThat(myFixture.completeBasic()).isEmpty()
  }

  fun testCompletionForClass() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      public class MyClass {}
    """.trimIndent()
    )

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE, """
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
    )

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE, """
        -keep class p1.p2.${caret} {
        }
    """.trimIndent()
    )

    val classes = myFixture.completeBasic()
    assertThat(classes).isNotEmpty()
    assertThat(classes.map { it.lookupString }).containsExactly("test")
  }

  fun testJavaClassNameRenaming() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      public class MyClass {}
    """.trimIndent()
    )

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE, """
        -keep class test.My${caret}Class {
        }
    """.trimIndent()
    )
    myFixture.renameElementAtCaret("MyClassNewName")

    assertThat(myFixture.referenceAtCaret.element.text).isEqualTo("test.MyClassNewName")
    assertThat(myFixture.findClass("test.MyClassNewName")).isNotNull()

    myFixture.checkResult(
      """
      -keep class test.MyClassNewName {
      }
    """.trimIndent()
    )
  }

  fun testKotlinClassNameRenaming() {
    myFixture.configureByText(
      "MyClass.kt",
      //language=JAVA
      """
      package test;

      class MyClass {}
    """.trimIndent()
    )

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE, """
        -keep class test.My${caret}Class {
        }
    """.trimIndent()
    )

    assertThat(myFixture.elementAtCaret).isEqualTo(myFixture.findClass("test.MyClass"))

    myFixture.renameElementAtCaret("MyClassNewName")

    myFixture.checkResult(
      """
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
    )

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE, """
        -keep class p1.p2.myPackage.MyClass {
        }
    """.trimIndent()
    )

    myFixture.moveCaret("myPack|age")

    assertThat(myFixture.elementAtCaret).isEqualTo(myFixture.findPackage("p1.p2.myPackage"))

    myFixture.renameElementAtCaret("myPackageNewName")

    myFixture.checkResult(
      """
      -keep class p1.p2.myPackageNewName.MyClass {
      }
    """.trimIndent()
    )

    myFixture.moveCaret("myPackageNew|Name")

    assertThat(myFixture.elementAtCaret).isEqualTo(myFixture.findPackage("p1.p2.myPackageNewName"))

  }

  fun testCompletionInsideClassSpecificationBodyAfterPackageName() {
    myFixture.addClass(
      //language=JAVA
      """
      package p1.myPackage1;

      public class MyClass {}
    """.trimIndent()
    )

    myFixture.addClass(
      //language=JAVA
      """
      package p1.myPackage2;

      public class MyClass2 {}
    """.trimIndent()
    )

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE, """
        -keep class p1.myPackage1.MyClass {
          p1.$caret
    """.trimIndent()
    )

    val classes = myFixture.completeBasic()
    assertThat(classes).isNotEmpty()
    assertThat(classes.map { it.lookupString }).containsAllOf("myPackage1", "myPackage2")
  }
}