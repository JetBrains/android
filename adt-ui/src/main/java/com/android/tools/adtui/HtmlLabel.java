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
package com.android.tools.adtui;

import com.intellij.ide.browsers.BrowserLauncher;
import com.intellij.ui.HyperlinkAdapter;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLDocument;
import java.awt.*;
import java.net.URI;
import java.net.URISyntaxException;

import static com.intellij.util.ui.UIUtil.getLabelFont;

public class HtmlLabel extends JEditorPane {

  public HtmlLabel() {
    addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent event) {
        if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          String uri = event.getDescription();
          try {
            BrowserLauncher.getInstance().browse(new URI(uri));
          }
          catch (URISyntaxException ignored) {
          }
        }
      }
    });
  }

  @Override
  public Dimension getMaximumSize() {
    return getPreferredSize();
  }

  public static void setUpAsHtmlLabel(@NotNull JEditorPane editorPane) {
    setUpAsHtmlLabel(editorPane, getLabelFont());
  }

  public static void setUpAsHtmlLabel(@NotNull JEditorPane editorPane, @NotNull Font font) {
    setUpAsHtmlLabel(editorPane, font, "");
  }

  public static void setUpAsHtmlLabel(@NotNull JEditorPane editorPane, @NotNull Font font, @NotNull Color c) {
    String color = String.format("#%02x%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());
    setUpAsHtmlLabel(editorPane, font, "color: " + color + ";");
  }

  private static void setUpAsHtmlLabel(@NotNull JEditorPane editorPane, @NotNull Font font, @NotNull String color) {
    editorPane.setContentType("text/html");
    editorPane.setEditable(false);
    editorPane.setOpaque(false);
    String bodyRule = "body { font-family: " + font.getFamily() + "; " + "font-size: " + font.getSize() + "pt; " + color + " } " +
                      "ol { padding-left: 0px; margin-left: 35px; margin-top: 0px; } " +
                      "ol li { margin-left: 0px; padding-left: 0px; list-style-type: decimal; }";
    ((HTMLDocument)editorPane.getDocument()).getStyleSheet().addRule(bodyRule);
  }
}
