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
package com.android.tools.idea.kotlin

import com.google.common.truth.Truth.assertThat
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.junit.Test

class AndroidKtPsiUtilsTest : LightJavaCodeInsightFixtureTestCase() {

  @Test
  fun testKtClass_insideBody() {
    val file = setFileContents("""
      class Foo {
        // <caret> body
      }
    """.trimIndent())

    val classReference = file.getElementAtCaret<KtClass>()
    assertThat(classReference.insideBody(myFixture.caretOffset)).isTrue()
    assertThat(classReference.insideBody(0)).isFalse()
  }

  @Test
  fun testKtProperty_hasBackingField() {
    fun PsiFile.findProperty(name: String) = find<KtProperty> { it.name == name }

    val file = setFileContents("""
      val propertyWithBackingField: String = "foo"
      val propertyWithoutBackingField: String get() = "bar"
      val delegatedProperty: String by lazy { "baz" }
    """.trimIndent())

    assertThat(file.findProperty("propertyWithBackingField").hasBackingField()).isTrue()
    assertThat(file.findProperty("propertyWithoutBackingField").hasBackingField()).isFalse()
    assertThat(file.findProperty("delegatedProperty").hasBackingField()).isFalse()
  }

  @Test
  fun testKtAnnotationEntry_getQualifiedName_validReference() {
    val file = setFileContents("""
      annotation class Foo

      @<caret>Foo
      object Bar
    """.trimIndent())

    val annotationEntry = file.getElementAtCaret<KtAnnotationEntry>()
    assertThat(annotationEntry.getQualifiedName()).isEqualTo("com.android.example.Foo")
  }

  @Test
  fun testKtAnnotationEntry_getQualifiedName_invalidReference() {
    val file = setFileContents("""
      @<caret>Foo
      object Bar
    """.trimIndent())

    val annotationEntry = file.getElementAtCaret<KtAnnotationEntry>()
    assertThat(annotationEntry.getQualifiedName()).isNull()
  }

  @Test
  fun testKtAnnotationEntry_fqNameMatches() {
    val file = setFileContents("""
      annotation class Foo

      @<caret>Foo
      object Bar
    """.trimIndent())

    val annotationEntry = file.getElementAtCaret<KtAnnotationEntry>()

    assertThat(annotationEntry.fqNameMatches("com.android.example.Foo")).isTrue()
    assertThat(annotationEntry.fqNameMatches("com.android.example.Bar")).isFalse()
    assertThat(annotationEntry.fqNameMatches("com.example.Foo")).isFalse()

    assertThat(annotationEntry.fqNameMatches(setOf("com.android.example.Bar", "com.example.Foo"))).isFalse()
    assertThat(annotationEntry.fqNameMatches(setOf("com.android.example.Foo", "com.android.example.Bar"))).isTrue()
  }

  @Test
  fun testKtClass_className() {
    val file = setFileContents("""
      class <caret>Foo
    """.trimIndent())

    assertThat(file.getElementAtCaret<KtClass>().getQualifiedName()).isEqualTo("com.android.example.Foo")
  }

  @Test
  fun testKtClass_className_kotlinBuiltin() {
    val file = setFileContents("""
      class <caret>Foo
    """.trimIndent(), packageName = "kotlin.some.package")

    assertThat(file.getElementAtCaret<KtClass>().getQualifiedName()).isNull()
  }

  @Test
  fun testKtAnnotationEntry_findArgumentExpression() {
    val file = setFileContents("""
      annotation class Foo(val bar: String)

      @<caret>Foo(bar = "baz")
      object Quux
    """.trimIndent())

    val annotationEntry = file.getElementAtCaret<KtAnnotationEntry>()
    val argumentExpression = annotationEntry.findArgumentExpression("bar")
    assertThat(argumentExpression).isNotNull()
    assertThat(argumentExpression!!.tryEvaluateConstant()).isEqualTo("baz")
  }

  @Test
  fun testKtExpression_tryEvaluateConstant_stringConstant() {
    val file = setFileContents("""
      val bar = 42
      val <caret>foo = "foo" + bar + "baz"
    """.trimIndent())

    val expression: KtExpression = file.getElementAtCaret<KtProperty>().initializer!!
    assertThat(expression.tryEvaluateConstant()).isEqualTo("foo42baz")
    assertThat(expression.tryEvaluateConstantAsText()).isEqualTo("foo42baz")
  }

