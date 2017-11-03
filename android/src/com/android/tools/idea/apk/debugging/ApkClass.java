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

import java.util.Objects;

public final class ApkClass {
  @NotNull private final String myName;
  @NotNull private final String myFqn;
  @NotNull private final ApkPackage myParent;

  public ApkClass(@NotNull String name, @NotNull ApkPackage parent) {
    myName = name;
    myParent = parent;

    String parentFqn = myParent.getFqn();
    myFqn = parentFqn.isEmpty() ? myName : (parentFqn + "." + myName);
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public String getFqn() {
    return myFqn;
  }

  @NotNull
  public ApkPackage getParent() {
    return myParent;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ApkClass)) {
      return false;
    }
    ApkClass aClass = (ApkClass)o;
    return Objects.equals(myName, aClass.myName) &&
           Objects.equals(myFqn, aClass.myFqn) &&
           Objects.equals(myParent, aClass.myParent);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myName, myFqn, myParent);
  }

  @Override
  public String toString() {
    return myFqn;
  }
}
