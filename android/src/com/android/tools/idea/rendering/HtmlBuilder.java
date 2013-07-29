/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import com.android.utils.SparseArray;
import com.android.utils.XmlUtils;
import com.intellij.icons.AllIcons;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URL;

public class HtmlBuilder {
  @NotNull private final StringBuilder myStringBuilder;
  private SparseArray<Runnable> myLinkRunnables;
  private int myNextLinkId = 0;
  private String myTableDataExtra;

  public HtmlBuilder(@NotNull StringBuilder stringBuilder) {
    myStringBuilder = stringBuilder;
  }

  public HtmlBuilder() {
    myStringBuilder = new StringBuilder(100);
  }

  public HtmlBuilder openHtmlBody() {
    addHtml("<html><body>");

    return this;
  }

  public HtmlBuilder closeHtmlBody() {
    addHtml("</body></html>");

    return this;
  }

  public HtmlBuilder addHtml(@NotNull String html) {
    myStringBuilder.append(html);

    return this;
  }

  public HtmlBuilder addNbsp() {
    myStringBuilder.append("&nbsp;");

    return this;
  }

  public HtmlBuilder addNbsps(int count) {
    for (int i = 0; i < count; i++) {
      addNbsp();
    }

    return this;
  }

  public HtmlBuilder newline() {
    myStringBuilder.append("<BR/>\n");

    return this;
  }

  public HtmlBuilder addLink(@Nullable String textBefore,
                                 @NotNull String linkText,
                                 @Nullable String textAfter,
                                 @NotNull String url) {
    if (textBefore != null) {
      add(textBefore);
    }

    addLink(linkText, url);

    if (textAfter != null) {
      add(textAfter);
    }

    return this;
  }

  public HtmlBuilder addLink(@NotNull String text, @NotNull String url) {
    int begin = 0;
    int length = text.length();
    for (; begin < length; begin++) {
      char c = text.charAt(begin);
      if (Character.isWhitespace(c)) {
        myStringBuilder.append(c);
      } else {
        break;
      }
    }
    myStringBuilder.append("<A HREF=\"");
    myStringBuilder.append(url);
    myStringBuilder.append("\">");

    XmlUtils.appendXmlTextValue(myStringBuilder, text.trim());
    myStringBuilder.append("</A>");

    int end = length - 1;
    for (; end > begin; end--) {
      char c = text.charAt(begin);
      if (Character.isWhitespace(c)) {
        myStringBuilder.append(c);
      }
    }

    return this;
  }

  public HtmlBuilder add(@NotNull String text) {
    XmlUtils.appendXmlTextValue(myStringBuilder, text);

    return this;
  }

  @NotNull
  public String getHtml() {
    return myStringBuilder.toString();
  }

  public HtmlBuilder beginBold() {
    myStringBuilder.append("<B>");

    return this;
  }

  public HtmlBuilder endBold() {
    myStringBuilder.append("</B>");

    return this;
  }

  public HtmlBuilder addBold(String text) {
    beginBold();
    add(text);
    endBold();

    return this;
  }

  public HtmlBuilder beginDiv() {
    return beginDiv(null);
  }

  public HtmlBuilder beginDiv(@Nullable String cssStyle) {
    myStringBuilder.append("<div");
    if (cssStyle != null) {
      myStringBuilder.append(" style=\"");
      myStringBuilder.append(cssStyle);
      myStringBuilder.append("\"");
    }
    myStringBuilder.append('>');
    return this;
  }

  public HtmlBuilder endDiv() {
    myStringBuilder.append("</div>");
    return this;
  }

  public HtmlBuilder addHeading(String text) {
    // See om.intellij.codeInspection.HtmlComposer.addHeading
    // (which operates on StringBuffers)
    myStringBuilder.append("<font style=\"font-weight:bold; color:")
      .append(UIUtil.isUnderDarcula() ? "#A5C25C" : "#005555").append(";\">");
    add(text);
    myStringBuilder.append("</font>");

    return this;
  }

