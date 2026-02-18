/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.lang.com.android.tools.idea.lang.proguardR8

import com.android.tools.idea.lang.proguardR8.KeepRulesR8FileType
import com.android.tools.idea.lang.proguardR8.ProguardR8FileType
import com.android.tools.idea.lang.proguardR8.ProguardR8InvalidFlagInspection
import com.android.tools.idea.testing.highlightedAs
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.fileTypes.LanguageFileType
import org.jetbrains.android.JavaCodeInsightFixtureAdtTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

abstract class ProguardR8FlagsTestCase : JavaCodeInsightFixtureAdtTestCase() {
  override fun setUp() {
    super.setUp()
    // Only turn on expensive keep rule inspections
    myFixture.enableInspections(ProguardR8InvalidFlagInspection::class.java)
  }
}

@RunWith(Parameterized::class)
class ProguardR8FlagsTest(private val fileType: LanguageFileType) : ProguardR8FlagsTestCase() {
  companion object {
    @Suppress("unused")
    @JvmStatic
    @get:Parameterized.Parameters(name = "{0}")
    val fileType = listOf(ProguardR8FileType.INSTANCE, KeepRulesR8FileType.INSTANCE)
  }

  @Test
  fun testProcessKotlinNullChecksFlag() {
    val rules =
      listOf(
        "-processkotlinnullchecks",
        "-processkotlinnullchecks keep",
        "-processkotlinnullchecks remove_message",
        "-processkotlinnullchecks remove",
      )
    rules.forEach { rule ->
      myFixture.configureByText(fileType, rule)
      myFixture.checkHighlighting()
    }
  }

  @Test
  fun testInvalidFlag() {
    val flag = "-invalid"
    val highlight = flag.highlightedAs(level = HighlightSeverity.ERROR, message = "Invalid flag")
    myFixture.configureByText(fileType, highlight)
    myFixture.checkHighlighting()
  }
}
