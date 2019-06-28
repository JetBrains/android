/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers.network.details;

import com.android.tools.adtui.TreeWalker;
import com.android.tools.adtui.ui.BreakWordWrapHtmlTextPane;
import com.android.tools.adtui.ui.HideablePanel;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBEmptyBorder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.android.tools.profilers.ProfilerFonts.STANDARD_FONT;

/**
 * A collection of common UI constants, components, and utility methods shared throughout the
 * various {@link TabContent} subclasses.
 */
final class TabUiUtils {

  public static final int SCROLL_UNIT = JBUIScale.scale(10);
  // Padding to be aligned with the tab title on the left.
  public static final int HORIZONTAL_PADDING = 15;

  // TODO(b/109661512): Move vgap scale into TabularLayout
  public static final int TAB_SECTION_VGAP = JBUIScale.scale(5);
  public static final int PAGE_VGAP = JBUIScale.scale(28);
  public static final int SECTION_VGAP = JBUIScale.scale(10);

  public static final String SECTION_TITLE_HEADERS = "Headers";


  private TabUiUtils() {
  }

  /**
   * Creates a panel with a vertical flowing layout and a consistent style.
   */
  @NotNull
  public static JPanel createVerticalPanel(int verticalGap) {
    return new JPanel(new VerticalFlowLayout(0, verticalGap));
  }

  /**
   * Creates a scroll panel that wraps a target component with a consistent style.
   */
  @NotNull
  public static JBScrollPane createScrollPane(@NotNull JComponent component) {
    JBScrollPane scrollPane = new JBScrollPane(component);
    scrollPane.getVerticalScrollBar().setUnitIncrement(SCROLL_UNIT);
    scrollPane.getHorizontalScrollBar().setUnitIncrement(SCROLL_UNIT);
    return scrollPane;
  }

  /**
   * Like {@link #createScrollPane(JComponent)} but for components you only want to support
   * vertical scrolling for. This is useful if scroll panes are nested within scroll panes.
   */
  @NotNull
  public static JBScrollPane createVerticalScrollPane(@NotNull JComponent component) {
    JBScrollPane scrollPane = createScrollPane(component);
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    return scrollPane;
  }

  /**
   * Creates a {@link HideablePanel} with a consistent style.
   */
  @NotNull
  public static HideablePanel createHideablePanel(@NotNull String title, @NotNull JComponent content,
                                                  @Nullable JComponent northEastComponent) {
    title = String.format("<html><b>%s</b></html>", title);
    return new HideablePanel.Builder(title, content)
      .setNorthEastComponent(northEastComponent)
      .setPanelBorder(new JBEmptyBorder(10, 0, 0, 0))
      .setContentBorder(new JBEmptyBorder(10, 12, 0, 0))
      .build();
  }

  /**
   * Create a component that shows a list of key/value pairs and some additional margins. If there
   * are no values in the map, this returns a label indicating that no data is available.
   */
  @NotNull
  public static JComponent createStyledMapComponent(@NotNull Map<String, String> map) {
    if (map.isEmpty()) {
      return new JLabel("Not available");
    }

    JTextPane textPane = new BreakWordWrapHtmlTextPane();
    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append("<html>");
    for (Map.Entry<String, String> entry : map.entrySet()) {
      stringBuilder.append(String.format("<p><b>%s</b>:&nbsp;<span>%s</span></p>", entry.getKey(), entry.getValue()));
    }
    stringBuilder.append("</html>");
    textPane.setText(stringBuilder.toString());
    return textPane;
  }

  /**
   * Adjusts the font of the target component to a consistent default size.
   */
  public static void adjustFont(@NotNull Component c) {
    if (c.getFont() == null) {
      // Some Swing components simply have no font set - skip over them
      return;
    }
    c.setFont(c.getFont().deriveFont(Font.PLAIN, STANDARD_FONT.getSize2D()));
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
  @Nullable
  public static JComponent findComponentWithUniqueName(@NotNull JComponent root, @NotNull String name) {
    List<Component> matches = new TreeWalker(root).descendantStream().filter(c -> name.equals(c.getName())).collect(Collectors.toList());
    if (matches.size() > 1) {
      throw new IllegalStateException("More than one component found with the name: " + name);
    }

    return (matches.size() == 1) ? (JComponent)matches.get(0) : null;
  }
}
