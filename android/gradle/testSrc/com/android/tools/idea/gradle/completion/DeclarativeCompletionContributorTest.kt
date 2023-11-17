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

import com.android.tools.idea.testing.caret
import com.google.common.truth.Truth
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.android.AndroidTestCase
import org.junit.After
import org.junit.Before
import org.junit.Test

@RunsInEdt
class DeclarativeCompletionContributorTest : AndroidTestCase() {
  @Before
  public override fun setUp() {
    super.setUp()
    Registry.get("android.gradle.declarative.plugin.studio.support").setValue(true)
  }

  @After
  public override fun tearDown() {
    try {
      Registry.get("android.gradle.declarative.plugin.studio.support").setValue(false)
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
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
  fun testCompletionStringProperty() = doCompletionTest("""
      [android]
      targetProj$caret
      """.trimIndent(), """
      [android]
      targetProjectPath = "$caret"
      """.trimIndent())

  @Test
  fun testCompletionNonStringProperty() = doCompletionTest("""
      [android]
      generatePureSpl$caret
      """.trimIndent(), """
      [android]
      generatePureSplits = $caret
      """.trimIndent())

  @Test
  fun testCompletionStringPropertyInHeader() = doCompletionTest(
    "[android.targetProj$caret]",
    """
      [android]
      targetProjectPath = "$caret"
      """.trimIndent())

  @Test
  fun testCompletionStringPropertyInHeader2() = doCompletionTest(
    """
      [android.targetProj$caret]
      compileSdk = 1
    """.trimIndent(),
    """
      [android]
      targetProjectPath = "$caret"
      compileSdk = 1
      """.trimIndent())

  @Test
  fun testCompletionNonStringPropertyInHeader() = doCompletionTest(
    "[android.generatePureSpl$caret]".trimIndent(),
    """
      [android]
      generatePureSplits = $caret
      """.trimIndent())

  @Test
  fun testCompletionNonStringPropertyInHeader2() = doCompletionTest(
    """
      [android.generatePureSpl$caret]
      compileSdk = 1
    """.trimIndent(),
    """
      [android]
      generatePureSplits = $caret
      compileSdk = 1
      """.trimIndent())

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

  @Test
  fun testCompletionArrayPropertyInHeader() =
    doCompletionTest(
      "[android.aidlPackagedLi$caret]",
      """
      [android]
      aidlPackagedList = ["$caret"]
      """.trimIndent())


  @Test
  fun testCompletionArrayPropertyInHeader2() =
    doCompletionTest(
      """
      [android.aidlPackagedLi$caret]
      targetSdk = 1
    """.trimIndent(),
      """
      [android]
      aidlPackagedList = ["$caret"]
      targetSdk = 1
      """.trimIndent())

  @Test
  fun testSuggestionsForTableArray() =
    doTest("p$caret") { suggestions ->
      Truth.assertThat(suggestions.toList()).contains(
        "plugins" to "Array Table",
      )
    }

  @Test
  fun testCompletionTableArray() =
    doCompletionTest(
      "plugi$caret",
      """
      [[plugins]]
      $caret
      """.trimIndent())

  @Test
  fun testCompletionTableArray2() =
    doCompletionTest(
      """
      [plugi$caret]
    """.trimIndent(),
      """
      [[plugins]]
      $caret
      """.trimIndent())

  @Test
  fun testCompletionTableArray3() =
    doCompletionTest(
      """
      [[plugi$caret]]
    """.trimIndent(),
      """
      [[plugins]]
      $caret
      """.trimIndent())

  @Test
  fun testCompletionTableArray4() =
    doCompletionTest(
      """
      [plugin$caret]
      [[plugins]]
    """.trimIndent(),
      """
        [[plugins]]
        $caret
        [[plugins]]
      """.trimIndent())

  @Test
  fun testCompletionTableArray5() =
    doCompletionTest(
      " [   plugin$caret]   ",
      " [[   plugins]]   \n$caret")

  fun testCompletionTableArray6() =
    doCompletionTest(
      """[[plu$caret # This is a [[comment]]""",
      "[[plugins]] # This is a [[comment]]\n" +
      "$caret"
    )

  @Test
  fun testCompletionTableArrayNegative() =
    doCompletionTest(
      "   plugi$caret   ",
      "   [[plugins]]   \n" +
      "$caret"
     )

  fun testSuggestionsWithinArrayTable() =
    doTest("""
      [[plugins]]
      $caret
      """.trimIndent()) { suggestions ->
      Truth.assertThat(suggestions.toList()).containsAllOf(
        "id" to "String",
        "version" to "String",
        "apply" to "Boolean",
      )
    }

  private fun doTest(declarativeFile: String, check: (Map<String, String>) -> Unit) {
    val buildFile = myFixture.addFileToProject(
      "build.gradle.toml", declarativeFile)
    myFixture.configureFromExistingVirtualFile(buildFile.virtualFile)
    myFixture.completeBasic()
    val map: Map<String, String> = myFixture.lookupElements!!.associate {
      val presentation = LookupElementPresentation()
      it.renderElement(presentation)
      it.lookupString to (presentation.typeText ?: "")
    }

    check.invoke(map)
  }

  private fun doCompletionTest(declarativeFile: String, fileAfter: String) {
    val buildFile = myFixture.addFileToProject(
      "build.gradle.toml", declarativeFile)
    myFixture.configureFromExistingVirtualFile(buildFile.virtualFile)
    myFixture.completeBasic()

    val caretOffset = fileAfter.indexOf(caret)
    val cleanFileAfter = fileAfter.replace(caret, "")

    Truth.assertThat(buildFile.text).isEqualTo(cleanFileAfter)
    Truth.assertThat(myFixture.editor.caretModel.offset).isEqualTo(caretOffset)
  }

}