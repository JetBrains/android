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

import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.ui.BreakWordWrapHtmlTextPane
import com.android.tools.adtui.ui.HideablePanel
import com.android.tools.idea.appinspection.inspectors.network.view.constants.STANDARD_FONT
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBUI.scale
import java.awt.Component
import java.awt.Font
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
  val htmlTitle = String.format("<html><b>%s</b></html>", title)
  return HideablePanel.Builder(htmlTitle, content)
    .setNorthEastComponent(northEastComponent)
    .setPanelBorder(JBEmptyBorder(10, 0, 0, 0))
    .setContentBorder(JBEmptyBorder(10, 12, 0, 0))
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
  val textPane = BreakWordWrapHtmlTextPane()
  textPane.text = buildString {
    append("<html>")
    map.toSortedMap(String.CASE_INSENSITIVE_ORDER)
      .forEach { (key, value) ->
        append(String.format("<p><b>%s</b>:&nbsp;<span>%s</span></p>", key, value))
      }
    append("</html>")
  }
  return textPane
}

/**
 * Adjusts the font of the target component to a consistent default size.
 */
fun adjustFont(c: Component) {
  if (c.font == null) {
    // Some Swing components simply have no font set - skip over them
    return
  }
  c.font = c.font.deriveFont(Font.PLAIN, STANDARD_FONT.size2D)
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