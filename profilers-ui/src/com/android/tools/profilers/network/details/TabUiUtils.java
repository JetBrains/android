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
import com.android.tools.adtui.ui.HideablePanel;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.html.HTMLDocument;
import java.awt.*;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A collection of common UI constants, components, and utility methods shared throughout the
 * various {@link TabContent} subclasses.
 */
final class TabUiUtils {

  public static final int SCROLL_UNIT = JBUI.scale(10);
  public static final float FIELD_FONT_SIZE = 11.f;

  public static final int TAB_SECTION_VGAP = JBUI.scale(5);
  public static final int PAGE_VGAP = JBUI.scale(28);
  public static final int SECTION_VGAP = JBUI.scale(10);
  public static final int HGAP = JBUI.scale(22);
  public static final float TITLE_FONT_SIZE = 14.f;

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
   * Like {@link #createScrollPane(JComponent)} but this scroll pane vertical scrolling is
   * delegated to upper level scroll pane and its own vertical scroll bar is hidden.
   */
  @NotNull
  public static JBScrollPane createNestedScrollPane(@NotNull JComponent component) {
    JBScrollPane scrollPane = createScrollPane(component);
    scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
    scrollPane.addMouseWheelListener(new DelegateMouseWheelListener(scrollPane));
    return scrollPane;
  }

  /**
   * Creates a separator to visually divide areas of a panel.
   */
  @NotNull
  public static JSeparator createSeparator() {
    JSeparator separator = new JSeparator();
    separator.setForeground(UIManager.getColor("Table.gridColor"));
    return separator;
  }

  /**
   * Creates a {@link HideablePanel} with a consistent style.
   */
  @NotNull
  public static HideablePanel createHideablePanel(@NotNull String title, @NotNull JComponent content,
                                                  @Nullable JComponent northEastComponent) {
    title = String.format("<html><b>%s</b></html>", title);
    return new HideablePanel.Builder(title, content).setNorthEastComponent(northEastComponent).build();
  }

  /**
   * Create a component that shows a list of key/value pairs and some additional margins. If there
   * are no values in the map, this returns a label indicating that no data is available.
   */
  @NotNull
  public static JComponent createStyledMapComponent(@NotNull Map<String, String> map) {
    JComponent component = createMapComponent(map);
    if (component instanceof JTextPane) {
      ((HTMLDocument)((JTextPane)component).getDocument()).getStyleSheet().addRule("p { margin: 5 0 5 0; }");
    }
    return component;
  }

  /**
   * Create a component that shows a list of key/value pairs. If there are no values in the map,
   * this returns a label indicating that no data is available.
   */
  @NotNull
  public static JComponent createMapComponent(@NotNull Map<String, String> argsMap) {
    if (argsMap.isEmpty()) {
      return new JLabel("No data available");
    }

    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append("<html>");
    for (Map.Entry<String, String> entry : argsMap.entrySet()) {
      stringBuilder.append("<p><nobr><b>").append(entry.getKey()).append(":&nbsp&nbsp</b></nobr>");
      stringBuilder.append("<span>").append(entry.getValue()).append("</span></p>");
    }
    stringBuilder.append("</html>");
    return createTextPane(stringBuilder.toString());
  }

  /**
   * Wraps a string with a read-only text panel, allowing users to select and copy the text if they
   * want to.
   */
  @NotNull
  private static JTextPane createTextPane(String text) {
    JTextPane textPane = new JTextPane();
    textPane.setContentType("text/html");
    textPane.setBackground(null);
    textPane.setBorder(null);
    textPane.setEditable(false);
    textPane.setText(text);
    Font labelFont = UIManager.getFont("Label.font");
    String rule = "body { font-family: " + labelFont.getFamily() + "; font-size: " + FIELD_FONT_SIZE + "pt; }";
    ((HTMLDocument)textPane.getDocument()).getStyleSheet().addRule(rule);
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
    c.setFont(c.getFont().deriveFont(Font.PLAIN, FIELD_FONT_SIZE));
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

  /**
   * Delegates given scroll pane's vertical wheel event to the parent scroll pane.
   */
  static class DelegateMouseWheelListener implements MouseWheelListener {
    @NotNull private final JScrollPane myScrollPane;
    @Nullable private JScrollPane myParentScrollPane;
    private int myLastScrollOffset = 0;

    DelegateMouseWheelListener(@NotNull JScrollPane scrollPane) {
      myScrollPane = scrollPane;
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
      setParentScrollPane();
      if (myParentScrollPane == null) {
        myScrollPane.removeMouseWheelListener(this);
        return;
      }

      JScrollBar scrollBar = myScrollPane.getVerticalScrollBar();
      int terminalValue = e.getWheelRotation() < 0 ? 0 : scrollBar.getMaximum() - scrollBar.getVisibleAmount();
      if (scrollBar.getValue() == terminalValue && myLastScrollOffset == terminalValue) {
        // Clone the event since this pane already consumes it.
        myParentScrollPane.dispatchEvent(clone(myParentScrollPane, e));
      }
      myLastScrollOffset = scrollBar.getValue();
    }

    private void setParentScrollPane() {
      if (myParentScrollPane == null) {
        Component parent = myScrollPane.getParent();
        while (parent != null && !(parent instanceof JScrollPane)) {
          parent = parent.getParent();
        }
        myParentScrollPane = (JScrollPane) parent;
      }
    }

    private static MouseWheelEvent clone(JScrollPane source, MouseWheelEvent e) {
      return new MouseWheelEvent(source,
                                 e.getID(),
                                 e.getWhen(),
                                 e.getModifiers(),
                                 e.getX(),
                                 e.getY(),
                                 e.getClickCount(),
                                 e.isPopupTrigger(),
                                 e.getScrollType(),
                                 e.getScrollAmount(),
                                 e.getWheelRotation());
    }
  }
}
