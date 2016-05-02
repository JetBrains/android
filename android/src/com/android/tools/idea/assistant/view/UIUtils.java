/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.assistant.view;

import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.JBColor;
import com.intellij.ui.UI;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;


/**
 * Centralized values and common, granular, UI properties. Scope of class
 * should be constrained to matters of color, font, padding. More complex
 * UI related functionality should live in more specific code.
 *
 * TODO: Add font defaults.
 */
public class UIUtils {

  /**
   * Foreground color for positive indication of results.
   * TODO: Currently mirrors the quoted string editor colors. Directly leveraging something else that shares semantics would be ideal.
   * NOTE: The colors may be found at Settings -> Editor -> Colors & Fonts -> Custom [String].
   */
  private static final Color SUCCESS_COLOR = new JBColor(0x008000, 0x6A8759);

  /**
   * Foreground color for negative indication of results.
   * TODO: Currently mirrors the keyword (4th variant) editor colors. Directly leveraging something else that shares semantics would be
   * ideal.
   * NOTE: The colors may be found at Settings -> Editor -> Colors & Fonts -> Custom [Keyword4].
   */
  private static final Color FAILURE_COLOR = new JBColor(0x660000, 0xC93B48);

  /**
   * Default color for inline <code></code> tags. Currently set to Material Teal 600/200.
   */
  private static final Color CODE_COLOR = new JBColor(0x00897B, 0x80CBC4);

  /**
   * Secondary text color to be used with non-emphasized content such as subtitles.
   * Colors are Material Grey 600/400.
   */
  private static final Color SECONDARY_COLOR = new JBColor(0x757575, 0xBDBDBD);

  /**
   * Default color for links. These are treated differently to make it clear that they are not the same as internal links.
   * Colors are Material Blue Grey 400/200.
   */
  private static final Color OFFSITE_LINK_COLOR = new JBColor(0x78909C, 0xB0BEC5);

  /**
   * "Normal" background color.
   */
  private static final Color AS_STANDARD_BACKGROUND_COLOR = new JPanel().getBackground();

  // TODO: Find or create a better background reference, we're not a tree but
  // this does currently match our desired color treatment. This is pulled
  // from the Maven Projects side panel.
  public static Color getBackgroundColor() {
    return UIUtil.getTreeTextBackground();
  }

  public static Color getSeparatorColor() {
    return UI.getColor("panel.separator.color");
  }

  // TODO: This blue seems a bit washed out, confirm what color we should
  // leverage.
  public static Color getLinkColor() {
    return UI.getColor("link.foreground");
  }

  public static Color getSuccessColor() {
    return SUCCESS_COLOR;
  }

  public static Color getFailureColor() {
    return FAILURE_COLOR;
  }

  public static Color getSecondaryColor() {
    return SECONDARY_COLOR;
  }

  public static Color getAsStandardBackgroundColor() {
    return AS_STANDARD_BACKGROUND_COLOR;
  }

  /**
   * Gets a CSS string representation of a given color. Useful for mapping themed colors to css rules in html blocks.
   */
  public static String getCssColor(Color color) {
    return "rgb(" + color.getRed() + ", " + color.getGreen() + ", " + color.getBlue() + ")";
  }

  public static GridBagConstraints getVerticalGlueConstraints(int gridy) {
    return getVerticalGlueConstraints(gridy, 1);
  }

  /**
   * Gets a common set of constraints for vertical glue to fill remaining space. New instances created as caller may need to override some
   * values.
   */
  public static GridBagConstraints getVerticalGlueConstraints(int gridy, int gridwidth) {
    GridBagConstraints glueConstraints = new GridBagConstraints();
    glueConstraints.gridx = 0;
    glueConstraints.gridy = gridy;
    glueConstraints.gridwidth = gridwidth;
    glueConstraints.gridheight = 1;
    glueConstraints.weightx = 0;
    glueConstraints.weighty = 1;
    glueConstraints.anchor = GridBagConstraints.NORTH;
    glueConstraints.fill = GridBagConstraints.BOTH;
    glueConstraints.insets = new Insets(0, 0, 0, 0);
    glueConstraints.ipadx = 0;
    glueConstraints.ipady = 0;
    return glueConstraints;
  }

  public static void setHtml(JEditorPane pane, String content) {
    setHtml(pane, content, null, null);
  }


  public static void setHtml(JEditorPane pane, String content, String css) {
    setHtml(pane, content, css, null);
  }

  /**
   * Sets html content on a {@code JTextPane} with default properties and convenience support for css and headers.
   *
   * @param pane        The element to set the html content on.
   * @param content     The html body content, excluding the <body></body> tags.
   * @param css         Extra css to add. Example ".testClass { color: red}\n.anotherClass { border: 1px solid blue}".
   * @param headContent Extra header content to add. Example "<title>My Favorite!!</title>".
   */
  public static void setHtml(JEditorPane pane, String content, String css, String headContent) {
    pane.setContentType("text/html");
    // It's assumed that markup is for display purposes in our context.
    pane.setEditable(false);
    // Margins should be handled by the css in this case.
    pane.setMargin(new Insets(0, 0, 0, 0));
    pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
    // Enable links opening in the default browser.
    pane.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE);

    // Use the label font as the default for markup so that it appears more consistent with non-html elements.
    // TODO: Determine if the label font is the ideal font choice.
    Font defaultFont = new JBLabel().getFont();

    // NOTE: HTMLBuilder has poor support for granular/structured css and header manipulation. The rest of its methods are just a more
    // verbose way of emitting string constants like "<html>" which isn't useful. Opting to directly manipulate markup instead.

    // See https://docs.oracle.com/javase/8/docs/api/javax/swing/text/html/CSS.html for supported css.
    // Summary of changes:
    // * Use standard label font family so this is more in sync with labels and text based panels.
    // * Defeat default list item treatment. It's hard coded in ListView as a poorly rendered 8px disc + large margins.
    // * Add bottom margins to list items for legibility.
    // * Colorizes <code>.
    String text = "<html><head><style>body { font-family: " + defaultFont.getFamily() + "; margin: 0px; } " +
                  "ol {margin: 0 0 0 20px; } ul {list-style-type: circle; margin: 0 0 0 20px; } " +
                  "li {margin: 0 0 10px 10px; } code { color: " + getCssColor(CODE_COLOR) + "; }" +
                  "a, a:visited, a:active { color: " + getCssColor(OFFSITE_LINK_COLOR) + "; }" +
                  // In some scenario, containers render contents at 0 height on theme change. Override this class to have 1px of top margin
                  // in that event and accommodate the size change in your document.
                  ".as-shim { margin: 0 0 0 0; }";
    if (css != null) {
      text += "\n" + css;
    }
    text += "</style>";
    if (headContent != null) {
      text += "\n" + headContent;
    }
    text += "</head><body><div class=\"as-shim\">" + content + "</div></body></html>";
    pane.setText(text);
  }

}