  /**
   * The JEditorPane HTML renderer creates really ugly bulleted lists; the
   * size is hardcoded to use a giant heavy bullet. So, use a definition
   * list instead.
   */
  private static final boolean USE_DD_LISTS = true;

  public HtmlBuilder beginList() {
    if (USE_DD_LISTS) {
      myStringBuilder.append("<DL>");
    } else {
      myStringBuilder.append("<UL>");
    }

    return this;
  }

  public HtmlBuilder endList() {
    if (USE_DD_LISTS) {
      myStringBuilder.append("\n</DL>");
    } else {
      myStringBuilder.append("\n</UL>");
    }

    return this;
  }

  public HtmlBuilder listItem() {
    if (USE_DD_LISTS) {
      myStringBuilder.append("\n<DD>");
      myStringBuilder.append("-&NBSP;");
    } else {
      myStringBuilder.append("\n<LI>");
    }

    return this;
  }

  public HtmlBuilder addImage(URL url, @Nullable String altText) {
    String link = "";
    try {
      link = url.toURI().toURL().toExternalForm();
    }
    catch (Throwable t) {
      // pass
    }
    myStringBuilder.append("<img src='");
    myStringBuilder.append(link);

    if (altText != null) {
      myStringBuilder.append("' alt=\"");
      myStringBuilder.append(altText);
      myStringBuilder.append("\"");
    }
    myStringBuilder.append(" />");

    return this;
  }

  private void addIcon(String relative) {
    try {
      // TODO: Find a way to do this more efficiently; not referencing assets but the corresponding
      // AllIcons constants, and loading them into HTML class loader contexts?
      URL resource = AllIcons.class.getClassLoader().getResource(relative);
      if (resource != null) {
        String src = resource.toURI().toURL().toExternalForm();
        myStringBuilder.append("<img src='");
        myStringBuilder.append(src);
        myStringBuilder.append("' width=16 height=16 border=0 />");
      }
    } catch (Throwable t) {
      // pass
    }
  }

  public HtmlBuilder addCloseIcon() {
    addIcon("/actions/closeNew.png");

    return this;
  }
  public HtmlBuilder addTipIcon() {
    addIcon("/actions/createFromUsage.png");

    return this;
  }

  public HtmlBuilder addWarningIcon() {
    addIcon("/actions/warning.png");

    return this;
  }

  public HtmlBuilder addErrorIcon() {
    addIcon("/actions/error.png");

    return this;
  }

  public HtmlBuilder beginTable(@Nullable String tdExtra) {
    myStringBuilder.append("<table>");
    myTableDataExtra = tdExtra;
    return this;
  }

  public HtmlBuilder beginTable() {
    return beginTable(null);
  }

  public HtmlBuilder endTable() {
    myStringBuilder.append("</table>");
    return this;
  }

  public HtmlBuilder beginTableRow() {
    myStringBuilder.append("<tr>");
    return this;
  }

  public HtmlBuilder endTableRow() {
    myStringBuilder.append("</tr>");
    return this;
  }

  public HtmlBuilder addTableRow(boolean isHeader, String... columns) {
    if (columns == null || columns.length == 0) {
      return this;
    }

    String tag = "t" + (isHeader ? 'h' : 'd');

    beginTableRow();
    for (String c : columns) {
      myStringBuilder.append('<');
      myStringBuilder.append(tag);
      if (myTableDataExtra != null) {
        myStringBuilder.append(' ');
        myStringBuilder.append(myTableDataExtra);
      }
      myStringBuilder.append('>');

      myStringBuilder.append(c);

      myStringBuilder.append("</");
      myStringBuilder.append(tag);
      myStringBuilder.append('>');
    }
    endTableRow();

    return this;
  }

  public HtmlBuilder addTableRow(String... columns) {
    return addTableRow(false, columns);
  }

  @NotNull
  public StringBuilder getStringBuilder() {
    return myStringBuilder;
  }
}
