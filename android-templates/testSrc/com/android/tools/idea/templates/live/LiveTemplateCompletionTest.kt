/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.caret
import com.google.common.truth.Truth
import com.intellij.codeInsight.template.impl.LiveTemplateCompletionContributor
import org.junit.Rule
import org.junit.Test

class LiveTemplateCompletionTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory().initAndroid(true)

  @Test
  fun testLiveTemplateAttributeCompletion() {
    LiveTemplateCompletionContributor.setShowTemplatesInTests(true, projectRule.fixture.testRootDisposable)
    val virtualFile = projectRule.fixture.addFileToProject(
      "res/layout/layout.xml",
      //language=XML
      """
        <LinearLayout
                xmlns:android="http://schemas.android.com/apk/res/android"
                $caret
                android:orientation="vertical"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
        </LinearLayout>
      """.trimIndent()).virtualFile
    projectRule.fixture.configureFromExistingVirtualFile(virtualFile)
    projectRule.fixture.type("too")
    projectRule.fixture.completeBasic()
    val lookupElementStrings = projectRule.fixture.lookupElementStrings
    Truth.assertThat(lookupElementStrings).contains("toolsNs")
  }

  @Test
  fun testLiveTemplateTagCompletion() {
    LiveTemplateCompletionContributor.setShowTemplatesInTests(true, projectRule.fixture.testRootDisposable)
    val virtualFile = projectRule.fixture.addFileToProject(
      "res/layout/layout.xml",
      //language=XML
      """
        <LinearLayout
                xmlns:android="http://schemas.android.com/apk/res/android"
                android:orientation="vertical"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                $caret
        </LinearLayout>
      """.trimIndent()).virtualFile
    projectRule.fixture.configureFromExistingVirtualFile(virtualFile)
    projectRule.fixture.type("too")
    projectRule.fixture.completeBasic()
    val lookupElementStrings = projectRule.fixture.lookupElementStrings
    Truth.assertThat(lookupElementStrings).doesNotContain("toolsNs")
  }
}