
/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.asdriver.tests;
import java.util.ArrayList;
import java.util.List;
/**
 * Class to construct XPath queries for fetching Swing / Compose components in Android Studio tests.
 */
public class UIXpathGenerator {
  private String tag = "div";
  private final List<String> conditions = new ArrayList<>();
  public UIXpathGenerator() {
  }
  /**
   * Sets the XML tag name to search for. Defaults to "div".
   */
  public UIXpathGenerator setTag(String tag) {
    this.tag = tag;
    return this;
  }
  /**
   * Adds a condition to match the 'class' attribute exactly.
   * Example: withClass("ComposeNode") -> @class='ComposeNode'
   */
  public UIXpathGenerator setClass(String className) {
    conditions.add("@class='" + className + "'");
    return this;
  }
  /**
   * Adds a condition to match the 'role' attribute exactly.
   * Useful for Compose semantics roles like "Button", "Checkbox".
   */
  public UIXpathGenerator setRole(String role) {
    conditions.add("@role='" + role + "'");
    return this;
  }
  /**
   * Adds a condition to match the 'text' attribute exactly.
   */
  public UIXpathGenerator setText(String text) {
    conditions.add("@text='" + text + "'");
    return this;
  }
  /**
   * Adds a condition checking if the 'text' attribute contains the specified substring.
   */
  public UIXpathGenerator setContainsText(String text) {
    conditions.add("contains(@text, '" + text + "')");
    return this;
  }
  /**
   * Adds a generic attribute exact match condition.
   * @param name The attribute name (without '@').
   * @param value The expected value.
   */
  public UIXpathGenerator setAttribute(String name, String value) {
    conditions.add("@" + name + "='" + value + "'");
    return this;
  }
  /**
   * Constructs the final XPath string.
   */
  public String build() {
    StringBuilder sb = new StringBuilder("//");
    sb.append(tag);
    if (!conditions.isEmpty()) {
      sb.append("[");
      sb.append(String.join(" and ", conditions));
      sb.append("]");
    }
    return sb.toString();
  }
  @Override
  public String toString() {
    return build();
  }
}