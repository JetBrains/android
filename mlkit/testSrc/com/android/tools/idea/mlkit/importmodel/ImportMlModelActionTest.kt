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
package com.android.tools.idea.mlkit.importmodel

import com.android.tools.idea.mlkit.MlProjectTestUtil
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.testing.onEdt
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TestActionEvent
import org.jetbrains.android.util.AndroidBundle
import org.junit.Rule
import org.junit.Test

/** Unit tests for [ImportMlModelAction]. */
@RunsInEdt
class ImportMlModelActionTest {
  private val myAction = ImportMlModelAction()

  @get:Rule val projectRule = AndroidProjectRule.withAndroidModels().onEdt()

  private fun setupProjectAndEvent(agpVersion: String, minSdkVersion: Int): AnActionEvent {
    MlProjectTestUtil.setupTestMlProject(
      projectRule.project,
      agpVersion,
      minSdkVersion,
      ImmutableList.of(),
    )

    val dataContext =
      SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, projectRule.project)
        .add(PlatformCoreDataKeys.MODULE, projectRule.project.gradleModule(":"))
        .build()

    return TestActionEvent.createTestEvent(dataContext)
  }

  @Test
  fun allConditionsMet_shouldEnabledPresentation() {
    val event =
      setupProjectAndEvent(ImportMlModelAction.MIN_AGP_VERSION, ImportMlModelAction.MIN_SDK_VERSION)

    myAction.update(event)
    assertThat(event.presentation.isEnabled).isTrue()
  }

  @Test
  fun lowAgpVersion_shouldDisablePresentation() {
    val event = setupProjectAndEvent("3.6.0", ImportMlModelAction.MIN_SDK_VERSION)

    myAction.update(event)

    assertThat(event.presentation.isEnabled).isFalse()
    assertThat(event.presentation.text)
      .isEqualTo(
        AndroidBundle.message(
          "android.wizard.action.requires.new.agp",
          ImportMlModelAction.TITLE,
          ImportMlModelAction.MIN_AGP_VERSION,
        )
      )
  }

  @Test
  fun lowMinSdkApi_shouldDisablePresentation() {
    val event =
      setupProjectAndEvent(
        ImportMlModelAction.MIN_AGP_VERSION,
        ImportMlModelAction.MIN_SDK_VERSION - 2,
      )

    myAction.update(event)

    assertThat(event.presentation.isEnabled).isFalse()
    assertThat(event.presentation.text)
      .isEqualTo(
        AndroidBundle.message(
          "android.wizard.action.requires.minsdk",
          ImportMlModelAction.TITLE,
          ImportMlModelAction.MIN_SDK_VERSION,
        )
      )
  }
}
