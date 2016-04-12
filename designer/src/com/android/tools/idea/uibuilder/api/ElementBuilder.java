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
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Builds XML element strings.
 */
public final class ElementBuilder {
  private final String myName;
  private final Collection<Attribute> myAttributes;

  public ElementBuilder(@NotNull String name) {
    myName = name;
    myAttributes = new ArrayList<>();
  }

  @NotNull
  public ElementBuilder addAndroidAttribute(@NotNull String name, boolean value) {
    return addAndroidAttribute(name, Boolean.toString(value));
  }

  @NotNull
  public ElementBuilder addAndroidAttribute(@NotNull String name, int value) {
    return addAndroidAttribute(name, Integer.toString(value));
  }

  /**
   * Adds an attribute with the Android namespace prefix to the element.
   */
  @NotNull
  public ElementBuilder addAndroidAttribute(@NotNull String name, @NotNull String value) {
    myAttributes.add(new Attribute(SdkConstants.ANDROID_NS_NAME, name, value));
    return this;
  }

  @NotNull
  public String build() {
    StringBuilder builder = new StringBuilder("<")
      .append(myName);

    myAttributes.stream()
      .forEach(attribute -> builder
        .append("\n    ")
        .append(attribute));

    return builder
      .append(" />")
      .toString();
  }

  private static final class Attribute {
    private final String myNamespacePrefix;
    private final String myName;
    private final String myValue;

    private Attribute(@NotNull String namespacePrefix, @NotNull String name, @NotNull String value) {
      myNamespacePrefix = namespacePrefix;
      myName = name;
      myValue = value;
    }

    @NotNull
    @Override
    public String toString() {
      return myNamespacePrefix + ':' + myName + "=\"" + myValue + '"';
    }
  }
}
