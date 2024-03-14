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
package com.android.tools.idea.gradle.something.parser

import com.android.testutils.TestUtils
import com.android.tools.idea.gradle.something.SomethingParserDefinition
import com.android.tools.idea.gradle.something.psi.SomethingAssignment
import com.android.tools.idea.gradle.something.psi.SomethingBlock
import com.android.tools.idea.gradle.something.psi.SomethingElement
import com.android.tools.idea.gradle.something.psi.SomethingEntry
import com.android.tools.idea.gradle.something.psi.SomethingFactory
import com.android.tools.idea.gradle.something.psi.SomethingIdentifier
import com.android.tools.idea.gradle.something.psi.SomethingLiteral
import com.android.tools.idea.gradle.something.psi.SomethingProperty
import com.android.tools.idea.gradle.something.psi.SomethingVisitor
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.ParsingTestCase
import kotlin.reflect.KClass

class SomethingVisitorTest : ParsingTestCase("no_data_path_needed", "something", SomethingParserDefinition()) {
  fun `test visit array table`() {
    doTest<SomethingBlock>(
      """
         an<caret>droid {
         }
      """.trimIndent(), SomethingEntry::class)
  }

  fun `test visit assignment`() {
    doTest<SomethingAssignment>(
      """
         he<caret>llo = "world"
      """.trimIndent(), SomethingEntry::class)
  }

  fun `test visit function`() {
    doTest<SomethingFactory>(
      """
         hel<caret>lo("world")
      """.trimIndent(), SomethingEntry::class)
  }

  fun `test visit block with factory name`() {
    doTest<SomethingBlock>(
      """
         hel<caret>lo("world"){
         }
      """.trimIndent(), SomethingEntry::class)
  }

  private inline fun <reified T : SomethingElement> doTest(
    code: String,
    vararg additionalVisits: KClass<out SomethingElement>
  ) {
    val index = code.indexOf("<caret>").also { if (it == -1) throw Exception("Should define <caret>") }
    val cleanCode = code.replace("<caret>", "")
    val file = createPsiFile("in-memory", cleanCode)

    val visits = mutableListOf<KClass<out SomethingElement>>()

    val visitor = object : SomethingVisitor() {
      override fun visitElement(o: SomethingElement) {
        visits.add(SomethingElement::class)
        super.visitElement(o)
      }

      override fun visitAssignment(o: SomethingAssignment) {
        visits.add(SomethingAssignment::class)

        super.visitAssignment(o)
      }

      override fun visitBlock(o: SomethingBlock) {
        visits.add(SomethingBlock::class)

        super.visitBlock(o)
      }

      override fun visitIdentifier(o: SomethingIdentifier) {
        visits.add(SomethingIdentifier::class)
        super.visitIdentifier(o)
      }

      override fun visitLiteral(o: SomethingLiteral) {
        visits.add(SomethingLiteral::class)
        super.visitLiteral(o)
      }

      override fun visitProperty(o: SomethingProperty) {
        visits.add(SomethingProperty::class)

        super.visitProperty(o)
      }

      override fun visitEntry(o: SomethingEntry) {
        visits.add(SomethingEntry::class)
        super.visitEntry(o)
      }

      override fun visitFactory(o: SomethingFactory) {
        visits.add(SomethingFactory::class)
        super.visitFactory(o)
      }
    }

    val element = file.findElementAt(index)!!.parentOfType<T>(true)!!

    element.accept(visitor)
    assertEquals(listOf(T::class, *additionalVisits, SomethingElement::class), visits)
  }

  override fun getTestDataPath(): String = TestUtils.resolveWorkspacePath("tools/adt/idea/gradle-dsl/testData").toString()

}