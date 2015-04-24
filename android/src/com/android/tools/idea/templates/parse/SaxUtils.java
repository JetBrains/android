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
package com.android.tools.idea.templates.parse;

import com.android.utils.XmlUtils;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.Attributes;

import java.io.File;

/**
 * Helper methods used by the template system when parsing XML with Java SAX (Simple API for XML).
 */
public final class SaxUtils {
  /**
   * Retrieve the named parameter from the attribute list and unescape it from XML as a path
   *
   * @param attributes the map of attributes
   * @param name       the name of the attribute to retrieve
   */
  @Nullable
  public static File getPath(@NotNull Attributes attributes, @NotNull String name) {
    String value = attributes.getValue(name);
    if (value == null) {
      return null;
    }
    String unescapedString = XmlUtils.fromXmlAttributeValue(value);
    return new File(FileUtil.toSystemDependentName(unescapedString));
  }

  /**
   * Retrieve the named parameter from the attribute list, or a default value if no match is found.
   *
   * @param attributes   the map of attributes
   * @param name         the name of the attribute to retrieve
   * @param defaultValue the value to use if the named parameter isn't present
   */
  @NotNull
  public static String attrOrDefault(@NotNull Attributes attributes, @NotNull String name, @NotNull String defaultValue) {
    String value = attributes.getValue(name);
    return (value != null) ? value : defaultValue;
  }
}
