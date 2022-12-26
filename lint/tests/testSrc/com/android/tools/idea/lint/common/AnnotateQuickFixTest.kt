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
package com.android.tools.idea.lint.common

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase

class AnnotateQuickFixTest : JavaCodeInsightFixtureTestCase() {

  fun testKotlinAnnotationArgs() {
    val file = myFixture.configureByText(
      "src/p1/p2/AnnotateTest.kt",
      //language=kotlin
      """
        package p1.p2
        const val someProperty = ""
      """.trimIndent()
    )

    val ktProperty = myFixture.findElementByText("someProperty", PsiElement::class.java)
    val addAnnotationFix = AnnotateQuickFix("Add @Suppress(SomeInspection)", "Test", "@kotlin.Suppress(\"SomeInspection\")", true)
    val context = AndroidQuickfixContexts.EditorContext.getInstance(myFixture.editor, myFixture.file)

    assertTrue(addAnnotationFix.isApplicable(ktProperty, ktProperty, context.type))
    WriteCommandAction.runWriteCommandAction(project) {
      addAnnotationFix.apply(ktProperty, ktProperty, context)
    }

    assertEquals(
      """
      package p1.p2
      @Suppress("SomeInspection")
      const val someProperty = ""
      """.trimIndent(),
      file.text
    )
  }

  fun testKotlinUseSiteTarget() {
    val file = myFixture.configureByText(
      "src/p1/p2/AnnotateTest.kt",
      //language=kotlin
      """
        package p1.p2
        const val someProperty = ""
      """.trimIndent()
    )

    val ktProperty = myFixture.findElementByText("someProperty", PsiElement::class.java)
    val addAnnotationFix = AnnotateQuickFix("Add @get:JvmSynthetic", "Test", "@get:JvmSynthetic", true)
    val context = AndroidQuickfixContexts.EditorContext.getInstance(myFixture.editor, myFixture.file)

    assertTrue(addAnnotationFix.isApplicable(ktProperty, ktProperty, context.type))
    WriteCommandAction.runWriteCommandAction(project) {
      addAnnotationFix.apply(ktProperty, ktProperty, context)
    }

    assertEquals(
      """
      package p1.p2
      @get:JvmSynthetic
      const val someProperty = ""
      """.trimIndent(),
      file.text
    )
  }

  fun testKotlinMultipleAnnotations() {
    val file = myFixture.configureByText(
      "src/p1/p2/AnnotateTest.kt",
      //language=kotlin
      """
        package p1.p2
        const val someProperty = ""
      """.trimIndent()
    )

    val ktProperty = myFixture.findElementByText("someProperty", PsiElement::class.java)
    val addAnnotationFixes = listOf(
      AnnotateQuickFix("Add @Suppress(SomeInspection1)", "Test", "@kotlin.Suppress(\"SomeInspection1\")", false),
      AnnotateQuickFix("Add @Suppress(SomeInspection2)", "Test", "@kotlin.Suppress(\"SomeInspection2\")", false),
    )
    val context = AndroidQuickfixContexts.EditorContext.getInstance(myFixture.editor, myFixture.file)

    for (fix in addAnnotationFixes) {
      assertTrue(fix.isApplicable(ktProperty, ktProperty, context.type))
      WriteCommandAction.runWriteCommandAction(project) {
        fix.apply(ktProperty, ktProperty, context)
      }
    }

    assertEquals(
      """
      package p1.p2
      @Suppress("SomeInspection2")
      @Suppress("SomeInspection1")
      const val someProperty = ""
      """.trimIndent(),
      file.text
    )
  }

  fun testKotlinReplaceAnnotation() {
    val file = myFixture.configureByText(
      "src/p1/p2/AnnotateTest.kt",
      //language=kotlin
      """
        package p1.p2
        const val someProperty = ""
      """.trimIndent()
    )

    val ktProperty = myFixture.findElementByText("someProperty", PsiElement::class.java)
    val addAnnotationFixes = listOf(
      AnnotateQuickFix("Add @Suppress(SomeInspection1)", "Test", "@kotlin.Suppress(\"SomeInspection1\")", true),
      AnnotateQuickFix("Add @Suppress(SomeInspection2)", "Test", "@kotlin.Suppress(\"SomeInspection2\")", true),
    )
    val context = AndroidQuickfixContexts.EditorContext.getInstance(myFixture.editor, myFixture.file)

    for (fix in addAnnotationFixes) {
      assertTrue(fix.isApplicable(ktProperty, ktProperty, context.type))
      WriteCommandAction.runWriteCommandAction(project) {
        fix.apply(ktProperty, ktProperty, context)
      }
    }

    assertEquals(
      """
      package p1.p2
      @Suppress("SomeInspection2")
      const val someProperty = ""
      """.trimIndent(),
      file.text
    )
  }
}