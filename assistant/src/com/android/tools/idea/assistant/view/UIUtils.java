/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.google.common.annotations.VisibleForTesting;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.HTMLEditorKitBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import java.net.URL;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static com.intellij.util.ui.UIUtil.CONTRAST_BORDER_COLOR;


/**
 * Centralized values and common, granular, UI properties. Scope of class
 * should be constrained to matters of color, font, padding. More complex
 * UI related functionality should live in more specific code.
 *
 * NOTE: Do not use any plain Color instances as they do not work across
 * theme change properly. This includes things like com.intellij.ui.UI.getColor("key").
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
   * Default color for links, whether they are internal or external links.
   * Colors are using IntelliJ theme colors for links.
   */
  private static final Color OFFSITE_LINK_COLOR = JBUI.CurrentTheme.Link.Foreground.ENABLED;

  /**
   * Panel background color. Patterned after tree text background.
   */
  private static final Color BACKGROUND_COLOR = new JBColor(0xFFFFFF, 0xFF3C3F41);

  /**
   * Hover color for components using {@code BACKGROUND_COLOR}.
   */
  private static final Color BACKGROUND_HOVER_COLOR = new JBColor(0xFFE8E8E8, 0x000000);

  /**
   * Do not reference, this is to address skew across OS's and should only be used with {@code AS_STANDARD_BACKGROUND_COLOR}.
   */
  private static final Color CURRENT_BG_COLOR = new JPanel().getBackground();

  /**
   * "Normal" background color as is found on a new JPanel background.
   */
  private static final Color AS_STANDARD_BACKGROUND_COLOR =
    new JBColor(UIUtil.isUnderDarcula() ? 0xFFE8E8E8 : CURRENT_BG_COLOR.getRGB(), 0xFF3D3F41);

  public static Color getBackgroundColor() {
    return BACKGROUND_COLOR;
  }

  public static Color getBackgroundHoverColor() {
    return BACKGROUND_HOVER_COLOR;
  }

  public static Color getSeparatorColor() {
    return CONTRAST_BORDER_COLOR;
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
   * TODO: Determine how to respond to theme change such that colors are updated. Currently this involves link colors as well as code
   * block colors.
   *
   * @param pane        The element to set the html content on.
   * @param content     The html body content, excluding the <body></body> tags.
   * @param css         Extra css to add. Example ".testClass { color: red}\n.anotherClass { border: 1px solid blue}".
   * @param headContent Extra header content to add. Example "<title>My Favorite!!</title>".
   */
  public static void setHtml(JEditorPane pane, String content, String css, String headContent) {
    pane.setEditorKit(HTMLEditorKitBuilder.simple());
    // It's assumed that markup is for display purposes in our context.
    pane.setEditable(false);
    // Margins should be handled by the css in this case.
    pane.setMargin(new Insets(0, 0, 0, 0));
    pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
    // Enable links opening in the default browser.
    pane.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE);

    pane.setText(getHtml(content, "a, a:visited, a:active { color: " + getCssColor(OFFSITE_LINK_COLOR) + "; }" + css, headContent));
  }

  public static String getHtml(@NotNull String content) {
    return getHtml(content, null, null);
  }

  public static String getHtml(@NotNull String content, @Nullable/*not required*/ String css) {
    return getHtml(content, css, null);
  }

  /**
   * Gets valid/consistent html markup for use in Swing component display.
   * NOTE: Use {@code setHtml} when working with editors.
   *
   * @param content     The html body content, excluding the <body></body> tags.
   * @param css         Extra css to add. Example ".testClass { color: red}\n.anotherClass { border: 1px solid blue}".
   * @param headContent Extra header content to add. Example "<title>My Favorite!!</title>".
   */
  public static String getHtml(@NotNull String content,
                               @Nullable/*not required*/ String css,
                               @Nullable/*not required*/ String headContent) {
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
    // * Colorizes <code>, resets the font size and uses a more legible monospace if available.
    String text = "<html><head><style>body { font-family: " + defaultFont.getFamily() + "; margin: 0px; } " +
                  "ol {margin: 0 0 0 20px; } ul {list-style-type: circle; margin: 0 0 0 20px; } " +
                  "li {margin: 0 0 10px 10px; } " +
                  "code { font-family: 'Roboto Mono', 'Courier New', monospace; color: " + getCssColor(CODE_COLOR) + "; font-size: 1em; }" +
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
    return text;
  }

  /**
   * Filter the given html content and replace local relative paths for images
   * by absolute paths, to work around a limitation of JEditorPane HTML's parser.
   *
   * @param resourceClass class to use for getting resources.
   * @param html The html to search for resource references, residing in the same jar as {@code resourceClass}
   * @return processed html content if local images are used.
   */
  public static @Nullable String addLocalHTMLPaths(@NotNull Class<?> resourceClass, @Nullable String html) {
    if (html != null) {
      String localImage = findLocalImage(html);
      if (localImage != null) {
        URL url = resourceClass.getResource("/" + localImage);
        if (url != null) {
          return addLocalHTMLPaths(html, url, localImage);
        }
      }
    }
    return html;
  }

  @VisibleForTesting
  @Nullable
  static String findLocalImage(@NotNull String html) {
    // find the first image that is local and use it as resource
    String pattern = "<img src=\"/";
    int index = html.indexOf(pattern);
    if (index >= 0) {
      index += pattern.length();
      String image = html.substring(index, html.indexOf('\"', index));
      if (!image.isEmpty()) {
        return image;
      }
    }
    return null;
  }

  @VisibleForTesting
  @NotNull
  static String addLocalHTMLPaths(@NotNull String html, @NotNull URL url, @NotNull String localImage) {
    String baseUrl = url.toExternalForm().replace(localImage, "");
    String replacementPattern = "<img src=\"" + baseUrl;
    return html.replaceAll("<img src=\"/", replacementPattern);
  }
}
