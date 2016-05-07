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
package com.android.tools.idea.uibuilder.api;

import com.android.SdkConstants;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.PreferenceTags.PREFERENCE_CATEGORY;

/**
 * Builds XML strings. Arguments are not validated or escaped. This class is designed to replace hand writing XML snippets in string
 * literals.
 */
public final class XmlBuilder {
  private enum Construct {
    NULL,
    START_TAG,
    ATTRIBUTE,
    END_TAG
  }

  private final StringBuilder myStringBuilder = new StringBuilder();

  private Construct myLastAppendedConstruct = Construct.NULL;
  private int myIndentationLevel;

  @NotNull
  public XmlBuilder startTag(@NotNull String name) {
    if (!myLastAppendedConstruct.equals(Construct.END_TAG)) {
      int length = myStringBuilder.length();
      if (length > 0) {
        myStringBuilder.replace(length - 1, length, ">\n");
      }
    }

    if (myIndentationLevel != 0) {
      myStringBuilder.append('\n');
    }

    indent();

    myStringBuilder
      .append('<')
      .append(name)
      .append('\n');

    myIndentationLevel++;
    myLastAppendedConstruct = Construct.START_TAG;

    return this;
  }

  @NotNull
  public XmlBuilder androidAttribute(@NotNull String name, boolean value) {
    return androidAttribute(name, Boolean.toString(value));
  }

  @NotNull
  public XmlBuilder androidAttribute(@NotNull String name, int value) {
    return androidAttribute(name, Integer.toString(value));
  }

  @NotNull
  public XmlBuilder androidAttribute(@NotNull String name, @NotNull String value) {
    return attribute(SdkConstants.ANDROID_NS_NAME, name, value);
  }

  @NotNull
  public XmlBuilder attribute(@NotNull String namespacePrefix, @NotNull String name, @NotNull String value) {
    indent();

    if (!StringUtil.isEmpty(namespacePrefix)) {
      myStringBuilder
        .append(namespacePrefix)
        .append(':');
    }

    myStringBuilder
      .append(name)
      .append("=\"")
      .append(value)
      .append("\"\n");

    myLastAppendedConstruct = Construct.ATTRIBUTE;
    return this;
  }

  @NotNull
  public XmlBuilder endTag(@NotNull String name) {
    boolean useEmptyElementTag = !name.endsWith("Layout") && !name.equals(PREFERENCE_CATEGORY);

    if (myLastAppendedConstruct.equals(Construct.START_TAG) || myLastAppendedConstruct.equals(Construct.ATTRIBUTE)) {
      int length = myStringBuilder.length();

      if (useEmptyElementTag) {
        myStringBuilder.deleteCharAt(length - 1);
      }
      else {
        myStringBuilder.replace(length - 1, length, ">\n\n");
      }
    }

    myIndentationLevel--;

    if ((myLastAppendedConstruct.equals(Construct.START_TAG) || myLastAppendedConstruct.equals(Construct.ATTRIBUTE)) &&
        useEmptyElementTag) {
      myStringBuilder.append(" />\n");
    }
    else {
      indent();

      myStringBuilder
        .append("</")
        .append(name)
        .append(">\n");
    }

    myLastAppendedConstruct = Construct.END_TAG;
    return this;
  }

  private void indent() {
    for (int i = 0; i < myIndentationLevel; i++) {
      myStringBuilder.append("    ");
    }
  }

  @NotNull
  @Override
  public String toString() {
    return myStringBuilder.toString();
  }
}
