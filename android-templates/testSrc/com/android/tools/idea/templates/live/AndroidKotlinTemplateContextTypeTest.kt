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
import org.jetbrains.kotlin.idea.liveTemplates.KotlinTemplateContextType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.`when`
import kotlin.reflect.KClass

/**
 * Unit-test for [AndroidKotlinTemplateContextType]
 */
@RunWith(Parameterized::class)
class AndroidKotlinTemplateContextTypeTest(
  private val kotlinTemplateClass: KClass<out KotlinTemplateContextType>,
  private val androidInContext: Boolean,
  private val kotlinInContext: Boolean,
  private val expectedResult: Boolean,
) {
  @Test
  fun test() {
    val templateActionContext = mock<TemplateActionContext>()
    withMockedAndroidSourceSet(templateActionContext) {
      withMockedKotlinTemplate(templateActionContext) {
        // Prepare
        val context = AndroidKotlinTemplateContextType.Generic()

        // Do
        val result = context.isInContext(templateActionContext)

        // Check
        assertEquals(expectedResult, result)
      }
    }
  }

  private fun withMockedAndroidSourceSet(templateActionContext: TemplateActionContext, block: () -> Unit) {
    mockConstruction(AndroidSourceSetTemplateContextType::class.java) { mock, _ ->
      `when`(mock.isInContext(templateActionContext)).thenReturn(androidInContext)
      `when`(mock.presentableName).thenReturn("name")
      block()
    }.close()
  }

  private fun withMockedKotlinTemplate(templateActionContext: TemplateActionContext, block: () -> Unit) {
    mockConstruction(kotlinTemplateClass.java) { mock, _ ->
      `when`(mock.isInContext(templateActionContext)).thenReturn(kotlinInContext)
      `when`(mock.presentableName).thenReturn("name")
      block()
    }.close()
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "class={0} android={1} kotlin={2}; result={3}")
    fun testData(): Array<Array<Any>> = arrayOf(
      // Generic
      arrayOf(KotlinTemplateContextType.Generic::class, true, true, true),
      arrayOf(KotlinTemplateContextType.Generic::class, true, false, false),
      arrayOf(KotlinTemplateContextType.Generic::class, false, true, false),
      arrayOf(KotlinTemplateContextType.Generic::class, false, false, false),

      // TopLevel
      arrayOf(KotlinTemplateContextType.TopLevel::class, true, true, true),
      arrayOf(KotlinTemplateContextType.TopLevel::class, true, false, false),
      arrayOf(KotlinTemplateContextType.TopLevel::class, false, true, false),
      arrayOf(KotlinTemplateContextType.TopLevel::class, false, false, false),

      // ObjectDeclaration
      arrayOf(KotlinTemplateContextType.ObjectDeclaration::class, true, true, true),
      arrayOf(KotlinTemplateContextType.ObjectDeclaration::class, true, false, false),
      arrayOf(KotlinTemplateContextType.ObjectDeclaration::class, false, true, false),
      arrayOf(KotlinTemplateContextType.ObjectDeclaration::class, false, false, false),

      // Class
      arrayOf(KotlinTemplateContextType.Class::class, true, true, true),
      arrayOf(KotlinTemplateContextType.Class::class, true, false, false),
      arrayOf(KotlinTemplateContextType.Class::class, false, true, false),
      arrayOf(KotlinTemplateContextType.Class::class, false, false, false),

      // Statement
      arrayOf(KotlinTemplateContextType.Statement::class, true, true, true),
      arrayOf(KotlinTemplateContextType.Statement::class, true, false, false),
      arrayOf(KotlinTemplateContextType.Statement::class, false, true, false),
      arrayOf(KotlinTemplateContextType.Statement::class, false, false, false),

      // Expression
      arrayOf(KotlinTemplateContextType.Expression::class, true, true, true),
      arrayOf(KotlinTemplateContextType.Expression::class, true, false, false),
      arrayOf(KotlinTemplateContextType.Expression::class, false, true, false),
      arrayOf(KotlinTemplateContextType.Expression::class, false, false, false),

      // Comment
      arrayOf(KotlinTemplateContextType.Comment::class, true, true, true),
      arrayOf(KotlinTemplateContextType.Comment::class, true, false, false),
      arrayOf(KotlinTemplateContextType.Comment::class, false, true, false),
      arrayOf(KotlinTemplateContextType.Comment::class, false, false, false),
    )
  }
}
