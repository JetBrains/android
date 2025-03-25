/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.compose.templates

import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.collect.Lists
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.util.application
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance
import org.jetbrains.kotlin.idea.liveTemplates.KotlinTemplateContextType
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.whenever

@RunWith(Parameterized::class)
internal class ComposeKotlinTemplateContextTypeTest(
  private val composeTemplateClass: KClass<out ComposeKotlinTemplateContextType>,
  private val kotlinTemplateClass: KClass<out KotlinTemplateContextType>,
  private val composeEnabled: Boolean,
  private val kotlinInContext: Boolean,
) {
  @get:Rule val projectRule = AndroidProjectRule.inMemory().withKotlin()

  private val fixture: CodeInsightTestFixture by lazy { projectRule.fixture }

  @Before
  fun setUp() {
    fixture.configureByText("NothingToSeeHere.kt", "class NothingToSeeHere")
    (fixture.module.getModuleSystem() as DefaultModuleSystem).usesCompose = composeEnabled
  }

  @Test
  fun isInContext() {
    application.runReadAction {
      val templateActionContext = TemplateActionContext.surrounding(fixture.file, fixture.editor)
      withMockedKotlinTemplate(templateActionContext) {
        assertThat(composeTemplateClass.createInstance().isInContext(templateActionContext))
          .isEqualTo(composeEnabled && kotlinInContext)
      }
    }
  }

  private fun withMockedKotlinTemplate(
    templateActionContext: TemplateActionContext,
    block: () -> Unit,
  ) {
    mockConstruction(kotlinTemplateClass.java) { mock, _ ->
        whenever(mock.isInContext(templateActionContext)).thenReturn(kotlinInContext)
        whenever(mock.presentableName).thenReturn("name")
        block()
      }
      .close()
  }

  companion object {
    private val BOOLEANS = listOf(true, false)
    private val SIMPLE_NAMES =
      ComposeKotlinTemplateContextType::class.sealedSubclasses.mapNotNull { it.simpleName }

    @JvmStatic
    @Parameterized.Parameters(name = "contextClass={0} compose={2} kotlin={3}")
    fun testData(): Array<Array<Any>> {
      return SIMPLE_NAMES.flatMap { simpleName ->
          Lists.cartesianProduct(BOOLEANS, BOOLEANS).map { (composeEnabled, kotlinInContext) ->
            arrayOf(
              ComposeKotlinTemplateContextType::class.nestedSubClass(simpleName),
              KotlinTemplateContextType::class.nestedSubClass(simpleName),
              composeEnabled,
              kotlinInContext,
            )
          }
        }
        .toTypedArray()
    }

    private fun <T : Any> KClass<T>.nestedSubClass(simpleName: String): KClass<out T> =
      nestedClasses.filterIsInstance<KClass<T>>().single { it.simpleName == simpleName }
  }
}
