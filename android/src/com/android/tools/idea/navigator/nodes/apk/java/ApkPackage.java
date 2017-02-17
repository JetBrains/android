/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.navigator.nodes.apk.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

class ApkPackage {
  @NotNull private final String myName;
  @Nullable private final ApkPackage myParent;

  @NotNull private final Map<String, ApkPackage> mySubpackagesByName = new HashMap<>();
  @NotNull private final Map<String, ApkClass> myClassesByName = new HashMap<>();

  ApkPackage(@NotNull String name, @Nullable ApkPackage parent) {
    myName = name;
    myParent = parent;
  }

  @NotNull
  Collection<ApkPackage> getSubpackages() {
    return mySubpackagesByName.values();
  }

  @Nullable
  ApkPackage findSubpackage(@NotNull String name) {
    return mySubpackagesByName.get(name);
  }

  @NotNull
  Collection<ApkClass> getClasses() {
    return myClassesByName.values();
  }

  @Nullable
  ApkClass findClass(@NotNull String name) {
    return myClassesByName.get(name);
  }

  @Nullable
  ApkPackage getParent() {
    return myParent;
  }

  @NotNull
  ApkPackage addSubpackage(@NotNull String name) {
    return mySubpackagesByName.computeIfAbsent(name, k -> new ApkPackage(name, this));
  }

  @NotNull
  ApkClass addClass(@NotNull String name) {
    return myClassesByName.computeIfAbsent(name, s -> new ApkClass(name, this));
  }

  @NotNull
  String getName() {
    return myName;
  }

  @NotNull
  String getFqn() {
    return myParent != null ? (myParent.getFqn() + "." + myName) : myName;
  }

  boolean doSubpackagesHaveClasses() {
    for (ApkPackage subpackage : getSubpackages()) {
      if (!subpackage.getClasses().isEmpty()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String toString() {
    return getFqn();
  }
}
