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
package com.android.tools.idea.lang.databinding.completion

import com.android.tools.idea.databinding.DataBindingMode
import com.android.tools.idea.databinding.module.LayoutBindingModuleCache
import com.android.tools.idea.lang.databinding.getTestDataPath
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.caret
import com.google.common.truth.Truth.assertThat
import com.intellij.facet.FacetManager
import com.intellij.psi.PsiClass
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Tests against various completion scenarios which ensures that [DataBindingCompletionSupportImpl]
 * is covered.
 */
@RunWith(Parameterized::class)
class DataBindingCompletionSupportTest(private val mode: DataBindingMode) {
  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modes() = listOf(DataBindingMode.SUPPORT, DataBindingMode.ANDROIDX)
  }

  private val projectRule = AndroidProjectRule.withSdk()

  @get:Rule
  val chain: RuleChain = RuleChain.outerRule(projectRule).around(EdtRule())

  private val fixture: JavaCodeInsightTestFixture by lazy {
    projectRule.fixture as JavaCodeInsightTestFixture
  }

  @Before
  fun setUp() {
    fixture.testDataPath = getTestDataPath()
    fixture.addFileToProject("AndroidManifest.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.langdb">
        <application />
      </manifest>
    """.trimIndent())

    val androidFacet = FacetManager.getInstance(projectRule.module).getFacetByType(AndroidFacet.ID)
    LayoutBindingModuleCache.getInstance(androidFacet!!).dataBindingMode = mode
  }

  @Test
  fun getPrefixPackage_worksAsExpected() {
    fun assertExpectedPrefix(expected: String, textWithCaret: String) {
      val offset = textWithCaret.indexOf(caret)
      assertThat(offset).isAtLeast(0)
      val text = textWithCaret.replace(caret, "")
      assertThat(DataBindingCompletionSupportImpl.getPackagePrefix(text, offset)).isEqualTo(expected)
    }

    assertExpectedPrefix("abc.def.ghi", "abc.def.ghi.${caret}")
    assertExpectedPrefix("abc.def.ghi", "    abc.def.ghi.${caret}")
    assertExpectedPrefix("abc.def.ghi", "abc.def.ghi.Cl${caret}ass")
    assertExpectedPrefix("abc", "abc.de${caret}f.ghi")
    assertExpectedPrefix("", "ab${caret}c.def.ghi")
    assertExpectedPrefix("", "${caret}")
  }

  @Test
  fun testDataBindingCompletion_autocompleteImportType_inVariableType() {
    val file = fixture.addFileToProject(
      "res/layout/test_layout.xml",
      // language=XML
      """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="java.util.Map" />
          <variable name="map" type="Ma${caret}" />
        </data>
      </layout>
    """.trimIndent())

    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.completeBasic()

    assertThat(fixture.lookupElementStrings!!).contains("Map")
  }

  @Test
  fun testDataBindingCompletion_autocompleteImportAlias_inVariableType() {
    val file = fixture.addFileToProject(
      "res/layout/test_layout.xml",
      // language=XML
      """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="java.util.Map" alias="MyMap" />
          <variable name="map" type="MyM${caret}" />
        </data>
      </layout>
    """.trimIndent())

    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.completeBasic()

    fixture.checkResult(
      // language=XML
      """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="java.util.Map" alias="MyMap" />
          <variable name="map" type="MyMap" />
        </data>
      </layout>
    """.trimIndent())
  }

  @Test
  fun testDataBindingCompletion_autocompleteInnerClass_inVariableType() {
    val file = fixture.addFileToProject(
      "res/layout/test_layout.xml",
      // language=XML
      """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="java.util.Map" alias="MyMap" />
          <variable name="entry" type="MyMap.En${caret}" />
        </data>
      </layout>
    """.trimIndent())

    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.completeBasic()

    fixture.checkResult(
      // language=XML
      """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="java.util.Map" alias="MyMap" />
          <variable name="entry" type="MyMap.Entry" />
        </data>
      </layout>
    """.trimIndent())
  }

  @Test
  fun testDataBindingCompletion_fullyQualifiedInnerClass_inVariableType() {
    val file = fixture.addFileToProject(
      "res/layout/test_layout.xml",
      // language=XML
      """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name="entry" type="java.util.Map.En${caret}" />
        </data>
      </layout>
    """.trimIndent())

    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.completeBasic()

    fixture.checkResult(
      // language=XML
      """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name="entry" type="java.util.Map.Entry" />
        </data>
      </layout>
    """.trimIndent())
  }

  @Test
  fun testDataBindingCompletion_incompletePackage_inVariableType() {
    val file = fixture.addFileToProject(
      "res/layout/test_layout.xml",
      // language=XML
      """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name="map" type="java.util.${caret}" />
        </data>
      </layout>
    """.trimIndent())

    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.completeBasic()

    fixture.lookupElementStrings!!.let { lookupStrings ->
      // Completions should include direct classes / packages
      assertThat(lookupStrings).containsAllOf("Map", "ArrayList", "concurrent", "stream")

      // Don't include deeply nested classes / packages, e.g. "atomic" from "java.util.concurrent",
      // or "Supplier" from "java.util.function"
      assertThat(lookupStrings).containsNoneOf("Supplier", "atomic")
    }
  }

  @Test
  fun testDataBindingCompletion_otherAliasesExcluded_inAliasType() {
    val file = fixture.addFileToProject(
      "res/layout/test_layout.xml",
      // language=XML
      """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="java.util.Map" alias="MyMap" />
          <import type="My${caret}" />
        </data>
      </layout>
    """.trimIndent())

    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.completeBasic()

    fixture.checkResult(
      // language=XML
      """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <import type="java.util.Map" alias="MyMap" />
          <import type="My" />
        </data>
      </layout>
    """.trimIndent())
  }



  @Test
  fun testDataBindingCompletion_topLevelPackage_allClassesFound() {
    fixture.addClass(
      // language=JAVA
    """
      package a.b.c.d;
      class Example {}
    """.trimIndent()
    )
    val file = fixture.addFileToProject(
      "res/layout/test_layout.xml",
      // language=XML
      """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name="map" type="Ex${caret}" />
        </data>
      </layout>
    """.trimIndent())

    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.completeBasic()

    assertThat(fixture.lookupElementsQualified).containsAllOf("java.util.concurrent.Executor", "a.b.c.d.Example")
  }

  private val JavaCodeInsightTestFixture.lookupElementsQualified: List<String>?
    get() = lookupElements?.map { (it.psiElement as PsiClass).qualifiedName!! }?.toList()
}
