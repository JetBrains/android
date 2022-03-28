/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.moveCaret
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth
import com.intellij.openapi.project.guessProjectDir
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class ProGuardR8ClassReferencesIntegrationTest {

  @get:Rule
  val gradleProjectRule = AndroidGradleProjectRule().onEdt()

  private val fixture
    get() = gradleProjectRule.fixture as JavaCodeInsightTestFixture

  val project
    get() = gradleProjectRule.project

  @Before
  fun setUp() {
    gradleProjectRule.loadProject(TestProjectPaths.SIMPLE_APPLICATION)
  }

  @Test
  fun testResolveToClassName() {
    VfsTestUtil.createFile(
      project.guessProjectDir()!!,
      "app/src/main/java/test/MyClass.java",
      //language=JAVA
      """
      package test;

      public class MyClass {}
    """.trimIndent()
    )

    val file = VfsTestUtil.createFile(
      project.guessProjectDir()!!,
      "app/proguard.pro",
      """
        -keep class test.MyClass {
        }
    """.trimIndent()
    )

    fixture.openFileInEditor(file)

    fixture.moveCaret("My|Class")

    Truth.assertThat(fixture.elementAtCaret).isEqualTo(fixture.findClass("test.MyClass"))
  }

  @Test
  fun testResolveToClassNameFromLibrary() {

    val file = VfsTestUtil.createFile(
      project.guessProjectDir()!!,
      "app/proguard.pro",
      """
        -keep class com.google.common.util.concurrent.AtomicDoubleArray {
        }
    """.trimIndent()
    )

    fixture.openFileInEditor(file)

    fixture.moveCaret("|AtomicDoubleArray")

    Truth.assertThat(fixture.elementAtCaret).isEqualTo(fixture.findClass("com.google.common.util.concurrent.AtomicDoubleArray"))
  }

  @Test
  fun testFindUsagesOfNonPublicClass() {
    VfsTestUtil.createFile(
      project.guessProjectDir()!!, "app/src/main/java/test/MyClass.java",
      //language=JAVA
      """
      package test;

      class MyClass {}
    """.trimIndent()
    )

    val file = VfsTestUtil.createFile(
      project.guessProjectDir()!!,
      "app/proguard.pro",
      """
        -keep class test.MyClass {
        }
    """.trimIndent()
    )

    fixture.openFileInEditor(file)
    fixture.moveCaret("My|Class")
    
    val presentation = fixture.getUsageViewTreeTextRepresentation(fixture.elementAtCaret)
    Truth.assertThat(presentation).contains(
      """
        Usages in Project Files (1)
          Referenced in Shrinker Config files (1)
      """.trimIndent()
    )
  }

  @Test
  fun testResolveToPackage() {
    VfsTestUtil.createFile(
      project.guessProjectDir()!!, "app/src/main/java/test/MyClass.java",
      //language=JAVA
      """
      package test;

      class MyClass {}
    """.trimIndent()
    )

    val file = VfsTestUtil.createFile(
      project.guessProjectDir()!!, "app/proguard.pro",
      """
        -keep class test.MyC${caret}lass {
        }
    """.trimIndent()
    )

    fixture.openFileInEditor(file)
    fixture.moveCaret("tes|t")

    Truth.assertThat(fixture.elementAtCaret).isEqualTo(fixture.findPackage("test"))
  }

  @Test
  fun testResolveToInnerClass() {
    VfsTestUtil.createFile(
      project.guessProjectDir()!!, "app/src/main/java/test/MyClass.java",
      //language=JAVA
      """
      package test;

      public class MyClass {
        class InnerClass {}
      }
    """.trimIndent()
    )

    val file = VfsTestUtil.createFile(
      project.guessProjectDir()!!,
      "app/proguard.pro",
      """
        -keep class test.MyClass${"$"}InnerClass {
        }
    """.trimIndent()
    )

    fixture.openFileInEditor(file)
    fixture.moveCaret("InnerClas|s")

    Truth.assertThat(fixture.elementAtCaret).isEqualTo(fixture.findClass("test.MyClass.InnerClass"))
  }
}