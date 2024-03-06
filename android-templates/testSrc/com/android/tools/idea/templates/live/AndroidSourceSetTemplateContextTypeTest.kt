/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.templates.live

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiFile
import org.jetbrains.android.facet.AndroidFacet
import org.junit.AfterClass
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.`when`

/**
 * Unit-test for [AndroidSourceSetTemplateContextType]
 */
class AndroidSourceSetTemplateContextTypeTest {
  private val context = AndroidSourceSetTemplateContextType()

  @Test
  fun `android facet is present`() {
    // Prepare
    val templateActionContext = mockedTemplateActionContext(hasAndroidFacet = true)

    // Do
    val result = context.isInContext(templateActionContext)

    // Check
    assertTrue(result)
  }

  @Test
  fun `android facet is not present`() {
    // Prepare
    val templateActionContext = mockedTemplateActionContext(hasAndroidFacet = false)

    // Do
    val result = context.isInContext(templateActionContext)

    // Check
    assertFalse(result)
  }

  private fun mockedTemplateActionContext(hasAndroidFacet: Boolean): TemplateActionContext {
    val templateActionContext = mock<TemplateActionContext>()
    val file = mock<PsiFile>()
    val module = mock<Module>()
    `when`(templateActionContext.file).thenReturn(file)
    `when`(ModuleUtilCore.findModuleForPsiElement(file)).thenReturn(module)
    if (hasAndroidFacet) {
      val androidFacet = mock<AndroidFacet>()
      `when`(AndroidFacet.getInstance(module)).thenReturn(androidFacet)
    } else {
      `when`(AndroidFacet.getInstance(module)).thenReturn(null)
    }
    return templateActionContext
  }

  companion object {
    private lateinit var mockedModuleUtilCore: MockedStatic<ModuleUtilCore>
    private lateinit var mockedAndroidFacet: MockedStatic<AndroidFacet>

    @JvmStatic
    @BeforeClass
    fun setUp() {
      mockedModuleUtilCore = mockStatic(ModuleUtilCore::class.java)
      mockedAndroidFacet = mockStatic(AndroidFacet::class.java)
    }

    @JvmStatic
    @AfterClass
    fun tearDown() {
      mockedModuleUtilCore.close()
      mockedAndroidFacet.close()
    }
  }
}
