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
package com.android.tools.idea.appinspection.inspectors.network.view.details

import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.common.borderLight
import com.android.tools.adtui.ui.HideablePanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.scale
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.lang.Integer.max
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

val SCROLL_UNIT = scale(10)

// Padding to be aligned with the tab title on the left.
const val HORIZONTAL_PADDING = 15

val TAB_SECTION_VGAP = scale(5)

val PAGE_VGAP = scale(28)

val SECTION_VGAP = scale(10)

const val SECTION_TITLE_HEADERS = "Headers"

const val REGEX_TEXT = "Regex"

/**
 * Creates a panel with a vertical flowing layout and a consistent style.
 */
fun createVerticalPanel(verticalGap: Int): JPanel {
  return JPanel(VerticalFlowLayout(0, verticalGap))
}

/**
 * Creates a scroll panel that wraps a target component with a consistent style.
 */
fun createScrollPane(component: JComponent): JBScrollPane {
  val scrollPane = JBScrollPane(component)
  scrollPane.verticalScrollBar.unitIncrement = SCROLL_UNIT
  scrollPane.horizontalScrollBar.unitIncrement = SCROLL_UNIT
  return scrollPane
}

/**
 * Like [.createScrollPane] but for components you only want to support
 * vertical scrolling for. This is useful if scroll panes are nested within scroll panes.
 */
fun createVerticalScrollPane(component: JComponent): JBScrollPane {
  val scrollPane: JBScrollPane = createScrollPane(component)
  scrollPane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
  return scrollPane
}

/**
 * Creates a [HideablePanel] with a consistent style.
 */
fun createHideablePanel(
  title: String, content: JComponent,
  northEastComponent: JComponent?
): HideablePanel {
  return HideablePanel.Builder(title, content)
    .setNorthEastComponent(northEastComponent)
    .setPanelBorder(JBUI.Borders.empty(10, 0, 0, 0))
    .setContentBorder(JBUI.Borders.empty(10, 12, 0, 0))
    .setIsTitleBold(true)
    .build()
}

/**
 * Create a component that shows a list of key/value pairs and some additional margins. If there
 * are no values in the map, this returns a label indicating that no data is available.
 */
fun createStyledMapComponent(map: Map<String, String>): JComponent {
  if (map.isEmpty()) {
    return JLabel("Not available")
  }
  val scaled5 = scale(5)
  val emptyBorder = JBUI.Borders.empty(scaled5, 0, scaled5, scaled5)
  val mainJPanel = JPanel()
  mainJPanel.layout = BoxLayout(mainJPanel, BoxLayout.Y_AXIS)
  map.toSortedMap(String.CASE_INSENSITIVE_ORDER)
    .forEach { (key, value) ->
      val currJPanel = JPanel().apply {
        layout = BorderLayout(scaled5, scaled5)
        add(NoWrapBoldLabel("$key:").apply {
          border = emptyBorder
          verticalAlignment = JLabel.TOP
        }, BorderLayout.LINE_START)
        add(WrappedTextArea(value).apply {
          border = emptyBorder
          background = null
          isOpaque = false
          isEditable = false
        }, BorderLayout.CENTER)
        alignmentX = JPanel.LEFT_ALIGNMENT
        alignmentY = JPanel.TOP_ALIGNMENT
      }
      mainJPanel.add(currJPanel)
    }
  mainJPanel.alignmentX = JPanel.LEFT_ALIGNMENT
  return mainJPanel
}

/**
 * Find a component by its name. If duplicate names are found, this will throw an exception.
 *
 * This utility method is meant to be used indirectly only for test purposes - names can be a
 * convenient way to expose child elements to tests to assert their state.
 *
 * Non-unique names throw an exception to help catch accidental copy/paste errors when
 * initializing names.
 */
