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
package com.android.tools.idea.compose.preview.runconfiguration

import com.android.tools.idea.compose.preview.ComposeLightJavaCodeInsightFixtureTestCase
import com.android.tools.idea.flags.StudioFlags
import com.intellij.execution.Location
import com.intellij.execution.PsiLocation
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.MapDataContext
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.psi.KtNamedFunction

class ComposePreviewRunConfigurationProducerTest : ComposeLightJavaCodeInsightFixtureTestCase() {

  private lateinit var composableFunction: KtNamedFunction

  override fun setUp() {
    super.setUp()
    StudioFlags.COMPOSE_PREVIEW_RUN_CONFIGURATION.override(true)

    @Language("kotlin")
    val file = myFixture.addFileToProject("src/Test.kt", """
      import androidx.ui.tooling.preview.Preview
      import androidx.compose.Composable

      @Composable
      @Preview
      fun Preview1() {
      }
    """.trimIndent())
    composableFunction = PsiTreeUtil.findChildrenOfType(file, KtNamedFunction::class.java).first()
  }

  override fun tearDown() {
    super.tearDown()
    StudioFlags.COMPOSE_PREVIEW_RUN_CONFIGURATION.clearOverride()
  }

  fun testSetupConfigurationFromContext() {
    val configurationFromFunction = createConfigurationFromElement(composableFunction)
    assertEquals("Preview1", configurationFromFunction.name)
    assertEquals("TestKt.Preview1", configurationFromFunction.composableMethodFqn)

    // Any PSI children of the KtNamedFunction (e.g. the keyword "fun", the function name, etc.) should produce a run configuration
    val configurationFromFunctionChildren = createConfigurationFromElement(composableFunction.children.random())
    assertEquals("Preview1", configurationFromFunctionChildren.name)
    assertEquals("TestKt.Preview1", configurationFromFunctionChildren.composableMethodFqn)

    assertEquals(2, composableFunction.annotationEntries.size)
    composableFunction.annotationEntries.forEach {
      // The run configuration can also be produced from the function's annotations
      val configurationFromAnnotation = createConfigurationFromElement(it)
      assertEquals("Preview1", configurationFromAnnotation.name)
      assertEquals("TestKt.Preview1", configurationFromAnnotation.composableMethodFqn)
    }
  }

  fun testInvalidContexts() {
    @Language("kotlin")
    val file = myFixture.addFileToProject("src/TestNotPreview.kt", """
      import androidx.ui.tooling.preview.Preview
      import androidx.compose.Composable

      @Preview
      @Composable
      fun Test() {
        fun NotAPreview() {
        }
      }

      @Preview
      @Composable
      fun Test() {
        @Preview
        @Composable
        fun NestedPreview() {
        }
      }
    """.trimIndent())

    val notPreview = PsiTreeUtil.findChildrenOfType(file, KtNamedFunction::class.java).first { it.name == "NotAPreview" }
    val notPreviewConfiguration = createConfigurationFromElement(notPreview)
    assertEquals(newComposePreviewRunConfiguration().name, notPreviewConfiguration.name)
    assertEquals(newComposePreviewRunConfiguration().composableMethodFqn, notPreviewConfiguration.composableMethodFqn)

    val nestedPreview = PsiTreeUtil.findChildrenOfType(file, KtNamedFunction::class.java).first { it.name == "NestedPreview" }
    val nestedPreviewConfiguration = createConfigurationFromElement(nestedPreview)
    assertEquals(newComposePreviewRunConfiguration().name, nestedPreviewConfiguration.name)
    assertEquals(newComposePreviewRunConfiguration().composableMethodFqn, nestedPreviewConfiguration.composableMethodFqn)
  }

  fun testIsConfigurationFromContext() {
    val producer = ComposePreviewRunConfigurationProducer()
    val context = configurationContext(composableFunction)
    val runConfiguration = newComposePreviewRunConfiguration()

    assertFalse(producer.isConfigurationFromContext(runConfiguration, context))
    runConfiguration.name = "Preview1"
    assertFalse(producer.isConfigurationFromContext(runConfiguration, context))
    runConfiguration.composableMethodFqn = "TestKt.Preview1"
    // Both configuration name and composable FQN need to match for the configuration be considered the same as the context's
    assertTrue(producer.isConfigurationFromContext(runConfiguration, context))
    runConfiguration.name = "Preview2"
    assertFalse(producer.isConfigurationFromContext(runConfiguration, context))
  }

  private fun createConfigurationFromElement(element: PsiElement): ComposePreviewRunConfiguration {
    val context = configurationContext(element)
    val runConfiguration = newComposePreviewRunConfiguration()
    val producer = ComposePreviewRunConfigurationProducer()
    producer.setupConfigurationFromContext(runConfiguration, context, Ref(context.psiLocation))

    return runConfiguration
  }

  private fun newComposePreviewRunConfiguration() =
    ComposePreviewRunConfigurationType().configurationFactories[0].createTemplateConfiguration(project) as ComposePreviewRunConfiguration

  private fun configurationContext(element: PsiElement): ConfigurationContext {
    return object : ConfigurationContext(element) {
      override fun getDataContext() = MapDataContext().apply {
        put(LangDataKeys.PROJECT, project)
        put(LangDataKeys.MODULE, ModuleUtilCore.findModuleForPsiElement(element))
        put(Location.DATA_KEY, PsiLocation.fromPsiElement(element))
        put(LangDataKeys.PSI_ELEMENT_ARRAY, arrayOf(element))
      }
    }
  }
}
