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
package com.android.tools.idea.gradle.dsl.model.util

import com.android.tools.idea.gradle.dsl.TestFileName
import com.android.tools.idea.gradle.dsl.api.util.PsiElementHolder
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase
import org.jetbrains.annotations.SystemDependent
import org.junit.Test
import java.io.File

class PsiElementHolderTest : GradleFileModelTestCase() {
  /** These tests make slightly more stringent requirements on the implementation of the Dsl parsers and on
   * [PsiElementHolder.getRepresentativeContainedPsiElement] than the documentation implies: they implicitly assume that the
   * parser will assign psiElements to lexically apparent blocks and leaf properties, and that the Dsl element tree will be traversed
   * in breadth-first order.  (Both of these features are useful, but not essential, to the rest of the Dsl implementation).
   */

  @Test
  fun testBlocks() {
    writeToBuildFile(TestFile.BLOCKS)
    val buildModel = gradleBuildModel
    buildModel.run {
      assertNotNull(psiElement)
      assertEquals(psiElement, representativeContainedPsiElement)
    }
    buildModel.android().run {
      assertNotNull(psiElement)
      assertEquals(psiElement, representativeContainedPsiElement)
    }
    buildModel.android().defaultConfig().run {
      assertNotNull(psiElement)
      assertEquals(psiElement, representativeContainedPsiElement)
    }
    buildModel.android().defaultConfig().applicationId().run {
      assertNotNull(psiElement)
      assertEquals(psiElement, representativeContainedPsiElement)
    }
    buildModel.dependencies().run {
      assertNull(psiElement)
      assertNull(representativeContainedPsiElement)
    }
  }

  @Test
  fun testStatements() {
    writeToBuildFile(TestFile.STATEMENTS)
    val buildModel = gradleBuildModel
    val applicationIdPsiElement = buildModel.android().defaultConfig().applicationId().psiElement
    assertNotNull(applicationIdPsiElement)
    buildModel.run {
      assertNotNull(psiElement)
      assertEquals(psiElement, representativeContainedPsiElement)
    }
    buildModel.android().run {
      assertNull(psiElement)
      assertEquals(applicationIdPsiElement, representativeContainedPsiElement)
    }
    buildModel.android().defaultConfig().run {
      assertNull(psiElement)
      assertEquals(applicationIdPsiElement, representativeContainedPsiElement)
    }
    buildModel.android().defaultConfig().applicationId().run {
      assertEquals(applicationIdPsiElement, representativeContainedPsiElement)
    }
    buildModel.dependencies().run {
      assertNull(psiElement)
      assertNull(representativeContainedPsiElement)
    }
  }

  @Test
  fun testBlockStatement() {
    writeToBuildFile(TestFile.BLOCK_STATEMENT)
    val buildModel = gradleBuildModel
    val androidPsiElement = buildModel.android().psiElement
    val applicationIdPsiElement = buildModel.android().defaultConfig().applicationId().psiElement
    assertNotNull(androidPsiElement)
    assertNotNull(applicationIdPsiElement)
    buildModel.run {
      assertNotNull(psiElement)
      assertEquals(psiElement, representativeContainedPsiElement)
    }
    buildModel.android().run {
      assertEquals(androidPsiElement, representativeContainedPsiElement)
    }
    buildModel.android().defaultConfig().run {
      assertNull(psiElement)
      assertEquals(applicationIdPsiElement, representativeContainedPsiElement)
    }
    buildModel.android().defaultConfig().applicationId().run {
      assertEquals(applicationIdPsiElement, representativeContainedPsiElement)
    }
    buildModel.dependencies().run {
      assertNull(psiElement)
      assertNull(representativeContainedPsiElement)
    }
  }

