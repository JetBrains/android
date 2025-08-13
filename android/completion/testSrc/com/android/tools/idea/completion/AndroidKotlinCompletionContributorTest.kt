/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.completion

import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.moveCaret
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.project.guessProjectDir
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class AndroidKotlinCompletionContributorTest {
  @get:Rule
  val projectRule = AndroidGradleProjectRule().onEdt()
  val project by lazy { projectRule.project }
  val fixture by lazy { projectRule.fixture }

  @Test
  fun testFilteredPrivateResources() {
    projectRule.loadProject(TestProjectPaths.TEST_ARTIFACTS_KOTLIN)
    val activityPath = "app/src/main/java/com/example/android/kotlin/MainActivity.kt"
    val file = project.guessProjectDir()!!.findFileByRelativePath(activityPath)
    fixture.openFileInEditor(file!!)

    fixture.moveCaret("setContentView(R.layout.|activity_main)")

    fixture.complete(CompletionType.BASIC)
    assertThat(fixture.lookupElementStrings).doesNotContain("abc_action_mode_bar")
    assertThat(fixture.lookupElementStrings).contains("activity_main")
  }

  @Test
  fun testFilteredPrivateResourcesInTests() {
    projectRule.loadProject(TestProjectPaths.TEST_ARTIFACTS_KOTLIN)
    val activityPath = "app/src/androidTest/java/com/example/android/kotlin/ExampleInstrumentedTest.kt"
    val file = project.guessProjectDir()!!.findFileByRelativePath(activityPath)
    fixture.openFileInEditor(file!!)

    fixture.moveCaret("assertEquals(\"com.example.android.kotlin\", appContext.packageName)|")
    fixture.type("\ncom.example.android.kotlin.R.layout.")

    fixture.complete(CompletionType.BASIC)
    assertThat(fixture.lookupElementStrings).doesNotContain("abc_action_mode_bar")
    assertThat(fixture.lookupElementStrings).contains("activity_main")
  }

  @Test
  fun testFilteredPrivateResourcesAliasedR() {
    projectRule.loadProject(TestProjectPaths.TEST_ARTIFACTS_KOTLIN)
    val activityPath = "app/src/main/java/com/example/android/kotlin/MainActivity.kt"
    val file = project.guessProjectDir()!!.findFileByRelativePath(activityPath)
    fixture.openFileInEditor(file!!)

    fixture.moveCaret("import android.os.Bundle|")
    fixture.type("\nimport com.example.android.kotlin.R as Q")
    fixture.moveCaret("setContentView(R.layout.activity_main)|")
    fixture.type("\nsetContentView(Q.layout.")

    fixture.complete(CompletionType.BASIC)
    assertThat(fixture.lookupElementStrings).doesNotContain("abc_action_mode_bar")
    assertThat(fixture.lookupElementStrings).contains("activity_main")
  }
}
