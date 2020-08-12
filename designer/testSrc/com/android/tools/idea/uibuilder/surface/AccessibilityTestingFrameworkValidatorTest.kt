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
import com.android.tools.idea.rendering.RenderService
import com.android.tools.idea.rendering.RenderTestUtil
import com.android.tools.idea.res.FrameworkResourceRepositoryManager
import com.android.tools.idea.validator.LayoutValidator
import com.android.tools.idea.validator.ValidatorData
import com.android.tools.idea.validator.ValidatorResult
import com.google.common.util.concurrent.Futures
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.jetbrains.android.AndroidTestCase
import org.junit.Assert
import java.util.EnumSet
import java.util.stream.Collectors

class AccessibilityTestingFrameworkValidatorTest : AndroidTestCase() {

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    RenderTestUtil.beforeRenderTestCase()
    val policy = ValidatorData.Policy(
      EnumSet.of(ValidatorData.Type.ACCESSIBILITY,
                 ValidatorData.Type.RENDER),
      EnumSet.of(ValidatorData.Level.ERROR, ValidatorData.Level.WARNING)
    )
    LayoutValidator.updatePolicy(policy)
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
    val result = renderAndResult(DUP_BOUNDS_LAYOUT)

    val validatorResult = result.validatorResult
    assertNotNull(validatorResult)
    assertTrue(validatorResult is ValidatorResult)
  }

  fun testDupBounds() {
    val result = renderAndResult(DUP_BOUNDS_LAYOUT)
    val validatorResult = result.validatorResult as ValidatorResult

    val dupBounds = filter(validatorResult.issues, "DuplicateClickableBoundsCheck")
    assertEquals(1, dupBounds.size)
  }

  fun testTextContrastSimple() {
    val result = renderAndResult(TEXT_COLOR_CONTRAST_SIMPLE)
    val validatorResult = result.validatorResult as ValidatorResult

    val textContrast = filter(validatorResult.issues, "TextContrastCheck")
    assertEquals(1, textContrast.size)
  }

  fun testTextContrastComplex() {
    val result = renderAndResult(TEXT_COLOR_CONTRAST_COMPLEX)
    val validatorResult = result.validatorResult as ValidatorResult

    val textContrast = filter(validatorResult.issues, "TextContrastCheck")
    assertEquals(4, textContrast.size)
  }

  private fun renderAndResult(layout: String): RenderResult {
    val layoutFile = myFixture.addFileToProject("res/layout/layoutvalidator.xml", layout).virtualFile
    val layoutConfiguration = RenderTestUtil.getConfiguration(myModule, layoutFile)

    val facet = myFacet
    val module = facet.module
    val psiFile = ReadAction.compute<PsiFile?, RuntimeException> { PsiManager.getInstance(module.project).findFile(layoutFile) }
    Assert.assertNotNull(psiFile)
    val renderService = RenderService.getInstance(module.project)
    val logger = renderService.createLogger(facet)
    val task = renderService.taskBuilder(facet, layoutConfiguration)
      .withLogger(logger)
      .withPsiFile(psiFile!!)
      .disableSecurityManager()
      .withLayoutScanner(true)
      .buildSynchronously()
    Assert.assertNotNull(task)
    try {
      return Futures.getUnchecked(task!!.render())
    }
    finally {
      task!!.dispose()
    }
  }

  private fun filter(
    results: List<ValidatorData.Issue>, sourceClass: String): List<ValidatorData.Issue?> {
    return results.stream().filter { issue: ValidatorData.Issue -> sourceClass == issue.mSourceClass }.collect(
      Collectors.toList())
  }
}