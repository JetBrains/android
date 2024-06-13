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
package com.android.tools.idea.databinding.xml

import com.android.SdkConstants
import com.android.tools.idea.databinding.DataBindingMode
import com.android.tools.idea.databinding.TestDataPaths
import com.android.tools.idea.databinding.module.LayoutBindingModuleCache
import com.android.tools.idea.testing.AndroidDomRule
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.facet.FacetManager
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class DataBindingXmlAutocompletionTest(private val dataBindingMode: DataBindingMode) {
  private val myProjectRule = AndroidProjectRule.inMemory().initAndroid(true)
  private val myDomRule = AndroidDomRule("res/layout") { myProjectRule.fixture }

  @Rule @JvmField val myRuleChain: TestRule = RuleChain.outerRule(myProjectRule).around(myDomRule)

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modes() = listOf(DataBindingMode.SUPPORT, DataBindingMode.ANDROIDX)
  }

  @Before
  fun setUp() {
    // AndroidManifest.xml comes from "databinding/", all other files from "dom/layout"
    myProjectRule.fixture.testDataPath = "${TestDataPaths.TEST_DATA_ROOT}/databinding"
    myProjectRule.fixture.copyFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML)

    myProjectRule.fixture.testDataPath = "${TestDataPaths.TEST_DATA_ROOT}/xml"

    val androidFacet =
      FacetManager.getInstance(myProjectRule.module).getFacetByType(AndroidFacet.ID)
    LayoutBindingModuleCache.getInstance(androidFacet!!).dataBindingMode = dataBindingMode
  }

  @Test
  fun dataBindingXmlCompletion_caretInImportTag() {
    myDomRule.testCompletion(
      "databinding_xml_completion_import.xml",
      "databinding_xml_completion_import_after.xml",
    )
  }

  @Test
  fun dataBindingXmlCompletion_caretInVariableTag() {
    myDomRule.testCompletion(
      "databinding_xml_completion_variable.xml",
      "databinding_xml_completion_variable_after.xml",
    )
  }

  @Test
  fun dataBindingXmlCompletion_caretInDataTag() {
    myDomRule.testCompletion(
      "databinding_xml_completion_data.xml",
      "databinding_xml_completion_data_after.xml",
    )
  }

  @Test
  fun dataBindingXmlCompletion_caretInDataClassAttribute() {
    myDomRule.testCompletion(
      "databinding_xml_completion_data_class.xml",
      "databinding_xml_completion_data_class_after.xml",
    )
  }
}
