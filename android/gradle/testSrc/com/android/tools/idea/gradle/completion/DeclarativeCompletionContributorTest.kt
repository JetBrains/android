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
package com.android.tools.idea.gradle.completion

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.caret
import com.google.common.truth.Truth
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.android.AndroidTestCase
import org.junit.After
import org.junit.Before
import org.junit.Test

@RunsInEdt
class DeclarativeCompletionContributorTest : AndroidTestCase() {
  @Before
  override fun setUp() {
    super.setUp()
    StudioFlags.DECLARATIVE_PLUGIN_STUDIO_SUPPORT.override(true)
  }

  @After
  override fun tearDown() {
    super.tearDown()
    StudioFlags.DECLARATIVE_PLUGIN_STUDIO_SUPPORT.clearOverride()
  }

  @Test
  fun testBasicCompletionInKey() =
    doTest("a$caret") { suggestions ->
      Truth.assertThat(suggestions.toList()).containsExactly(
        "android" to "Block element", "configurations" to "Block element", "crashlytics" to "Block element", "java" to "Block element"
      )
    }

  @Test
  fun testBasicCompletionInTableHeader() =
    doTest("[a$caret]") { suggestions ->
      Truth.assertThat(suggestions.toList()).containsExactly(
        "android" to "Block element", "configurations" to "Block element", "crashlytics" to "Block element", "java" to "Block element"
      )
    }

  @Test
  fun testBasicCompletionSecondLevel() =
    doTest("[android.$caret]") { suggestions ->
      Truth.assertThat(suggestions.toList()).containsAllOf(
        "aaptOptions" to "Block element",
        "aidlPackagedList" to "String Array",
        "compileSdk" to "Integer",
        "compileSdkVersion" to "Integer"
      )
    }

  @Test
  fun testBasicCompletionSecondLevelInTable() =
    doTest("""
      [android]
      $caret
      """.trimIndent()) { suggestions ->
      Truth.assertThat(suggestions.toList()).containsAllOf(
        "aaptOptions" to "Block element",
        "aidlPackagedList" to "String Array",
        "compileSdk" to "Integer",
        "compileSdkVersion" to "Integer"
      )
    }

  @Test
  fun testBasicCompletionThirdLevel() =
    doTest("[android.defaultConfig.$caret]") { suggestions ->
      Truth.assertThat(suggestions.toList()).containsAllOf(
        "applicationId" to "Property",
        "buildConfigField" to "Property",
        "renderscriptSupportModeEnabled" to "Property"
      )
    }

  @Test
  fun testBasicCompletionBuildTypes() =
    doTest("[android.buildTypes.$caret]") { suggestions ->
      Truth.assertThat(suggestions.toList()).isEmpty()
    }

  @Test
  fun testBasicCompletionBuildType() =
    doTest("""
      [android.buildTypes.debug]
      $caret
      """.trimIndent()) { suggestions ->
      Truth.assertThat(suggestions.toList()).containsAllOf(
        "applicationIdSuffix" to "Property", "consumerProguardFiles" to "Property", "signingConfig" to "Property"
      )
    }

  private fun doTest(declarativeFile: String, check: (Map<String, String>) -> Unit) {
    val buildFile = myFixture.addFileToProject(
      "build.gradle.toml", declarativeFile)
    myFixture.configureFromExistingVirtualFile(buildFile.virtualFile)
    myFixture.completeBasic()
    val map: Map<String, String> = myFixture.lookupElements.associate {
      val presentation = LookupElementPresentation()
      it.renderElement(presentation)
      it.lookupString to (presentation.typeText ?: "")
    }

    check.invoke(map)
  }

}