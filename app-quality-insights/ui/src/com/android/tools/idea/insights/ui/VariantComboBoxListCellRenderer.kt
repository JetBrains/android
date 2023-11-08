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
package com.android.tools.idea.insights.ui

import com.android.tools.idea.insights.IssueVariant
import com.intellij.ui.components.JBLabel
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.SwingConstants

/** Represents a row to be rendered in the variants dropdown. */
sealed interface Row {
  fun getRendererComponent(): Component
}

/** A row containing variant information. */
data class VariantRow(
  val name: String,
  val eventCount: Long,
  val userCount: Long,
  val issueVariant: IssueVariant?
) : Row {
  override fun getRendererComponent(): Component {
    textLabel.update(name, null)
    eventCountLabel.update(
      eventCount.formatNumberToPrettyString(),
      StudioIcons.AppQualityInsights.ISSUE
    )
    userCountLabel.update(
      userCount.formatNumberToPrettyString(),
      StudioIcons.LayoutEditor.Palette.QUICK_CONTACT_BADGE
    )
    return rendererComponent
  }

  companion object {
    private val textLabel = JBLabel()
    private val eventCountLabel = JBLabel()
    private val userCountLabel = JBLabel()
    private val rendererComponent =
      JPanel().apply {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        isOpaque = false
        add(textLabel)
        add(Box.createHorizontalGlue())
        add(eventCountLabel)
        add(Box.createHorizontalStrut(4))
        add(userCountLabel)
      }

    private fun JBLabel.update(value: String, icon: Icon?) {
      removeAll()
      this.icon = icon
      toolTipText = value
      text = value
    }
  }
}

/** Shown when the dropdown has no variant information. */
data class DisabledTextRow(val text: String) : Row {
  override fun getRendererComponent(): Component {
    textComponent.text = text
    return textComponent
  }

  companion object {
    private val textComponent =
      JBLabel().apply {
        isEnabled = false
        isOpaque = false
      }
  }
}

/** Represents the header of the variants list. */
object HeaderRow : Row {
  private val rendererComponent =
    JPanel(BorderLayout()).apply {
      add(
        JBLabel("VARIANTS").apply {
          isEnabled = false
          isOpaque = false
        },
        BorderLayout.WEST
      )
      add(
        JBLabel("IMPACT").apply {
          isEnabled = false
          isOpaque = false
          horizontalAlignment = SwingConstants.RIGHT
        },
        BorderLayout.EAST
      )
      isOpaque = false
      isEnabled = false
    }

  override fun getRendererComponent() = rendererComponent
}
