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
package com.android.tools.idea.lang.databinding

import com.android.tools.idea.databinding.DataBindingMode
import com.android.tools.idea.databinding.ModuleDataBinding
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.facet.FacetManager
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * A test which loads the "testData/projects/calculator" data binding project. The test is
 * designed to ensure various classes in the lang-databinding module are covered.
 */
class CalculatorProjectTest {
  private val projectRule = AndroidProjectRule.withSdk().initAndroid(true)
  private val edtRule = EdtRule()

  @get:Rule
  val chain = RuleChain(projectRule, edtRule)

  /**
   * Expose the underlying project rule fixture directly.
   *
   * We know that the underlying fixture is a [JavaCodeInsightTestFixture] because our
   * [AndroidProjectRule] is initialized to use the disk.
   *
   * In some cases, using the specific subclass provides us with additional methods we can
   * use to inspect the state of our parsed files. In other cases, it's just fewer characters
   * to type.
   */
  private val fixture: JavaCodeInsightTestFixture
    get() = projectRule.fixture as JavaCodeInsightTestFixture

  @Before
  fun setUp() {
    fixture.testDataPath = "${getTestDataPath()}/projects/calculator"
    fixture.copyDirectoryToProject("", "")
    val androidFacet = FacetManager.getInstance(projectRule.module).getFacetByType(AndroidFacet.ID)
    ModuleDataBinding.getInstance(androidFacet!!).dataBindingMode = DataBindingMode.ANDROIDX
  }

  @Test
  @RunsInEdt
  fun verifyProjectLoaded() {
    fixture.findClass("test.langdb.calc.Calculator") // Internally asserts if class isn't found
  }

  // TODO(b/123021236): Add more tests interacting with the project to bring code coverage up
}