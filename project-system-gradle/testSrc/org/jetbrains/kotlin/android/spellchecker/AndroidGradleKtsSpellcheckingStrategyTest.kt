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
package org.jetbrains.kotlin.android.spellchecker

import com.google.common.truth.Truth
import com.intellij.codeInsight.daemon.ProblemHighlightFilter
import com.intellij.spellchecker.SpellCheckerSeveritiesProvider
import com.intellij.spellchecker.inspections.SpellCheckingInspection
import com.intellij.testFramework.ExtensionTestUtil.maskExtensions
import org.jetbrains.android.AndroidTestCase

class AndroidGradleKtsSpellcheckingStrategyTest : AndroidTestCase() {
  override fun setUp() {
    super.setUp()
    maskExtensions(ProblemHighlightFilter.EP_NAME, listOf(), myFixture.projectDisposable)
    unmaskKotlinHighlightVisitor()
  }

  fun testNoTypoInDependencyCallExpression() {
    myFixture.enableInspections(setOf(SpellCheckingInspection::class.java))
    val virtualFile = myFixture.addFileToProject(
      "build.gradle.kts",
      //language=kotlin
      """
        dependencies {
          implementation("com.example:xyzzy:1.0")
        }
      """.trimIndent()).virtualFile
    myFixture.configureFromExistingVirtualFile(virtualFile)
    val typos = myFixture.doHighlighting(SpellCheckerSeveritiesProvider.TYPO)
    Truth.assertThat(typos).isEmpty()
  }

  fun testTypoInPrintCallExpression() {
    myFixture.enableInspections(setOf(SpellCheckingInspection::class.java))
    val virtualFile = myFixture.addFileToProject(
      "build.gradle.kts",
      //language=kotlin
      """
        dependencies {
          print("com.example:xyzzy:1.0")
        }
      """.trimIndent()).virtualFile
    myFixture.configureFromExistingVirtualFile(virtualFile)
    val typos = myFixture.doHighlighting(SpellCheckerSeveritiesProvider.TYPO)
    Truth.assertThat(typos).hasSize(1)
    Truth.assertThat(typos[0].description).isEqualTo("Typo: In word 'xyzzy'")
  }
}