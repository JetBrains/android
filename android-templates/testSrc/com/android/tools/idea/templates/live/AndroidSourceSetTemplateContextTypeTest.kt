// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
