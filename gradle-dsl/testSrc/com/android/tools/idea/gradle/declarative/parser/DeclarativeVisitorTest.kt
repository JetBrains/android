/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.declarative.parser

import com.android.testutils.TestUtils
import com.android.tools.idea.gradle.declarative.DeclarativeParserDefinition
import com.android.tools.idea.gradle.declarative.psi.DeclarativeAssignment
import com.android.tools.idea.gradle.declarative.psi.DeclarativeBlock
import com.android.tools.idea.gradle.declarative.psi.DeclarativeElement
import com.android.tools.idea.gradle.declarative.psi.DeclarativeEntry
import com.android.tools.idea.gradle.declarative.psi.DeclarativeFactory
import com.android.tools.idea.gradle.declarative.psi.DeclarativeIdentifier
import com.android.tools.idea.gradle.declarative.psi.DeclarativeLiteral
import com.android.tools.idea.gradle.declarative.psi.DeclarativeProperty
import com.android.tools.idea.gradle.declarative.psi.DeclarativeVisitor
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.ParsingTestCase
import kotlin.reflect.KClass

class DeclarativeVisitorTest : ParsingTestCase("no_data_path_needed", "dcl", DeclarativeParserDefinition()) {
  fun `test visit array table`() {
    doTest<DeclarativeBlock>(
      """
         an<caret>droid {
         }
      """.trimIndent(), DeclarativeEntry::class)
  }

  fun `test visit assignment`() {
    doTest<DeclarativeAssignment>(
      """
         he<caret>llo = "world"
      """.trimIndent(), DeclarativeEntry::class)
  }

  fun `test visit function`() {
    doTest<DeclarativeFactory>(
      """
         hel<caret>lo("world")
      """.trimIndent(), DeclarativeEntry::class)
  }

  fun `test visit block with factory name`() {
    doTest<DeclarativeBlock>(
      """
         hel<caret>lo("world"){
         }
      """.trimIndent(), DeclarativeEntry::class)
  }

  private inline fun <reified T : DeclarativeElement> doTest(
    code: String,
    vararg additionalVisits: KClass<out DeclarativeElement>
  ) {
    val index = code.indexOf("<caret>").also { if (it == -1) throw Exception("Should define <caret>") }
    val cleanCode = code.replace("<caret>", "")
    val file = createPsiFile("in-memory", cleanCode)

    val visits = mutableListOf<KClass<out DeclarativeElement>>()

    val visitor = object : DeclarativeVisitor() {
      override fun visitElement(o: DeclarativeElement) {
        visits.add(DeclarativeElement::class)
        super.visitElement(o)
      }

      override fun visitAssignment(o: DeclarativeAssignment) {
        visits.add(DeclarativeAssignment::class)
        super.visitAssignment(o)
      }

      override fun visitBlock(o: DeclarativeBlock) {
        visits.add(DeclarativeBlock::class)
        super.visitBlock(o)
      }

      override fun visitIdentifier(o: DeclarativeIdentifier) {
        visits.add(DeclarativeIdentifier::class)
        super.visitIdentifier(o)
      }

      override fun visitLiteral(o: DeclarativeLiteral) {
        visits.add(DeclarativeLiteral::class)
        super.visitLiteral(o)
      }

      override fun visitProperty(o: DeclarativeProperty) {
        visits.add(DeclarativeProperty::class)
        super.visitProperty(o)
      }

      override fun visitEntry(o: DeclarativeEntry) {
        visits.add(DeclarativeEntry::class)
        super.visitEntry(o)
      }

      override fun visitFactory(o: DeclarativeFactory) {
        visits.add(DeclarativeFactory::class)
        super.visitFactory(o)
      }
    }

    val element = file.findElementAt(index)!!.parentOfType<T>(true)!!

    element.accept(visitor)
    assertEquals(listOf(T::class, *additionalVisits, DeclarativeElement::class), visits)
  }

  override fun getTestDataPath(): String = TestUtils.resolveWorkspacePath("tools/adt/idea/gradle-dsl/testData").toString()

}