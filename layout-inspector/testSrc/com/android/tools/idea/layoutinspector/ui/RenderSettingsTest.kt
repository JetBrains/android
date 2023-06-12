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
package com.android.tools.idea.layoutinspector.ui

import com.android.tools.adtui.workbench.PropertiesComponentMock
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.replaceService
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class RenderSettingsTest {

  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()

  @get:Rule
  val ruleChain = RuleChain(projectRule, DeviceViewSettingsRule(), disposableRule)

  @Before
  fun before() {
    ApplicationManager.getApplication().replaceService(
      PropertiesComponent::class.java, PropertiesComponentMock(), disposableRule.disposable)
  }

  @Test
  fun testInspectorSettingsPersisted() {
    val settings1 = InspectorRenderSettings()

    // Default values:
    assertThat(settings1.drawBorders).isTrue()
    assertThat(settings1.drawLabel).isTrue()
    assertThat(settings1.highlightColor).isEqualTo(HIGHLIGHT_DEFAULT_COLOR)

    settings1.drawBorders = true
    settings1.drawLabel = false
    settings1.highlightColor = HIGHLIGHT_COLOR_RED

    assertThat(settings1.drawBorders).isTrue()
    assertThat(settings1.drawLabel).isFalse()
    assertThat(settings1.highlightColor).isEqualTo(HIGHLIGHT_COLOR_RED)

    val settings2 = InspectorRenderSettings()
    assertThat(settings2.drawBorders).isTrue()
    assertThat(settings2.drawLabel).isFalse()
    assertThat(settings2.highlightColor).isEqualTo(HIGHLIGHT_COLOR_RED)

    settings2.drawBorders = false
    settings2.drawLabel = true
    settings2.highlightColor = HIGHLIGHT_COLOR_PURPLE

    // settings1 gets the new values
    assertThat(settings1.drawBorders).isFalse()
    assertThat(settings1.drawLabel).isTrue()
    assertThat(settings1.highlightColor).isEqualTo(HIGHLIGHT_COLOR_PURPLE)
  }

  @Test
  fun testEditorSettingsNotPersisted() {
    val settings1 = EditorRenderSettings()
    settings1.drawBorders = false
    settings1.drawLabel = false

    assertThat(settings1.drawBorders).isFalse()
    assertThat(settings1.drawLabel).isFalse()

    val settings2 = EditorRenderSettings()
    // settings2 has the default values
    assertThat(settings2.drawBorders).isTrue()
    assertThat(settings2.drawLabel).isTrue()

    settings2.drawBorders = false
    settings2.drawLabel = false

    // settings1 keeps its original values
    assertThat(settings1.drawBorders).isFalse()
    assertThat(settings1.drawLabel).isFalse()
  }
}