  @Test
  fun testKtExpression_tryEvaluateConstant_integerConstant() {
    val file = setFileContents("""
      val foo = 2
      val <caret>bar = 1 + foo + 3
    """.trimIndent())

    val expression: KtExpression = file.getElementAtCaret<KtProperty>().initializer!!
    assertThat(expression.tryEvaluateConstant()).isNull()
    assertThat(expression.tryEvaluateConstantAsText()).isEqualTo("6")
  }

  @Test
  fun testKtExpression_tryEvaluateConstant_notConstant() {
    val file = setFileContents("""
      import kotlin.random.Random

      val <caret>foo = Random.nextInt(10)
    """.trimIndent())

    val expression: KtExpression = file.getElementAtCaret<KtProperty>().initializer!!
    assertThat(expression.tryEvaluateConstant()).isNull()
    assertThat(expression.tryEvaluateConstantAsText()).isNull()
  }

  @Test
  fun testKtNamedFunction_className() {
    fun PsiFile.findFunction(name: String) = find<KtNamedFunction> { it.name == name }

    val file = setFileContents("""
      fun topLevelFun() {}

      class SomeClass {
        fun innerFun() {
          fun nestedFun() {}
        }
      }
      """.trimIndent())

    assertThat(file.findFunction("topLevelFun").getClassName()).isEqualTo("com.android.example.MyFileKt")
    assertThat(file.findFunction("innerFun").getClassName()).isEqualTo("com.android.example.SomeClass")
    assertThat(file.findFunction("nestedFun").getClassName()).isEqualTo("com.android.example.SomeClass")
  }

  @Test
  fun testGetPreviousInQualifiedChain() {
    val file = setFileContents("""
      fun foo() {
        val id = R.layout.<caret>activity
      }
      """.trimIndent())

    val fieldReference = file.getElementAtCaret<KtExpression>()
    assertThat(fieldReference.text).isEqualTo("activity")
    assertThat(fieldReference.getPreviousInQualifiedChain()!!.text).isEqualTo("layout")
  }

  @Test
  fun testGetNextInQualifiedChain() {
    val file = setFileContents("""
      fun foo() {
        val id = <caret>R.layout.activity
      }
      """.trimIndent())

    val classReference = file.getElementAtCaret<KtExpression>()
    assertThat(classReference.text).isEqualTo("R")
    assertThat(classReference.getNextInQualifiedChain()!!.text).isEqualTo("layout")
    assertThat(classReference.getNextInQualifiedChain()!!.getNextInQualifiedChain()!!.text).isEqualTo("activity")
  }

  @Test
  fun testToPsiType_class() {
    val file = setFileContents("class Fo<caret>o".trimIndent())

    val classElement = file.getElementAtCaret<KtClassOrObject>()
    assertThat(classElement.toPsiType()?.canonicalText).isEqualTo("com.android.example.Foo")
  }

  @Test
  fun testToPsiType_interface() {
    val file = setFileContents("class Fo<caret>o".trimIndent())

    val classElement = file.getElementAtCaret<KtClassOrObject>()
    assertThat(classElement.toPsiType()?.canonicalText).isEqualTo("com.android.example.Foo")
  }

  @Test
  fun testToPsiType_object() {
    val file = setFileContents("class Fo<caret>o".trimIndent())

    val classElement = file.getElementAtCaret<KtClassOrObject>()
    assertThat(classElement.toPsiType()?.canonicalText).isEqualTo("com.android.example.Foo")
  }

  private inline fun <reified T : PsiElement> PsiFile.getElementAtCaret(): T =
    PsiTreeUtil.findElementOfClassAtOffset(this, myFixture.caretOffset, T::class.java, false)!!

  private inline fun <reified T : PsiElement> PsiFile.find(predicate: (T) -> Boolean): T =
    PsiTreeUtil.findChildrenOfType(this, T::class.java).first(predicate)

  private fun setFileContents(@Language("kotlin") contents: String, packageName: String = "com.android.example"): PsiFile =
    myFixture.addFileToProject(
      "src/${packageName.replace('.', '/')}/MyFile.kt",
      "package ${packageName}\n\n${contents}"
    ).also { myFixture.configureFromExistingVirtualFile(it.virtualFile) }
}
