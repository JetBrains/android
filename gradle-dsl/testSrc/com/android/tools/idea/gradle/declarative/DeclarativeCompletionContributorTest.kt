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
package com.android.tools.idea.gradle.declarative

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.testFramework.RunsInEdt
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

// Test is based on generated schema files. Schema has declarations for
// androidApplication{compileSdk, namespace, jdkVersion, minSdk}, declarativeDependencies{api, implementation}
@RunsInEdt
class DeclarativeCompletionContributorTest : DeclarativeSchemaTestBase() {
  @get:Rule
  override val projectRule = AndroidProjectRule.onDisk().onEdt()
  private val fixture by lazy { projectRule.fixture }

  @Before
  fun before() {
    StudioFlags.GRADLE_DECLARATIVE_IDE_SUPPORT.override(true)
  }

  @After
  fun onAfter() = StudioFlags.GRADLE_DECLARATIVE_IDE_SUPPORT.clearOverride()

  @Test
  fun testBasicRootCompletion() {
    writeToSchemaFile(TestFile.DECLARATIVE_GENERATED_SCHEMAS)
    doTest("a$caret") { suggestions ->
      assertThat(suggestions.toList()).containsExactly(
        "androidApplication", "declarativeDependencies"
      )
    }
  }

  @Test
  fun testInsideBlockCompletion() {
    writeToSchemaFile(TestFile.DECLARATIVE_GENERATED_SCHEMAS)
    doTest("""
      androidApplication{
        a$caret
      }
      """) { suggestions ->
      assertThat(suggestions.toList()).containsExactly(
        "namespace"
      )
    }
  }

  @Test
  fun testPluginBlockCompletion() {
    writeToSchemaFile(TestFile.DECLARATIVE_GENERATED_SCHEMAS)
    doTest("""
      pl$caret
      """) { suggestions ->
      assertThat(suggestions.toList()).containsExactly(
        "plugins", "androidApplication"
      )
    }
  }

  @Test
  fun testInsidePluginBlockCompletion() {
    writeToSchemaFile(TestFile.DECLARATIVE_GENERATED_SCHEMAS)
    doTest("""
      plugins{
        i$caret
      }
      """) { suggestions ->
      assertThat(suggestions.toList()).containsExactly(
        "id", "kotlin"
      )
    }
  }

  private fun doTest(declarativeFile: String, check: (List<String>) -> Unit) {
    val buildFile = fixture.addFileToProject(
      "build.gradle.dcl", declarativeFile)
    fixture.configureFromExistingVirtualFile(buildFile.virtualFile)
    fixture.completeBasic()
    val list: List<String> = fixture.lookupElements!!.map {
      val presentation = LookupElementPresentation()
      it.renderElement(presentation)
      it.lookupString
    }

    check.invoke(list)
  }
}