  @Test
  fun testStatementBlock() {
    writeToBuildFile(TestFile.STATEMENT_BLOCK)
    val buildModel = gradleBuildModel
    val defaultConfigPsiElement = buildModel.android().defaultConfig().psiElement
    val applicationIdPsiElement = buildModel.android().defaultConfig().applicationId().psiElement
    assertNotNull(defaultConfigPsiElement)
    assertNotNull(applicationIdPsiElement)
    buildModel.run {
      assertNotNull(psiElement)
      assertEquals(psiElement, representativeContainedPsiElement)
    }
    buildModel.android().run {
      assertNull(psiElement)
      assertEquals(defaultConfigPsiElement, representativeContainedPsiElement)
    }
    buildModel.android().defaultConfig().run {
      assertEquals(defaultConfigPsiElement, representativeContainedPsiElement)
    }
    buildModel.android().defaultConfig().applicationId().run {
      assertEquals(applicationIdPsiElement, representativeContainedPsiElement)
    }
    buildModel.dependencies().run {
      assertNull(psiElement)
      assertNull(representativeContainedPsiElement)
    }
  }

  @Test
  fun testBlockAndStatement() {
    writeToBuildFile(TestFile.BLOCK_AND_STATEMENT)
    val buildModel = gradleBuildModel
    val defaultConfigPsiElement = buildModel.android().defaultConfig().psiElement
    val applicationIdPsiElement = buildModel.android().defaultConfig().applicationId().psiElement
    val applicationIdSuffixPsiElement = buildModel.android().defaultConfig().applicationIdSuffix().psiElement
    assertNotNull(defaultConfigPsiElement)
    assertNotNull(applicationIdPsiElement)
    assertNotNull(applicationIdSuffixPsiElement)
    buildModel.run {
      assertNotNull(psiElement)
      assertEquals(psiElement, representativeContainedPsiElement)
    }
    buildModel.android().run {
      assertNull(psiElement)
      assertEquals(defaultConfigPsiElement, representativeContainedPsiElement)
    }
    buildModel.android().defaultConfig().run {
      assertEquals(defaultConfigPsiElement, representativeContainedPsiElement)
    }
    buildModel.android().defaultConfig().applicationId().run {
      assertEquals(applicationIdPsiElement, representativeContainedPsiElement)
    }
    buildModel.android().defaultConfig().applicationIdSuffix().run {
      assertEquals(applicationIdSuffixPsiElement, representativeContainedPsiElement)
    }
    buildModel.dependencies().run {
      assertNull(psiElement)
      assertNull(representativeContainedPsiElement)
    }
  }

  @Test
  fun testStatementAndBlock() {
    writeToBuildFile(TestFile.STATEMENT_AND_BLOCK)
    val buildModel = gradleBuildModel
    val defaultConfigPsiElement = buildModel.android().defaultConfig().psiElement
    val applicationIdPsiElement = buildModel.android().defaultConfig().applicationId().psiElement
    val applicationIdSuffixPsiElement = buildModel.android().defaultConfig().applicationIdSuffix().psiElement
    assertNotNull(defaultConfigPsiElement)
    assertNotNull(applicationIdPsiElement)
    assertNotNull(applicationIdSuffixPsiElement)
    buildModel.run {
      assertNotNull(psiElement)
      assertEquals(psiElement, representativeContainedPsiElement)
    }
    buildModel.android().run {
      assertNull(psiElement)
      assertEquals(defaultConfigPsiElement, representativeContainedPsiElement)
    }
    buildModel.android().defaultConfig().run {
      assertEquals(defaultConfigPsiElement, representativeContainedPsiElement)
    }
    buildModel.android().defaultConfig().applicationId().run {
      assertEquals(applicationIdPsiElement, representativeContainedPsiElement)
    }
    buildModel.android().defaultConfig().applicationIdSuffix().run {
      assertEquals(applicationIdSuffixPsiElement, representativeContainedPsiElement)
    }
    buildModel.dependencies().run {
      assertNull(psiElement)
      assertNull(representativeContainedPsiElement)
    }
  }

  enum class TestFile(val path: @SystemDependent String) : TestFileName {
    BLOCKS("blocks"),
    BLOCK_AND_STATEMENT("blockAndStatement"),
    BLOCK_STATEMENT("blockStatement"),
    STATEMENTS("statements"),
    STATEMENT_AND_BLOCK("statementAndBlock"),
    STATEMENT_BLOCK("statementBlock"),
    ;
    override fun toFile(basePath: @SystemDependent String, extension: String): File {
      return super.toFile("$basePath/psiElementHolder/$path", extension)
    }

  }
}