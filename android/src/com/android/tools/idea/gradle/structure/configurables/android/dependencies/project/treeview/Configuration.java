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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.project.treeview;

import com.intellij.util.containers.SortedList;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

import static java.lang.String.CASE_INSENSITIVE_ORDER;

class Configuration implements Comparable<Configuration> {
  @NotNull private final String myName;
  @NotNull private final Icon myIcon;
  @NotNull private final List<String> myTypes = new SortedList<>(CASE_INSENSITIVE_ORDER);

  Configuration(@NotNull String name, @NotNull Icon icon, boolean transitive) {
    myName = name;
    myIcon = icon;
    addTransitive(transitive);
  }

  @NotNull
  String getName() {
    return myName;
  }

  @NotNull
  Icon getIcon() {
    return myIcon;
  }

  @NotNull
  public List<String> getTypes() {
    return myTypes;
  }

  void addTransitive(boolean transitive) {
    String type = transitive ? "transitive" : "declared";
    if (!myTypes.contains(type)) {
      myTypes.add(type);
    }
  }

  @Override
  public int compareTo(Configuration other) {
    return myName.compareTo(other.myName);
  }
}
