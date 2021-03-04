/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.lang

import com.android.tools.idea.testing.loadNewFile
import com.android.tools.idea.testing.moveCaret
import com.intellij.testFramework.RunsInEdt
import org.junit.Test

/**
 * Test for [IntDefCompletionContributorJava].
 */
class IntDefCompletionContributorJavaTest : IntDefCompletionContributorTest() {

  @RunsInEdt
  @Test
  fun testJavaClass() {
    myFixture.loadNewFile(
      "/src/test/MyClass.java",
      //language=JAVA
      """
        package test;

        class MyClass {
          public static void myFunction() {
            new MyClassWithModeParameter(<caret>
          }
        }
      """.trimIndent()
    )

    checkTopCompletionElements()
  }

  @RunsInEdt
  @Test
  fun testJavaAnnotation() {
    myFixture.loadNewFile(
      "/src/test/MyClass.java",
      //language=JAVA
      """
        package test;

        import com.library.MyJavaClassLibrary;

        class MyClass {
          @PreviewJava(<caret>U)
          public static void myFunction() { }

          @MyJavaClassLibrary.Preview()
          public static void myFunction2() { }
        }
      """.trimIndent()
    )

    checkTopCompletionElements()

    myFixture.moveCaret("@MyJavaClassLibrary.Preview(|")
    checkTopCompletionElements()
  }

  // Test for b/181776602.
  @RunsInEdt
  @Test
  fun testJavaAnnotation_notExistingAttribute() {
    myFixture.loadNewFile(
      "/src/test/MyClass.java",
      //language=JAVA
      """
        package test;

        import com.library.MyJavaClassLibrary;

        class MyClass {
          @PreviewJava(wrongAttr = <caret>)
          public static void myFunction() { }
        }
      """.trimIndent()
    )

    // Check that it doesn't throw exception.
    myFixture.completeBasic()
  }

  @RunsInEdt
  @Test
  fun testKotlinAnnotation() {

    myFixture.loadNewFile(
      "/src/test/MyClass.java",
      //language=JAVA
      """
        package test;

        import com.library.PreviewLibrary;

        class MyClass {
          @Preview(uiMode = <caret>)
          public static void myFunction() { }

          @PreviewLibrary(uiMode = )
          public static void myFunction2() { }
        }
      """.trimIndent()
    )

    checkTopCompletionElements()

    myFixture.moveCaret("@PreviewLibrary(uiMode = |")
    checkTopCompletionElements()
  }

  @RunsInEdt
  @Test
  fun testKotlinClass() {
    myFixture.loadNewFile(
      "/src/test/MyClass.java",
      //language=JAVA
      """
        package test;

        class MyClass {
          public static void myFunction() {
            new KtClassWithModeParameter(<caret>
          }
        }
      """.trimIndent()
    )

    checkTopCompletionElements()
  }

  @RunsInEdt
  @Test
  fun testAnnotation_javaFunctionDefinition_kotlin() {
    myFixture.loadNewFile(
      "/src/test/MyClass.java",
      //language=JAVA
      """
        package test;

        class MyClass {
          public static void myFunction() {
            SetModeKt.setMode(<caret>
          }
        }
      """.trimIndent()
    )

    checkTopCompletionElements()
  }

  @RunsInEdt
  @Test
  fun testAnnotation_javaFunctionDefinition_java() {
    myFixture.loadNewFile(
      "/src/test/MyClass.java",
      //language=JAVA
      """
        package test;

        class MyClass {
          public static void myFunction() {
            MyJavaClass.setMode(<caret>
          }
        }
      """.trimIndent()
    )

    checkTopCompletionElements()
  }

  @RunsInEdt
  @Test
  fun testAnnotation_kotlinFunctionDefinition_kotlin() {
    myFixture.loadNewFile(
      "/src/test/MyClass.java",
      //language=JAVA
      """
        package test;

        class MyClass {
          public static void myFunction() {
            SetModeKt.setModeKotlin(<caret>
          }
        }

      """.trimIndent()
    )
    checkTopCompletionElements()
  }

  @RunsInEdt
  @Test
  fun testAnnotation_kotlinFunctionDefinition_java() {
    myFixture.loadNewFile(
      "/src/test/MyClass.java",
      //language=JAVA
      """
        package test;

        class MyClass {
          public static void myFunction() {
            MyJavaClass.setModeKotlin(<caret>
          }
        }
      """.trimIndent()
    )

    checkTopCompletionElements()
  }
}