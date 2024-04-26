/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.gradle.navigation

import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.RunsInEdt
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class DeclarativeGotoApiDeclarationHandlerTest {
  @get:Rule
  val projectRule = AndroidGradleProjectRule().onEdt()

  private val myFixture by lazy { projectRule.fixture }

  @Before
  fun onBefore(){
    Registry.get("android.gradle.declarative.plugin.studio.support").setValue(true)
  }

  @After
  fun tearDown(){
    Registry.get("android.gradle.declarative.plugin.studio.support").resetToDefault()
  }

  @Test
  fun testGoToAaptOptionsInToml() {
    projectRule.loadProject(TestProjectPaths.SIMPLE_APPLICATION_VERSION_CATALOG_KTS)

    checkUsage(
      "app/build.gradle.toml",
      "[android.aaptOpt|ions]",
      "com.android.build.api.dsl.CommonExtension",
      "fun aaptOptions(action: com.android.build.api.dsl.AaptOptions.() -> kotlin.Unit): kotlin.Unit"
    )
  }

  @Test
  fun testGoToAndroidInToml() {
    projectRule.loadProject(TestProjectPaths.SIMPLE_APPLICATION_VERSION_CATALOG_KTS)

    checkUsage(
      "app/build.gradle.toml",
      "[andro|id]",
      "com.android.build.api.dsl.ApplicationExtension",
      "public interface ApplicationExtension "
    )
  }

  @Test
  fun testGoToSetterProperty() {
    projectRule.loadProject(TestProjectPaths.SIMPLE_APPLICATION_VERSION_CATALOG_KTS)

    checkUsage(
      "app/build.gradle.toml",
      """
        [android]
        compil|eSdk = 12
      """.trimIndent(),
      "com.android.build.api.dsl.CommonExtension",
      "public abstract var compileSdk: kotlin.Int?"
    )
  }

  @Test
  fun testGoToPropertyInTheMiddle() {
    projectRule.loadProject(TestProjectPaths.SIMPLE_APPLICATION_VERSION_CATALOG_KTS)

    checkUsage(
      "app/build.gradle.toml",
      """
        android.aaptO|ptions.ignoreAssetsPattern = "aaa"
      """.trimIndent(),
      "com.android.build.api.dsl.CommonExtension",
      "public abstract fun aaptOptions(action: com.android.build.api.dsl.AaptOptions.() -> kotlin.Unit)"
    )
  }

  @Test
  fun testGoToFromPropertyInComplexContext() {
    projectRule.loadProject(TestProjectPaths.SIMPLE_APPLICATION_VERSION_CATALOG_KTS)

    checkUsage(
      "app/build.gradle.toml",
      """
        [[plugins]]
        id = "application"
        [android]
        compileSdk = 12
        aaptO|ptions.ignoreAssetsPattern = "aaa"
      """.trimIndent(),
      "com.android.build.api.dsl.CommonExtension",
      "public abstract fun aaptOptions(action: com.android.build.api.dsl.AaptOptions.() -> kotlin.Unit)"
    )
  }

  @Test
  fun testGoToFromBuildType() {
    projectRule.loadProject(TestProjectPaths.SIMPLE_APPLICATION_VERSION_CATALOG_KTS)

    checkUsage(
      "app/build.gradle.toml",
      "android.buildTypes.debug.versionN|ameSuffix =\"aaa\"",
      "com.android.build.api.dsl.ApplicationVariantDimension",
      "var versionNameSuffix: kotlin.String?"
    )
  }

  private fun checkUsage(relativePath: String, caretContext: String, expectedClass: String, expected: String) {
    val caret = caretContext.indexOf('|')
    assertWithMessage("The caretContext must include | somewhere to point to the caret position").that(caret).isNotEqualTo(-1)

    val withoutCaret = caretContext.substring(0, caret) + caretContext.substring(caret + 1)
    val psiFile =  myFixture.addFileToProject(relativePath, withoutCaret)

    val handler = DeclarativeGoToApiDeclarationHandler()
    val element = psiFile.findElementAt(caret)
    val target = handler.getGotoDeclarationTarget(element, null)
    assertWithMessage("Didn't find a go to destination from $caretContext").that(target).isNotNull()
    assertThat(target?.text?.substringBefore("\n")).contains(expected)
    assertThat(target?.containingFile!!.virtualFile.path).contains(expectedClass.replace(".", "/"))
  }
}