fun findComponentWithUniqueName(root: JComponent, name: String): JComponent? {
  val matches = TreeWalker(root).descendants()
    .filter { component -> name == component.name }
    .toList()
  check(matches.size <= 1) { "More than one component found with the name: $name" }
  return if (matches.size == 1) matches[0] as JComponent else null
}

/**
 * Create a component that shows a category [name] with [TitledSeparator] and a list of following
 * [entryComponents].
 */
fun createCategoryPanel(
  name: String?,
  vararg entryComponents: Pair<JComponent, JComponent>
): JPanel {
  val panel = JPanel(VerticalLayout(6))
  if (name != null) {
    val headingPanel = TitledSeparator(name)
    headingPanel.minimumSize = Dimension(0, 34)
    panel.add(headingPanel)
  }
  val bodyPanel = JPanel(TabularLayout("Fit,*")).apply { border = JBUI.Borders.empty() }

  for ((index, components) in entryComponents.withIndex()) {
    val (component1, component2) = components
    val component2Panel = JPanel(BorderLayout()).apply {
      border = JBUI.Borders.empty(5, 10)

      add(component2, BorderLayout.CENTER)
    }

    bodyPanel.add(component1, TabularLayout.Constraint(index, 0))
    bodyPanel.add(component2Panel, TabularLayout.Constraint(index, 1))
  }
  panel.add(bodyPanel)
  return panel
}

/**
 * Create a [JBTextField] with preferred [width] and focus lost listener.
 */
fun createTextField(
  initialText: String?,
  hintText: String,
  name: String? = null,
  focusLost: (String) -> Unit = {}
) = JBTextField(initialText).apply {
  emptyText.appendText(hintText)
  // Adjust TextField size to contain hintText properly
  preferredSize = Dimension(max(preferredSize.width, emptyText.preferredSize.width + font.size),
                            max(preferredSize.height, emptyText.preferredSize.height))
  border = BorderFactory.createLineBorder(borderLight)
  this.name = name
  addFocusListener(object : FocusAdapter() {
    override fun focusLost(e: FocusEvent) {
      focusLost(text.trim())
    }
  })
}

fun createWarningLabel(warningText: String, labelName: String?) = JBLabel(StudioIcons.Common.WARNING).apply {
  isVisible = false
  border = JBUI.Borders.emptyLeft(5)
  toolTipText = warningText
  name = labelName
}

fun createPanelWithTextFieldAndWarningLabel(
  textField: JBTextField,
  warningLabel: JBLabel
) = JPanel(TabularLayout("*,Fit")).apply {
  add(textField, TabularLayout.Constraint(0, 0))
  add(warningLabel, TabularLayout.Constraint(0, 1))
}

/**
 * Returns a [JPanel] of a [JBCheckBox] with Regex icon and label.
 */
fun JBCheckBox.withRegexLabel(): JPanel {
  val label = JBLabel(REGEX_TEXT)
  label.icon = AllIcons.Actions.RegexHovered
  label.disabledIcon = AllIcons.Actions.Regex
  label.iconTextGap = 0
  addPropertyChangeListener {
    label.isEnabled = this@withRegexLabel.isEnabled
  }
  return JPanel(HorizontalLayout(0)).apply {
    add(this@withRegexLabel)
    add(label)
  }
}

/**
 * This is a label with bold font and does not wrap.
 */
class NoWrapBoldLabel(text: String) : JBLabel(text) {
  init {
    withFont(JBFont.label().asBold())
  }
  override fun setFont(ignored: Font?) {
    // ignore the input font and explicitly set the label font provided by JBFont
    super.setFont(JBFont.label().asBold())
  }
}

/**
 * This is a text area with line and word wrap and plain text.
 */
class WrappedTextArea(text: String) : JBTextArea(text) {
  init {
    font = JBFont.label().asPlain()
    lineWrap = true
    wrapStyleWord = true
  }

  override fun setFont(ignored: Font?) {
    // ignore the input font and explicitly set the label font provided by JBFont
    super.setFont(JBFont.label().asPlain())
  }
}
