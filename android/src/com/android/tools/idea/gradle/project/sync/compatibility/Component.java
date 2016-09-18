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
package com.android.tools.idea.gradle.project.sync.compatibility;

import com.android.tools.idea.gradle.project.sync.compatibility.version.VersionRange;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.util.SystemProperties.getLineSeparator;

class Component {
  @NotNull private final String myName;
  @NotNull private final VersionRange myVersionRange;
  @NotNull private final List<Component> myRequirements;

  @Nullable private final String myFailureMessage;

  Component(@NotNull String name, @NotNull String version, @Nullable String failureMessage) {
    myName = name;
    myVersionRange = VersionRange.parse(version);
    myFailureMessage = failureMessage;
    myRequirements = new ArrayList<>();
  }

  @NotNull
  String getName() {
    return myName;
  }

  @NotNull
  VersionRange getVersionRange() {
    return myVersionRange;
  }

  @Nullable
  String getFailureMessage() {
    return myFailureMessage;
  }

  @NotNull
  List<Component> getRequirements() {
    return ImmutableList.copyOf(myRequirements);
  }

  void addRequirement(@NotNull Component component) {
    myRequirements.add(component);
  }

  @Override
  public String toString() {
    StringBuilder buffer = new StringBuilder();
    buffer.append(myName).append(" ").append(myVersionRange);
    if (!myRequirements.isEmpty()) {
      String lineSeparator = getLineSeparator();
      buffer.append(lineSeparator).append("Requirements:");
      for (Component requirement : myRequirements) {
        buffer.append(lineSeparator).append("  ").append(requirement);
      }
    }
    return buffer.toString();
  }
}
