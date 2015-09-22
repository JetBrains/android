/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.strings;

import com.google.common.escape.Escaper;
import com.google.common.escape.Escapers;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.xml.CommonXmlStrings;
import org.jetbrains.annotations.NotNull;

final class XmlTagUtils {
  // @formatter:off
  @NotNull private static final Escaper ESCAPER = Escapers.builder()
    .addEscape('\'', "\\'")
    .build();
  // @formatter:on

  private XmlTagUtils() {
  }

  /**
   * Escapes apostrophes (') in XmlText children with backslashes. Recurses into XmlTag children. Does not escape CDATA.
   */
  static void escape(@NotNull XmlTag tag) {
    for (Object child : tag.getValue().getChildren()) {
      if (child instanceof XmlText) {
        XmlText text = (XmlText)child;

        if (!text.getText().contains(CommonXmlStrings.CDATA_START)) {
          text.setValue(ESCAPER.escape(text.getValue()));
        }
      }
      else if (child instanceof XmlTag) {
        escape((XmlTag)child);
      }
    }
  }

  /**
   * Unescapes escaped apostrophes (\') in XmlText children with {@link String#replaceAll(String, String) String.replaceAll}. Recurses into
   * XmlTag children. Does not unescape CDATA.
   *
   * @return the escaped text
   */
  @NotNull
  static String unescape(@NotNull XmlTag tag) {
    StringBuilder builder = new StringBuilder();
    unescape(builder, tag);

    return builder.toString();
  }

  private static void unescape(@NotNull StringBuilder builder, @NotNull XmlTag tag) {
    for (Object child : tag.getValue().getChildren()) {
      if (child instanceof XmlText) {
        String text = ((XmlText)child).getText();

        if (!text.contains(CommonXmlStrings.CDATA_START)) {
          text = text.replaceAll("\\\\'", "'");
        }

        builder.append(text);
      }
      else if (child instanceof XmlTag) {
        XmlTag childTag = (XmlTag)child;
        String name = childTag.getName();

        builder.append('<');
        builder.append(name);
        appendAttributes(builder, childTag);
        builder.append('>');

        unescape(builder, childTag);

        builder.append("</");
        builder.append(name);
        builder.append('>');
      }
    }
  }

  private static void appendAttributes(@NotNull StringBuilder builder, @NotNull XmlTag tag) {
    for (XmlAttribute attribute : tag.getAttributes()) {
      builder.append(' ');
      builder.append(attribute.getName());
      builder.append("=\"");
      builder.append(attribute.getValue());
      builder.append('"');
    }
  }
}
