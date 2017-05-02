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
package com.android.tools.idea.apk.debugging;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class ApkPackage {
  @NotNull private final String myName;
  @NotNull private final String myFqn;

  @Nullable private final ApkPackage myParent;

  @NotNull private final Map<String, ApkPackage> mySubpackagesByName = new HashMap<>();
  @NotNull private final Map<String, ApkClass> myClassesByName = new HashMap<>();

  public ApkPackage(@NotNull String name, @Nullable ApkPackage parent) {
    myName = name;
    myParent = parent;
    myFqn = myParent != null ? (myParent.getFqn() + "." + myName) : myName;
  }

  @NotNull
  public Collection<ApkPackage> getSubpackages() {
    return mySubpackagesByName.values();
  }

  @Nullable
  public ApkPackage findSubpackage(@NotNull String name) {
    return mySubpackagesByName.get(name);
  }

  @NotNull
  public Collection<ApkClass> getClasses() {
    return myClassesByName.values();
  }

  @Nullable
  public ApkClass findClass(@NotNull String name) {
    return myClassesByName.get(name);
  }

  @Nullable
  public ApkPackage getParent() {
    return myParent;
  }

  @NotNull
  public ApkPackage addSubpackage(@NotNull String name) {
    return mySubpackagesByName.computeIfAbsent(name, k -> new ApkPackage(name, this));
  }

  @NotNull
  public ApkClass addClass(@NotNull String name) {
    return myClassesByName.computeIfAbsent(name, s -> new ApkClass(name, this));
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public String getFqn() {
    return myFqn;
  }

  public boolean doSubpackagesHaveClasses() {
    for (ApkPackage subpackage : getSubpackages()) {
      if (!subpackage.getClasses().isEmpty()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ApkPackage)) {
      return false;
    }
    ApkPackage that = (ApkPackage)o;
    return Objects.equals(myName, that.myName) &&
           Objects.equals(myFqn, that.myFqn) &&
           Objects.equals(myParent, that.myParent);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myName, myFqn, myParent);
  }

  @Override
  public String toString() {
    return getFqn();
  }
}
