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
package com.android.tools.idea.inspections


import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths.TEST_ARTIFACTS_KOTLIN
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.kotlin.android.inspection.IncorrectScopeInspection

class IncorrectScopeInspectionTest : AndroidGradleTestCase() {

  override fun setUp() {
    super.setUp()
    loadProject(TEST_ARTIFACTS_KOTLIN)
    StudioFlags.KOTLIN_INCORRECT_SCOPE_CHECK_IN_TESTS.override(true)
    myFixture.enableInspections(IncorrectScopeInspection::class.java)
  }

  override fun tearDown() {
    try {
      StudioFlags.KOTLIN_INCORRECT_SCOPE_CHECK_IN_TESTS.clearOverride()
    }
    finally {
      super.tearDown()
    }
  }


  fun testCorrectScopeAndroidTest() {
    val file = project.guessProjectDir()!!
      .findFileByRelativePath("app/src/androidTest/java/com/example/android/kotlin/ExampleInstrumentedTest.kt")!!
    myFixture.openFileInEditor(file)
    val highlightInfo = myFixture.doHighlighting(HighlightSeverity.WARNING)
    assertThat(highlightInfo).isEmpty()
  }

  fun testIncorrectScopeAndroidTest() {
    val unitTestPath = "app/src/androidTest/java/com/example/android/kotlin/ExampleInstrumentedTest.kt"
    val file = project.guessProjectDir()!!.findFileByRelativePath(unitTestPath)
    myFixture.openFileInEditor(file!!)
    myFixture.moveCaret("assertEquals(\"com.example.android.kotlin\", appContext.packageName)|")
    myFixture.type("\nExampleUnitTest()")

    val expectedErrorMessage = "Unresolved reference: ExampleUnitTest"
    assertThat(myFixture.doHighlighting().stream().anyMatch {
      it.description == expectedErrorMessage
      && it.severity == HighlightSeverity.ERROR
    }).isTrue()
  }

  fun testIncorrectScopeDifferentModuleAndroidTest() {
    val unitTestPath = "app/src/test/java/com/example/android/kotlin/ExampleUnitTest.kt"
    val file = project.guessProjectDir()!!.findFileByRelativePath(unitTestPath)
    myFixture.openFileInEditor(file!!)
    myFixture.moveCaret("import org.junit.Test|")
    myFixture.type("\nimport android.support.test.InstrumentationRegistry")
    PsiDocumentManager.getInstance(project).commitDocument(myFixture.editor.document)
    myFixture.moveCaret("assertEquals(4, 2 + 2)|")
    myFixture.type("\nInstrumentationRegistry.getTargetContext()")
    PsiDocumentManager.getInstance(project).commitDocument(myFixture.editor.document)
    val expectedErrorMessage = "Unresolved reference: InstrumentationRegistry"
    assertThat(myFixture.doHighlighting().stream().anyMatch {
      it.description == expectedErrorMessage && it.severity == HighlightSeverity.ERROR
    }).isTrue()
  }

  fun testCorrectScopeDifferentModuleAndroidTest() {
    val unitTestPath = "app/src/androidTest/java/com/example/android/kotlin/ExampleInstrumentedTest.kt"
    val file = project.guessProjectDir()!!.findFileByRelativePath(unitTestPath)
    myFixture.openFileInEditor(file!!)
    myFixture.moveCaret("\nimport android.support.test.InstrumentationRegistry|")
    // JUnit has been added to the androidTest scope as an indirect dependency on com.android.support.test:runner:1.0.2
    myFixture.moveCaret("\nimport org.junit.Test|")
    PsiDocumentManager.getInstance(project).commitDocument(myFixture.editor.document)
    val highlightInfo = myFixture.doHighlighting(HighlightSeverity.WARNING)
    assertThat(highlightInfo).isEmpty()
  }

  fun testCorrectScopeUnitTest() {
    val file = project.guessProjectDir()!!
      .findFileByRelativePath("app/src/test/java/com/example/android/kotlin/ExampleUnitTest.kt")!!
    myFixture.openFileInEditor(file)
    val highlightInfo = myFixture.doHighlighting(HighlightSeverity.WARNING)
    assertThat(highlightInfo).isEmpty()
  }

  fun testIncorrectScopeUnitTest() {
    val unitTestPath = "app/src/test/java/com/example/android/kotlin/ExampleUnitTest.kt"
    val file = project.guessProjectDir()!!.findFileByRelativePath(unitTestPath)
    myFixture.openFileInEditor(file!!)
    myFixture.moveCaret("assertEquals(4, 2 + 2)|")
    myFixture.type("\nExampleInstrumentedTest()")

    val expectedErrorMessage = "Unresolved reference: ExampleInstrumentedTest"
    assertThat(myFixture.doHighlighting().stream().anyMatch {
      it.description == expectedErrorMessage && it.severity == HighlightSeverity.ERROR
    }).isTrue()
  }

  // Regression test for b/132401150
  fun testCorrectScopeKotlinStandardLibrary() {
    val unitTestPath = "app/src/test/java/com/example/android/kotlin/ExampleUnitTest.kt"
    val file = project.guessProjectDir()!!.findFileByRelativePath(unitTestPath)
    myFixture.openFileInEditor(file!!)
    myFixture.moveCaret("assertEquals(4, 2 + 2)|")
    myFixture.type("\nlistOf<AbstractCollection<*>>()")

    val highlightInfo = myFixture.doHighlighting(HighlightSeverity.WARNING)
    assertThat(highlightInfo).isEmpty()
  }

  // Regression test for b/132401150. Kotlin primitive types will not be found in JavaPsiFacade, so they must be ignored.
  fun testCorrectScopeKotlinStandardLibraryPrimitiveType() {
    val unitTestPath = "app/src/test/java/com/example/android/kotlin/ExampleUnitTest.kt"
    val file = project.guessProjectDir()!!.findFileByRelativePath(unitTestPath)
    myFixture.openFileInEditor(file!!)
    myFixture.moveCaret("assertEquals(4, 2 + 2)|")
    myFixture.type("\nlistOf<Int>()")

    val highlightInfo = myFixture.doHighlighting(HighlightSeverity.WARNING)
    assertThat(highlightInfo).isEmpty()
  }

  fun testCorrectScopeKotlinBuiltIns() {
    val unitTestPath = "app/src/test/java/com/example/android/kotlin/ExampleUnitTest.kt"
    val file = project.guessProjectDir()!!.findFileByRelativePath(unitTestPath)
    myFixture.openFileInEditor(file!!)
    myFixture.moveCaret("assertEquals(4, 2 + 2)|")
    myFixture.type("""

      listOf<Any>()
      listOf<Nothing>()
      listOf<Function<LongArray>>()""".trimIndent())
    val highlightInfo = myFixture.doHighlighting(HighlightSeverity.WARNING)
    assertThat(highlightInfo).isEmpty()
  }
}