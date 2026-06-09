/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.property.panel.impl.ui

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.tools.adtui.stdui.CommonHyperLinkLabel
import com.android.tools.property.panel.api.TableExpansionState
import com.android.tools.property.panel.impl.model.LinkPropertyEditorModel
import com.android.tools.property.panel.impl.model.TextFieldPropertyEditorModel
import com.android.tools.property.panel.impl.model.util.FakeLinkPropertyItem
import com.android.tools.property.panel.impl.model.util.FakePropertyItem
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.testFramework.ApplicationRule
import com.intellij.ui.components.JBLabel
import java.awt.BorderLayout
import org.junit.Rule
import org.junit.Test

class PropertyLabelAndLinkTest {
  @get:Rule val rule = ApplicationRule()

  @Test
  fun testPropertyLabelHtmlDisabling() {
    val property = FakePropertyItem(ANDROID_URI, ATTR_ID, "<html>evil</html>")
    val model = TextFieldPropertyEditorModel(property, editable = false)
    val label = PropertyLabel(model)

    // Default state (NORMAL) -> html should be disabled
    model.tableExpansionState = TableExpansionState.NORMAL
    assertThat(label.getClientProperty("html.disable")).isEqualTo(true)
    assertThat(label.text).isEqualTo("<html>evil</html>")

    // Expanded state -> html should be enabled (null)
    model.tableExpansionState = TableExpansionState.EXPANDED_CELL_FOR_POPUP
    assertThat(label.getClientProperty("html.disable")).isNull()
    assertThat(label.text).isEqualTo("<html><nobr>&lt;html&gt;evil&lt;/html&gt;</nobr></html>")
  }

  @Test
  fun testPropertyLinkHtmlDisabling() {
    val action =
      object : AnAction() {
        override fun actionPerformed(e: AnActionEvent) {}
      }
    val property = FakeLinkPropertyItem(ANDROID_URI, ATTR_ID, "<html>evil-value</html>", action)
    // We also need link text to be HTML-like
    property.link.templatePresentation.text = "<html>evil-link</html>"

    val model = LinkPropertyEditorModel(property)
    val propertyLink = PropertyLink(model)

    val layout = propertyLink.layout as BorderLayout
    val label = layout.getLayoutComponent(BorderLayout.WEST) as JBLabel
    val link = layout.getLayoutComponent(BorderLayout.CENTER) as CommonHyperLinkLabel

    // Default state (NORMAL) -> html should be disabled on both
    model.tableExpansionState = TableExpansionState.NORMAL
    assertThat(label.getClientProperty("html.disable")).isEqualTo(true)
    assertThat(link.getClientProperty("html.disable")).isEqualTo(true)
    assertThat(label.text).isEqualTo("<html>evil-value</html>")
    assertThat(link.text).isEqualTo("<html>evil-link</html>")

    // Expanded state -> html should be enabled on both
    model.tableExpansionState = TableExpansionState.EXPANDED_CELL_FOR_POPUP
    assertThat(label.getClientProperty("html.disable")).isNull()
    assertThat(link.getClientProperty("html.disable")).isNull()
    assertThat(label.text).isEqualTo("<html><nobr>&lt;html&gt;evil-value&lt;/html&gt;</nobr></html>")
    assertThat(link.text).isEqualTo("<html><nobr><u>&lt;html&gt;evil-link&lt;/html&gt;</u></nobr></html>")
  }

  @Test
  fun testExpandableLabelHtmlDisabling() {
    val label = ExpandableLabel()
    label.actualText = "<html>evil</html>"

    // Default state (showEllipsis = true) -> html should be disabled
    assertThat(label.getClientProperty("html.disable")).isEqualTo(true)
    assertThat(label.text).isEqualTo("<html>evil</html>")

    // Use reflection to set showEllipsis to false (simulating expansion)
    val setter = ExpandableLabel::class.java.getDeclaredMethod("setShowEllipsis", Boolean::class.java)
    setter.isAccessible = true
    setter.invoke(label, false)

    // Expanded state -> html should be enabled (null)
    assertThat(label.getClientProperty("html.disable")).isNull()
    assertThat(label.text).isEqualTo("<html><nobr>&lt;html&gt;evil&lt;/html&gt;</nobr></html>")
  }
}
