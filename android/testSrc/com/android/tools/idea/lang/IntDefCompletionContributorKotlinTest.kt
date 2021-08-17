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
 * Test for [IntDefCompletionContributorKotlin]
 */
class IntDefCompletionContributorKotlinTest : IntDefCompletionContributorTest() {

  @RunsInEdt
  @Test
  fun testAnnotation_kotlinFunctionDefinition_kotlin() {
    myFixture.loadNewFile("/src/test/MyFile.kt",
      //language=kotlin
                          """
        package test

        fun myFunction() {
          setModeKotlin(<caret>
        }
      """.trimIndent()
    )

    checkTopCompletionElements()

    myFixture.type("mode = ")
    myFixture.moveCaret("mode = |")

    checkTopCompletionElements()
  }

  @RunsInEdt
  @Test
  fun testJavaClass() {
    myFixture.loadNewFile(
      "/src/test/MyFile.kt",
      //language=kotlin
      """
        package test

        fun myFunction(){
          MyClassWithModeParameter(<caret>
        }
      """.trimIndent()
    )

    checkTopCompletionElements()
  }

  @RunsInEdt
  @Test
  fun testJavaAnnotation() {
    myFixture.loadNewFile(
      "/src/test/MyFile.kt",
      //language=kotlin
      """
        package test

        import com.library.MyJavaClassLibrary

        @PreviewJava(<caret>)
        fun myFunction(){}

        @MyJavaClassLibrary.Preview()
        fun myFunction2(){}
      """.trimIndent()
    )
    checkTopCompletionElements()

    myFixture.moveCaret("@MyJavaClassLibrary.Preview(|")
    checkTopCompletionElements()
  }

  @RunsInEdt
  @Test
  fun testKotlinAnnotation() {
    myFixture.loadNewFile(
      "/src/test/MyFile.kt",
      //language=kotlin
      """
        package test

        import com.library.PreviewLibrary

        @Preview(uiMode = <caret>)
        fun myFunction(){}

        @PreviewLibrary(uiMode = )
        fun myFunction2(){}
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
      "/src/test/MyFile.kt",
      //language=kotlin
      """
        package test

        fun myFunction(){
          KtClassWithModeParameter(uiMode = <caret>)
        }
      """.trimIndent()
    )

    checkTopCompletionElements()
  }

  @RunsInEdt
  @Test
  fun testAnnotation_javaFunctionDefinition_kotlin() {
    myFixture.loadNewFile("/src/test/MyFile.kt",
      //language=kotlin
                          """
        package test

        fun myFunction() {
          setMode(<caret>
        }
      """.trimIndent()

    )

    checkTopCompletionElements()
  }

  @RunsInEdt
  @Test
  fun testAnnotation_javaFunctionDefinition_java() {
    myFixture.loadNewFile("/src/test/MyFile.kt",
      //language=kotlin
                          """
        package test

        fun myFunction() {
          MyJavaClass.setMode(<caret>
        }
      """.trimIndent()

    )
    checkTopCompletionElements()
  }

  @RunsInEdt
  @Test
  fun testAnnotation_kotlinFunctionDefinition_java() {
    myFixture.loadNewFile("/src/test/MyFile.kt",
      //language=kotlin
                          """
        package test

        fun myFunction() {
          MyJavaClass.setModeKotlin(<caret>
        }
      """.trimIndent()

    )
    checkTopCompletionElements()
  }
}