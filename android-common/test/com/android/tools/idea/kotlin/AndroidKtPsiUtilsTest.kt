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
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementsAtOffsetUp
import com.intellij.psi.util.parents
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.junit.Test

class AndroidKtPsiUtilsTest : LightJavaCodeInsightFixtureTestCase() {

  @Test
  fun testClassName() {
    fun PsiFile.findFunction(name: String) = PsiTreeUtil.findChildrenOfType(this, KtNamedFunction::class.java).first { it.name == name }

    @Language("kotlin")
    val file = myFixture.addFileToProject("src/com/android/example/MyFile.kt", """
      package com.android.example

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

  fun testGetPreviousInQualifiedChain() {
    val file = myFixture.addFileToProject(
      "src/com/android/example/MyFile.kt",
      """
      package com.android.example
      
      fun foo() {
        val id = R.layout.<caret>activity
      }
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(file.virtualFile)

    val fieldReference: KtExpression = PsiTreeUtil.findElementOfClassAtOffset(
      file,
      myFixture.caretOffset,
      KtExpression::class.java,
      false
    )!!
    assertThat(fieldReference.text).isEqualTo("activity")
    assertThat(fieldReference.getPreviousInQualifiedChain()!!.text).isEqualTo("layout")
  }

  fun testGetNextInQualifiedChain() {
    val file = myFixture.addFileToProject(
      "src/com/android/example/MyFile.kt",
      """
      package com.android.example
      
      fun foo() {
        val id = <caret>R.layout.activity
      }
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(file.virtualFile)

    val classReference: KtExpression = PsiTreeUtil.findElementOfClassAtOffset(
      file,
      myFixture.caretOffset,
      KtExpression::class.java,
      false
    )!!
    assertThat(classReference.text).isEqualTo("R")
    assertThat(classReference.getNextInQualifiedChain()!!.text).isEqualTo("layout")
    assertThat(classReference.getNextInQualifiedChain()!!.getNextInQualifiedChain()!!.text).isEqualTo("activity")
  }
}
