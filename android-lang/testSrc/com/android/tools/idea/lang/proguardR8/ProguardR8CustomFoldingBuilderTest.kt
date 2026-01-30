/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.lang.proguardR8

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ProguardR8CustomFoldingBuilderTest(private val fileType: LanguageFileType) : JavaCodeInsightFixtureTestCase() {

  companion object {
    @Suppress("unused")
    @JvmStatic
    @get:Parameterized.Parameters(name = "{0}")
    val fileType = listOf(ProguardR8FileType.INSTANCE, KeepRulesR8FileType.INSTANCE)
  }

  @Test
  fun testCustomRegions() {
    myFixture.configureByText(
      fileType,
      """
      #region

      #endregion
      """
        .trimIndent(),
    )

    val res = (myFixture as CodeInsightTestFixtureImpl).getFoldingDescription(false, false)

    assertThat(res)
      .isEqualTo(
        """
        <fold text='...'>#region

        #endregion</fold>
        """
          .trimIndent()
      )
  }
}
