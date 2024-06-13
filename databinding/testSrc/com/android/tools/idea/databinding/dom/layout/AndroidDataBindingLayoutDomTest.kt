/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.databinding.dom.layout

import com.android.SdkConstants
import com.android.tools.idea.databinding.DataBindingMode
import com.android.tools.idea.databinding.TestDataPaths
import com.android.tools.idea.databinding.module.LayoutBindingModuleCache
import com.android.tools.idea.testing.AndroidDomRule
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.facet.FacetManager
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

/** Tests data-binding specific highlighting and completion in layout XML files. */
@RunWith(Parameterized::class)
class AndroidDataBindingLayoutDomTest(private val myDataBindingMode: DataBindingMode) {
  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun modes() = listOf(DataBindingMode.SUPPORT, DataBindingMode.ANDROIDX)
  }

  private val projectRule = AndroidProjectRule.withSdk().initAndroid(true)
  private val domRule = AndroidDomRule("res/layout") { projectRule.fixture }

  @get:Rule val ruleChain: TestRule = RuleChain.outerRule(projectRule).around(domRule)

  @Before
  fun setUp() {
    // AndroidManifest.xml comes from "databinding/", all other files from "dom/layout"
    projectRule.fixture.testDataPath = "${TestDataPaths.TEST_DATA_ROOT}/databinding"
    projectRule.fixture.copyFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML)

    projectRule.fixture.testDataPath = "${TestDataPaths.TEST_DATA_ROOT}/dom/layout"

    val androidFacet = FacetManager.getInstance(projectRule.module).getFacetByType(AndroidFacet.ID)
    LayoutBindingModuleCache.getInstance(androidFacet!!).dataBindingMode = myDataBindingMode
  }

  @Test
  fun dataBindingHighlighting_complexLayoutWithErrorsAndWarnings() {
    projectRule.fixture.copyFileToProject("DataBindingUser.java", "src/p1/p2/DataBindingUser.java")
    domRule.testHighlighting("databinding_highlighting_complex.xml")
  }

  @Test
  fun dataBindingHighlighting_unknownTypeResultsInError() {
    domRule.testHighlighting("databinding_highlighting_unknown_type.xml")
  }

  @Test
  fun dataBindingHighlighting_handlesEnumMap() {
    projectRule.fixture.copyFileToProject(
      "DataBindingWithEnumMap.java",
      "src/p1/p2/DataBindingWithEnumMap.java",
    )
    domRule.testHighlighting("databinding_highlighting_enum_map.xml")
  }

  /**
   * Regression test for issue [http://b.android.com/195485]
   *
   * Don't highlight data binding attributes as unknown
   */
  @Test
  fun dataBindingHighlighting_unknownAttributes() {
    domRule.testHighlighting("databinding_highlighting_unknown_attribute.xml")
  }

  /**
   * Don't highlight merge as an invalid element in layouts
   *
   * For context, see [https://issuetracker.google.com/37150098]
   */
  @Test
  fun dataBindingHighlighting_mergeInLayoutIsNotAnError() {
    domRule.testHighlighting("databinding_highlighting_merge_as_layout_root.xml")
  }

  @Test
  fun dataBindingHighlighting_mergeIsInvalidIfNotRoot() {
    domRule.testHighlighting("databinding_highlighting_invalid_merge_locations.xml")
  }

  @Test
  fun dataBindingHighlighting_fragmentInLayoutIsNotAnError() {
    domRule.testHighlighting("databinding_highlighting_fragment_as_layout_root.xml")
  }

  @Test
  fun dataBindingHighlighting_dataClassIsValidAttrbute() {
    domRule.testHighlighting("databinding_highlighting_data_class.xml")
  }

  @Test
  fun dataBindingCompletion_caretInVariableBlockWithNoParams() {
    assertThat(domRule.getCompletionResults("databinding_completion_variable_no_params.xml"))
      .containsExactly("name", "type")
  }

  @Test
  fun dataBindingCompletion_caretInVariableBlockStartingNameParam() {
    domRule.testCompletion(
      "databinding_completion_variable_name_param.xml",
      "databinding_completion_variable_name_param_after.xml",
    )
  }

  @Test
  fun dataBindingCompletion_caretInVariableBlockTypeParam() {
    domRule.testCompletion(
      "databinding_completion_variable_type_param.xml",
      "databinding_completion_variable_type_param_after.xml",
    )
  }

  @Test
  fun dataBindingCompletion_caretInExpression() {
    domRule.testCompletion(
      "databinding_completion_expression.xml",
      "databinding_completion_expression_after.xml",
    )
  }

  /**
   * Regression test for [https://issuetracker.google.com/37104001]
   *
   * Code completion in views inside a `<layout>` tag need to pick up default layout parameters.
   */
  @Test
  fun dataBindingCompletion_caretInAndroidAttribute() {
    domRule.testCompletion(
      "databinding_completion_attribute.xml",
      "databinding_completion_attribute_after.xml",
    )
  }

  @Test
  fun dataBindingCompletion_caretInMerge() {
    domRule.testCompletion(
      "databinding_completion_merge.xml",
      "databinding_completion_merge_after.xml",
    )
  }
}
