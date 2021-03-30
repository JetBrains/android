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
package com.android.tools.idea.imports

import com.android.testutils.TestUtils
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.loadNewFile
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * Tests for [AndroidMavenImportIntentionAction], for disabling [StudioFlags.SUGGESTED_IMPORT] specific tests.
 */
@RunsInEdt
class AndroidMavenImportIntentionActionSuggestedImportDisabledTest {
  private val projectRule = AndroidGradleProjectRule()

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())

  @Before
  fun setUp() {
    StudioFlags.ENABLE_SUGGESTED_IMPORT.override(false)
    ApplicationManager.getApplication().replaceService(
      MavenClassRegistryManager::class.java,
      fakeMavenClassRegistryManager,
      projectRule.fixture.testRootDisposable
    )
  }

  @After
  fun tearDown() {
    StudioFlags.ENABLE_SUGGESTED_IMPORT.clearOverride()
  }

  // Disabled because of b/144188081
  // Most likely this can be resolved by using a project that is configured to use Kotlin
  @Ignore
  fun unresolvedSymbolInKotlin() {
    // In a project not using AndroidX, add a new file which contains an unresolved
    // symbol "RecyclerView"; check that the unresolved action applies to it, has
    // the right label and when invoked adds a com.android.support:recyclerview
    // dependency

    projectRule.loadProject(TestProjectPaths.MIGRATE_TO_APP_COMPAT) // project not using AndroidX
    assertBuildGradle(projectRule) { !it.contains("com.android.support:recyclerview-v7:") } // not already using recyclerview

    projectRule.fixture.loadNewFile("app/src/main/java/test/pkg/imports/MainActivity2.kt", """
      package test.pkg.imports
      import android.support.v7.app.AppCompatActivity;
      class MainActivity2 : AppCompatActivity() {
          val view = RecyclerView() // Here RecyclerView is an unresolvable symbol
      }
      """.trimIndent())
    val source = projectRule.fixture.editor.document.text

    val action = AndroidMavenImportIntentionAction()
    val element = projectRule.fixture.moveCaret("R|ecyclerView")
    val available = action.isAvailable(projectRule.project, projectRule.fixture.editor, element)
    assertThat(available).isTrue()
    assertThat(action.text).isEqualTo("Add dependency on com.android.support:recyclerview-v7 and import")

    // Check corner case: if the caret is at the very end of the word, the element is the element on
    // the right. Make sure we support that one as well:
    val next = projectRule.fixture.moveCaret("RecyclerView|")
    assertThat("(").isEqualTo(next.text)
    assertThat(action.isAvailable(projectRule.project, projectRule.fixture.editor, next)).isTrue()

    performAndWaitForSyncEnd(projectRule) {
      action.perform(projectRule.project, projectRule.fixture.editor, element, true)
    }

    assertBuildGradle(projectRule) { it.contains("implementation 'com.android.support:recyclerview-v7:") }

    // Also make sure the action doesn't apply elsewhere, such as on the "MainActivity2" identifier:
    assertThat(
      action.isAvailable(projectRule.project, projectRule.fixture.editor, projectRule.fixture.moveCaret("Main|Activity2"))).isFalse()

    // Now make sure the action doesn't apply on RecyclerView, since we've already imported it:
    assertThat(
      action.isAvailable(projectRule.project, projectRule.fixture.editor, projectRule.fixture.moveCaret("Recycler|View"))).isFalse()

    // Make sure we've imported the RecyclerView correctly as well
    val newSource = projectRule.fixture.editor.document.text
    val diff = TestUtils.getDiff(source, newSource, 1)
    assertThat(diff.trim()).isEqualTo(
      """
      @@ -3 +3
        import android.support.v7.app.AppCompatActivity;
      + import android.support.v7.widget.RecyclerView
      +
        class MainActivity2 : AppCompatActivity() {
      """.trimIndent().trim()
    )
  }

  @Test
  fun unresolvedSymbolInJava() {
    // Like testUnresolvedSymbolInKotlin but in a Java file
    projectRule.loadProject(TestProjectPaths.MIGRATE_TO_APP_COMPAT)
    assertBuildGradle(projectRule) { !it.contains("com.android.support:recyclerview-v7:") }
    projectRule.fixture.loadNewFile("app/src/main/java/test/pkg/imports/MainActivity2.java", """
      package test.pkg.imports;
      public class Test {
          private RecyclerView view;
      }
      """.trimIndent())
    val source = projectRule.fixture.editor.document.text

    val action = AndroidMavenImportIntentionAction()
    val element = projectRule.fixture.moveCaret("RecyclerView|")
    val available = action.isAvailable(projectRule.project, projectRule.fixture.editor, element)
    assertThat(available).isTrue()
    assertThat(action.text).isEqualTo("Add dependency on com.android.support:recyclerview-v7 and import")

    performAndWaitForSyncEnd(projectRule) {
      action.perform(projectRule.project, projectRule.fixture.editor, element, true)
    }

    assertBuildGradle(projectRule) { it.contains("implementation 'com.android.support:recyclerview-v7:") }

    // Make sure we've imported the RecyclerView correctly as well
    val newSource = projectRule.fixture.editor.document.text
    val diff = TestUtils.getDiff(source, newSource, 1)
    assertThat(diff.trim()).isEqualTo(
      """
      @@ -2 +2
        package test.pkg.imports;
      +
      + import android.support.v7.widget.RecyclerView;
      +
        public class Test {
      """.trimIndent().trim()
    )
  }
}