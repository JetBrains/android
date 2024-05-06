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

import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.CodeShrinker
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.highlightedAs
import com.intellij.lang.annotation.HighlightSeverity.ERROR
import com.intellij.lang.annotation.HighlightSeverity.WARNING
import org.jetbrains.android.AndroidTestCase

class ProguardR8InspectionsTest : ProguardR8TestCase() {
  fun testUnresolvedClassName() {
    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class ${"test.MyNotExistingClass".highlightedAs(ERROR, "Unresolved class name")} {
        long myBoolean;
      }
      """.trimIndent())

    myFixture.checkHighlighting()

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyNotExistingClass.WithWildCard** {
        long myBoolean;
      }
      """.trimIndent())

    myFixture.checkHighlighting()

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class java.lang.String {
        ${"test.MyNotExistingClass".highlightedAs(ERROR, "Unresolved class name")}
        ${"myVal".highlightedAs(ERROR, "The rule matches no class members")};
      }
      """.trimIndent())

    myFixture.checkHighlighting()
  }

  fun testUnresolvedClassNameInFlagRule() {
    // Regression test for b/301246673
    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -dontwarn test.MyNotExistingClass
      -dontnote test.MyNotExistingClass
      """.trimIndent())

    myFixture.checkHighlighting()
  }

  fun testWildcards() {
    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class ** {
        long myBoolean;
      }
      """.trimIndent())

    myFixture.checkHighlighting()

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class com.android.** { *; }
      """.trimIndent())

    myFixture.checkHighlighting()
  }

  fun testInnerClasses() {
    myFixture.addClass(
      //language=JAVA
      """
      package example;

      class MyClass {
        class Inner {}
      }
    """.trimIndent())

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class example.MyClass${'$'}Inner
      """
    )
    myFixture.checkHighlighting()
  }

  fun testInnerClassesSeparatorInspection() {
    myFixture.addClass(
      //language=JAVA
      """
      package example;

      class MyClass {
        class Inner {
          class SecondInner {}
        }
      }
    """.trimIndent())

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class example.MyClass.Inner
      """
    )
    var highlights = myFixture.doHighlighting(ERROR).map { it.description }

    highlights.contains("Inner classes should be separated by a dollar sign \"\$\"")

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class example.MyClass.Inner${'$'}SecondInner
      """
    )
    highlights = myFixture.doHighlighting(ERROR).map { it.description }

    highlights.contains("Inner classes should be separated by a dollar sign \"\$\"")

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class example.MyClass${'$'}Inner.SecondInner
      """
    )
    highlights = myFixture.doHighlighting(ERROR).map { it.description }

    highlights.contains("Inner classes should be separated by a dollar sign \"\$\"")
  }

  fun testInvalidFlag() {
    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
        ${"-invalidflag".highlightedAs(ERROR, "Invalid flag")}
      """.trimIndent()
    )

    myFixture.checkHighlighting()
  }

  fun testSpacesInArrayType() {
    myFixture.addClass("""
      package test
      class myClass {
        int[] method() {};
       }
    """.trimIndent())

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.myClass {
        ${"int []".highlightedAs(ERROR, "White space between type and array annotation is not allowed, use 'type[]'")} method();
        int${"[\n]".highlightedAs(ERROR, "White space is not allowed in array annotation, use 'type[]'")} method();
        int ${"[  ]".highlightedAs(ERROR, "White space is not allowed in array annotation, use 'type[]'")} method();
        int[] method();
      }
      """.trimIndent())

    myFixture.checkHighlighting()
  }

}

class ProguardR8IgnoredFlagInspectionTest : AndroidTestCase() {
  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(ProguardR8IgnoredFlagInspection::class.java)
  }

  fun testIgnoredFlag() {
    (myModule.getModuleSystem() as DefaultModuleSystem).codeShrinker = CodeShrinker.R8

    val flag = PROGUARD_FLAGS.minus(R8_FLAGS).first()
    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
        ${"-${flag}".highlightedAs(WARNING, "Flag ignored by R8")}
      """.trimIndent()
    )

    myFixture.checkHighlighting()

    (myModule.getModuleSystem() as DefaultModuleSystem).codeShrinker = CodeShrinker.PROGUARD

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
        -${flag}
      """.trimIndent()
    )

    myFixture.checkHighlighting()

    (myModule.getModuleSystem() as DefaultModuleSystem).codeShrinker = null

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
        -${flag}
      """.trimIndent()
    )

    myFixture.checkHighlighting()
  }
}