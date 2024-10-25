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
package com.android.tools.idea.run.activity.launch

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.ui.ComponentWithBrowseButton
import com.intellij.testFramework.ProjectRule
import com.intellij.ui.components.JBTextField
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import javax.swing.JLabel

private const val EXAMPLE_URL = "http://example.com"
private const val EXAMPLE_ACTIVITY_NAME = "com.example.MyActivity"

internal class DeepLinkConfigurableTest {

  @get:Rule
  val rule = ProjectRule()
  private val project get() = rule.project

  @Test
  fun panel() {
    val panel = DeepLinkConfigurable(project, mock()).createComponent()
    val components = panel.components
    assertThat(components).hasLength(4)

    assertThat(components[0]).isInstanceOf(JLabel::class.java)
    assertThat((components[0] as JLabel).text).isEqualTo("URL:")

    assertThat(components[1]).isInstanceOf(ComponentWithBrowseButton::class.java)
    assertThat((components[1] as ComponentWithBrowseButton<*>).childComponent).isInstanceOf(JBTextField::class.java)

    assertThat(components[2]).isInstanceOf(JLabel::class.java)
    assertThat((components[2] as JLabel).text).isEqualTo("Activity (optional):")

    assertThat(components[3]).isInstanceOf(ComponentWithBrowseButton::class.java)
    assertThat((components[3] as ComponentWithBrowseButton<*>).childComponent).isInstanceOf(JBTextField::class.java)
  }

  @Test
  fun resetFromState() {
    val configurable = DeepLinkConfigurable(project, mock())
    val panel = configurable.createComponent()
    val state = DeepLinkLaunch.State().apply {
      DEEP_LINK = EXAMPLE_URL
      ACTIVITY = EXAMPLE_ACTIVITY_NAME
    }
    configurable.resetFrom(state)

    val components = panel.components

    assertThat(components[1]).isInstanceOf(ComponentWithBrowseButton::class.java)
    assertThat((components[1] as ComponentWithBrowseButton<*>).childComponent).isInstanceOf(JBTextField::class.java)
    assertThat(((components[1] as ComponentWithBrowseButton<JBTextField>).childComponent).text).isEqualTo(EXAMPLE_URL)

    assertThat(components[3]).isInstanceOf(ComponentWithBrowseButton::class.java)
    assertThat((components[3] as ComponentWithBrowseButton<*>).childComponent).isInstanceOf(JBTextField::class.java)
    assertThat(((components[3] as ComponentWithBrowseButton<JBTextField>).childComponent).text).isEqualTo(EXAMPLE_ACTIVITY_NAME)
  }

  @Test
  fun applyTo() {
    val configurable = DeepLinkConfigurable(project, mock())
    val panel = configurable.createComponent()
    val components = panel.components

    (components[1] as ComponentWithBrowseButton<JBTextField>).childComponent.text = EXAMPLE_URL
    (components[3] as ComponentWithBrowseButton<JBTextField>).childComponent.text = EXAMPLE_ACTIVITY_NAME

    val state = DeepLinkLaunch.State()
    configurable.applyTo(state)

    assertThat(state.DEEP_LINK).isEqualTo(EXAMPLE_URL)
    assertThat(state.ACTIVITY).isEqualTo(EXAMPLE_ACTIVITY_NAME)
  }
}