/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.templates;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.Attributes;

import java.util.Locale;

/**
 * Helper code for Template Globals (defined in globals.xml)
 */
public class TypedVariable {
  private static final Logger LOG = Logger.getInstance(TypedVariable.class);

  public enum Type {
    STRING,
    BOOLEAN,
    INTEGER;

    @NotNull
    public static Type get(@Nullable String name) {
      if (name == null) {
        return STRING;
      }
      try {
        return valueOf(name.toUpperCase(Locale.US));
      } catch (IllegalArgumentException e) {
        if (!ApplicationManager.getApplication().isUnitTestMode()) {
          StringBuilder stringBuilder = new StringBuilder();
          stringBuilder.append("Unexpected global type '" + name + "'. Expected one of: \n");
          for (Type s : Type.values()) {
            stringBuilder.append("   " + s.name().toLowerCase(Locale.US) + "\n");
          }
          LOG.error(stringBuilder.toString());
        }
      }

      return STRING;
    }
  }

  /**
   * Return the value if it could be parsed to the correct type, or null.
   *
   * Note: Passing in null for the value parameter will always return a null result, but this
   * method allows it to make it easier to use with APIs that may return null strings.
   */
  @Nullable
  public static Object parse(@NotNull Type type, @Nullable String value) {
    if (value == null) {
      return null;
    }

    switch (type) {
      case STRING:
        return value;
      case BOOLEAN:
        // Do our own boolean parsing, as Boolean.parseBoolean doesn't fail on invalid match.
        if (Boolean.TRUE.toString().equalsIgnoreCase(value)) {
          return Boolean.TRUE;
        }
        else if (Boolean.FALSE.toString().equalsIgnoreCase(value)) {
          return Boolean.FALSE;
        }
        else {
          return null;
        }
      case INTEGER:
        try {
          return Integer.parseInt(value);
        } catch (NumberFormatException e) {
          if (!ApplicationManager.getApplication().isUnitTestMode()) {
            LOG.error("NumberFormatException while evaluating " + value);
          }
          return null;
        }
    }

    return null;
  }

  @Nullable
  public static Object parseGlobal(@NotNull Attributes attributes) {
    String value = attributes.getValue(Template.ATTR_VALUE);
    Type type = Type.get(attributes.getValue(Template.ATTR_TYPE));
    Object result = parse(type, value);
    return (result != null) ? result : value;
  }
}
