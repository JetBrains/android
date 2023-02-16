/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.sdk.sources

import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

/**
 * Tests for [SdkSourcePositionFinderImpl]
 */
@RunsInEdt
class SdkSourcePositionFinderImplTest {
  private val androidProjectRule = AndroidProjectRule.withSdk()

  @get:Rule
  val rule = RuleChain(androidProjectRule, EdtRule())

  private val project get() = androidProjectRule.project

  /**
   * Indirectly tests that the internal SdkSourceFinderForApiLevel is cached rather than created for each call.
   */
  @Test
  fun getSourcePosition_missingSourcesFileIsCreatedOnlyOnce() {
    val file = PsiFileFactory.getInstance(project).createFileFromText("View.java", JavaLanguage.INSTANCE, "")
    val finder = SdkSourcePositionFinderImpl(project)

    val position1 = finder.getSourcePosition(apiLevel = 1, file, lineNumber = 12)
    val position2 = finder.getSourcePosition(apiLevel = 1, file, lineNumber = 13)

    assertThat(position1.file).isSameAs(position2.file)
  }
}