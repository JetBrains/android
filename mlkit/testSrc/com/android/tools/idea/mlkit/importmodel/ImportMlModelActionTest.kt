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
import com.android.tools.idea.testing.EdtAndroidProjectRule
import com.android.tools.idea.testing.JavaLibraryDependency
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.testing.onEdt
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TestActionEvent
import org.jetbrains.android.util.AndroidBundle
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Unit tests for [ImportMlModelAction]. */
@RunsInEdt
class ImportMlModelActionTest {
  private var myEvent: AnActionEvent? = null
  private var myAction: ImportMlModelAction? = null

  @Rule var projectRule: EdtAndroidProjectRule = AndroidProjectRule.withAndroidModels().onEdt()

  @Before
  fun setUp() {
    myAction = ImportMlModelAction()
  }

  private fun setupProject(version: String?, version2: Int) {
    MlProjectTestUtil.setupTestMlProject(
      projectRule.project,
      version,
      version2,
      ImmutableList.of<JavaLibraryDependency?>(),
    )

    val dataContext =
      SimpleDataContext.builder()
        .add<Project?>(CommonDataKeys.PROJECT, projectRule.project)
        .add<Module?>(PlatformCoreDataKeys.MODULE, projectRule.project.gradleModule(":"))
        .build()
    myEvent = TestActionEvent.createTestEvent(dataContext)
  }

  @Test
  fun allConditionsMet_shouldEnabledPresentation() {
    setupProject(ImportMlModelAction.MIN_AGP_VERSION, ImportMlModelAction.MIN_SDK_VERSION)
    myAction!!.update(myEvent!!)
    Truth.assertThat(myEvent!!.getPresentation().isEnabled()).isTrue()
  }

  @Test
  fun lowAgpVersion_shouldDisablePresentation() {
    setupProject("3.6.0", ImportMlModelAction.MIN_SDK_VERSION)

    myAction!!.update(myEvent!!)

    Truth.assertThat(myEvent!!.getPresentation().isEnabled()).isFalse()
    Truth.assertThat(myEvent!!.getPresentation().getText())
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
    setupProject(ImportMlModelAction.MIN_AGP_VERSION, ImportMlModelAction.MIN_SDK_VERSION - 2)

    myAction!!.update(myEvent!!)

    Truth.assertThat(myEvent!!.getPresentation().isEnabled()).isFalse()
    Truth.assertThat(myEvent!!.getPresentation().getText())
      .isEqualTo(
        AndroidBundle.message(
          "android.wizard.action.requires.minsdk",
          ImportMlModelAction.TITLE,
          ImportMlModelAction.MIN_SDK_VERSION,
        )
      )
  }
}
