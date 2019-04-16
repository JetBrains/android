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
package com.android.tools.idea.databinding.dom.layout;

import static com.google.common.truth.Truth.assertThat;

import com.android.SdkConstants;
import com.android.tools.idea.databinding.DataBindingMode;
import com.android.tools.idea.databinding.ModuleDataBinding;
import com.android.tools.idea.databinding.TestDataPaths;
import com.android.tools.idea.testing.AndroidDomRule;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.google.common.collect.Lists;
import com.intellij.facet.FacetManager;
import java.util.List;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Tests data-binding specific highlighting and completion in layout XML files.
 */
@RunWith(Parameterized.class)
public final class AndroidDataBindingLayoutDomTest {
  private final AndroidProjectRule myProjectRule = AndroidProjectRule.withSdk().initAndroid(true);
  private final AndroidDomRule myDomRule = new AndroidDomRule("res/layout", () -> myProjectRule.fixture);

  @Rule
  public final TestRule myRuleChain = RuleChain.outerRule(myProjectRule).around(myDomRule);

  @NotNull
  private final DataBindingMode myDataBindingMode;

  @Parameters(name = "{0}")
  public static List<DataBindingMode> getModes() {
    return Lists.newArrayList(DataBindingMode.SUPPORT, DataBindingMode.ANDROIDX);
  }

  public AndroidDataBindingLayoutDomTest(@NotNull DataBindingMode mode) {
    myDataBindingMode = mode;
  }

  @Before
  public void setUp() {
    // AndroidManifest.xml comes from "databinding/", all other files from "dom/layout"
    myProjectRule.fixture.setTestDataPath(TestDataPaths.TEST_DATA_ROOT + "/databinding");
    myProjectRule.fixture.copyFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML);

    myProjectRule.fixture.setTestDataPath(TestDataPaths.TEST_DATA_ROOT + "/dom/layout");

    AndroidFacet androidFacet = FacetManager.getInstance(myProjectRule.module).getFacetByType(AndroidFacet.ID);
    ModuleDataBinding.getInstance(androidFacet).setMode(myDataBindingMode);
  }

  @Test
  public void dataBindingHighlighting_complexLayoutWithErrorsAndWarnings() {
    myProjectRule.fixture.copyFileToProject("DataBindingUser.java", "src/p1/p2/DataBindingUser.java");
    myDomRule.testHighlighting("databinding_highlighting_complex.xml");
  }

  @Test
  public void dataBindingHighlighting_unknownTypeResultsInError() {
    myDomRule.testHighlighting("databinding_highlighting_unknown_type.xml");
  }

  @Test
  public void dataBindingHighlighting_handlesEnumMap() {
    myProjectRule.fixture.copyFileToProject("DataBindingWithEnumMap.java", "src/p1/p2/DataBindingWithEnumMap.java");
    myDomRule.testHighlighting("databinding_highlighting_enum_map.xml");
  }

  /**
   * Regression test for issue http://b.android.com/195485
   * Don't highlight data binding attributes as unknown
   */
  @Test
  public void dataBindingHighlighting_unknownAttributes() {
    myDomRule.testHighlighting("databinding_highlighting_unknown_attribute.xml");
  }

  /**
   * Don't highlight merge as an invalid element in layouts
   * For context, see https://issuetracker.google.com/37150098
   */
  @Test
  public void dataBindingHighlighting_mergeInLayoutIsNotAnError() {
    myDomRule.testHighlighting("databinding_highlighting_merge_as_layout_root.xml");
  }

  @Test
  public void dataBindingHighlighting_mergeIsInvalidIfNotRoot() {
    myDomRule.testHighlighting("databinding_highlighting_invalid_merge_locations.xml");
  }

  @Test
  public void dataBindingHighlighting_dataClassIsValidAttrbute() {
    myDomRule.testHighlighting("databinding_highlighting_data_class.xml");
  }

  @Test
  public void dataBindingCompletion_caretInVariableBlockWithNoParams() {
    assertThat(myDomRule.getCompletionResults("databinding_completion_variable_no_params.xml")).containsExactly("name", "type");
  }

  @Test
  public void dataBindingCompletion_caretInVariableBlockStartingNameParam() {
    myDomRule.testCompletion(
      "databinding_completion_variable_name_param.xml", "databinding_completion_variable_name_param_after.xml");
  }

  @Test
  public void dataBindingCompletion_caretInVariableBlockTypeParam() {
    myDomRule.testCompletion(
      "databinding_completion_variable_type_param.xml", "databinding_completion_variable_type_param_after.xml");
  }

  @Test
  public void dataBindingCompletion_caretInExpression() {
    myDomRule.testCompletion("databinding_completion_expression.xml", "databinding_completion_expression_after.xml");
  }

  /**
   * Regression test for https://issuetracker.google.com/37104001.
   * Code completion in views inside a <layout> tag need to pick up default layout parameters.
   */
  @Test
  public void dataBindingCompletion_caretInAndroidAttribute() {
    myDomRule.testCompletion("databinding_completion_attribute.xml", "databinding_completion_attribute_after.xml");
  }

  @Test
  public void dataBindingCompletion_caretInMerge() {
    myDomRule.testCompletion("databinding_completion_merge.xml", "databinding_completion_merge_after.xml");
  }
}

