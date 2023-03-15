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
package com.android.tools.idea.uibuilder.surface

import com.android.tools.idea.rendering.RenderResult
import com.android.tools.idea.rendering.RenderTestUtil
import com.android.tools.idea.validator.LayoutValidator
import com.android.tools.idea.validator.ValidatorData
import com.android.tools.idea.validator.ValidatorHierarchy
import com.android.tools.idea.validator.ValidatorResult
import com.android.tools.idea.validator.ValidatorUtil
import com.android.tools.res.FrameworkResourceRepositoryManager
import com.google.common.util.concurrent.Futures
import org.jetbrains.android.AndroidTestCase
import java.util.EnumSet
import java.util.stream.Collectors

class AccessibilityTestingFrameworkValidatorTest : AndroidTestCase() {

  companion object {
    private val TEST_POLICY = ValidatorData.Policy(
      EnumSet.of(ValidatorData.Type.ACCESSIBILITY,
                 ValidatorData.Type.RENDER),
      EnumSet.of(ValidatorData.Level.ERROR, ValidatorData.Level.WARNING))
  }

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    RenderTestUtil.beforeRenderTestCase()
  }

  @Throws(Exception::class)
  override fun tearDown() {
    try {
      RenderTestUtil.afterRenderTestCase()
      LayoutValidator.updatePolicy(LayoutValidator.DEFAULT_POLICY)
    }
    finally {
      FrameworkResourceRepositoryManager.getInstance().clearCache()
      super.tearDown()
    }
  }

  fun testRenderHasResult() {
    renderAndValidate(DUP_BOUNDS_LAYOUT) { validatorResult ->
      assertNotNull(validatorResult)
    }
  }

  fun testDupBounds() {
    renderAndValidate(DUP_BOUNDS_LAYOUT) { validatorResult ->
      val dupBounds = filter(validatorResult.issues, "DuplicateClickableBoundsCheck")
      assertEquals(1, dupBounds.size)
    }
  }

  fun testTextContrastSimple() {
    renderAndValidate(TEXT_COLOR_CONTRAST_SIMPLE) { validatorResult ->
      val textContrast = filter(validatorResult.issues, "TextContrastCheck")
      assertEquals(1, textContrast.size)
    }
  }

  fun testTextContrastComplex() {
    renderAndValidate(TEXT_COLOR_CONTRAST_COMPLEX) { validatorResult ->
      val textContrast = filter(validatorResult.issues, "TextContrastCheck")
      assertEquals(4, textContrast.size)
    }
  }

  private fun renderAndValidate(layout: String, validationChecks: (ValidatorResult) -> Unit) {
    val layoutFile = myFixture.addFileToProject("res/layout/layoutvalidator.xml", layout).virtualFile
    val layoutConfiguration = RenderTestUtil.getConfiguration(myModule, layoutFile)
    RenderTestUtil.withRenderTask(myFacet, layoutFile, layoutConfiguration, true) {
      val result = Futures.getUnchecked(it.render())
      val validatorResult = getValidatorResult(result)
      validationChecks(validatorResult)
    }
  }

  private fun getValidatorResult(result: RenderResult): ValidatorResult {
    return ValidatorUtil.generateResults(TEST_POLICY, result.validatorResult as ValidatorHierarchy)
  }

  private fun filter(
    results: List<ValidatorData.Issue>, sourceClass: String): List<ValidatorData.Issue?> {
    return results.stream().filter { issue: ValidatorData.Issue -> sourceClass == issue.mSourceClass }.collect(
      Collectors.toList())
  }
}