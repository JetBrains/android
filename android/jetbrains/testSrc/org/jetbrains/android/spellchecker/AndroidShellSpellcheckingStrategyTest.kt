/*
 * Copyright (C) 2020 The Android Open Source Project
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

/**
 * Tests verifying the behaviour of [AndroidShellSpellcheckingStrategy]
 *
 * Verifies that gradlew files are the only shell files ignore by spellcheck.
 */
class AndroidShellSpellcheckingStrategyTest : AndroidTestCase() {
  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(setOf(SpellCheckingInspection::class.java))
  }

  fun testIgnoredGradlewScript() {
    val gradlewScript = myFixture.copyFileToProject("spellchecker/gradlew", "gradlew")
    myFixture.configureFromExistingVirtualFile(gradlewScript)
    myFixture.checkHighlighting(true, false, false)
  }

  // TODO(b/232037999): Re-enable this test when flakiness is fixed.
  fun ignoreTestNotIgnoredShellScript() {
    val typosScript = myFixture.copyFileToProject("spellchecker/gradlew", "typos")
    myFixture.configureFromExistingVirtualFile(typosScript)
    val highlightingResults = myFixture.doHighlighting()
    assertThat(highlightingResults.filter { it.severity == SpellCheckerSeveritiesProvider.TYPO }).isNotEmpty()
  }
}
