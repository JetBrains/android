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
package com.android.tools.idea.editors.hierarchyview.model;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.Objects;

public class ViewProperty implements Comparable<ViewProperty> {

  private static final Comparator<String> CATEGORY_COMPARATOR = Ordering.natural().nullsFirst();

  @NotNull public final String fullName;

  @NotNull public final String name;
  public final String category;

  private String myValue;

  ViewProperty(@NotNull String fullName) {
    this.fullName = fullName;

    int colonIndex = fullName.indexOf(':');
    if (colonIndex != -1) {
      category = fullName.substring(0, colonIndex);
      name = fullName.substring(colonIndex + 1);
    } else {
      category = null;
      name = fullName;
    }
  }

  @Override
  public String toString() {
    return fullName + '=' + myValue;
  }

  @Override
  public int compareTo(@NotNull ViewProperty other) {
    return ComparisonChain.start()
      .compare(category, other.category, CATEGORY_COMPARATOR)
      .compare(name, other.name)
      .result();
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof ViewProperty)) {
      return false;
    }

    ViewProperty other = (ViewProperty)obj;
    return Objects.equals(category, other.category) && name.equals(other.name);
  }

  public void setValue(String value) {
    myValue = value;
  }

  public String getValue() {
    return myValue;
  }
}
