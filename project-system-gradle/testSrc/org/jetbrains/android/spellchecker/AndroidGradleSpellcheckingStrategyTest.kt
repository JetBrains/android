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
package org.jetbrains.android.spellchecker

import com.google.common.truth.Truth.assertThat
import com.intellij.spellchecker.SpellCheckerSeveritiesProvider
import com.intellij.spellchecker.inspections.SpellCheckingInspection
import org.jetbrains.android.AndroidTestCase

class AndroidGradleSpellcheckingStrategyTest : AndroidTestCase() {
  fun testNoTypoInDependencyApplicationStatement() {
    myFixture.enableInspections(setOf(SpellCheckingInspection::class.java))
    val virtualFile = myFixture.addFileToProject(
      "build.gradle",
      //language=Groovy
      """
        dependencies {
          implementation 'com.example:xyzzy:1.0'
        }
      """.trimIndent()).virtualFile
    myFixture.configureFromExistingVirtualFile(virtualFile)
    val typos = myFixture.doHighlighting(SpellCheckerSeveritiesProvider.TYPO)
    assertThat(typos).isEmpty()
  }

  fun testNoTypoInDependencyCallExpression() {
    myFixture.enableInspections(setOf(SpellCheckingInspection::class.java))
    val virtualFile = myFixture.addFileToProject(
      "build.gradle",
      //language=Groovy
      """
        dependencies {
          implementation('com.example:xyzzy:1.0')
        }
      """.trimIndent()).virtualFile
    myFixture.configureFromExistingVirtualFile(virtualFile)
    val typos = myFixture.doHighlighting(SpellCheckerSeveritiesProvider.TYPO)
    assertThat(typos).isEmpty()
  }

  fun testTypoInPrintApplicationStatement() {
    myFixture.enableInspections(setOf(SpellCheckingInspection::class.java))
    val virtualFile = myFixture.addFileToProject(
      "build.gradle",
      //language=Groovy
      """
        dependencies {
          print 'com.example:xyzzy:1.0'
        }
      """.trimIndent()).virtualFile
    myFixture.configureFromExistingVirtualFile(virtualFile)
    val typos = myFixture.doHighlighting(SpellCheckerSeveritiesProvider.TYPO)
    assertThat(typos).hasSize(1)
    assertThat(typos[0].description).isEqualTo("Typo: In word 'xyzzy'")
  }

  fun testTypoInPrintCallExpression() {
    myFixture.enableInspections(setOf(SpellCheckingInspection::class.java))
    val virtualFile = myFixture.addFileToProject(
      "build.gradle",
      //language=Groovy
      """
        dependencies {
          print('com.example:xyzzy:1.0')
        }
      """.trimIndent()).virtualFile
    myFixture.configureFromExistingVirtualFile(virtualFile)
    val typos = myFixture.doHighlighting(SpellCheckerSeveritiesProvider.TYPO)
    // TODO: fix AndroidGradleSpellcheckingStrategy
  }
}